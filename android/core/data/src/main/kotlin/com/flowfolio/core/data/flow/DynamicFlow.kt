package com.flowfolio.core.data.flow

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import com.flowfolio.core.data.IoDispatcher
import com.flowfolio.core.data.flow.FlowWatch.waiting
import com.flowfolio.core.data.toBook
import com.flowfolio.core.database.AnnotationDao
import com.flowfolio.core.database.BookDao
import com.flowfolio.core.database.BookReadingDao
import com.flowfolio.core.database.BookmarkDao
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.BookHealth
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.FlowFailure
import com.flowfolio.core.model.FlowNotice
import com.flowfolio.core.model.FlowPause
import com.flowfolio.core.model.FlowStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dynamic Flow in the app (ADR-0045): starts conversions in the conversion process, follows them through its
 * broadcasts, and keeps each PDF's view. [statuses] holds every book this run knows about; [notices] tells the app what
 * to show.
 */
@Suppress("TooManyFunctions") // one function per thing the app asks Dynamic Flow about.
interface DynamicFlow {
    val statuses: StateFlow<Map<String, FlowStatus>>

    val notices: SharedFlow<FlowNotice>

    /** Whether Android 13's notification permission should be asked for: true once, at the first conversion. */
    val wantsNotificationPermission: StateFlow<Boolean>

    fun status(bookId: String): Flow<FlowStatus>

    /** The converted EPUB when the book has one, else null. */
    fun bundle(bookId: String): File?

    /**
     * What the reader may open now: the complete EPUB when the book is converted, else the partial bundle while it is
     * still converting (progressive delivery). Null when nothing is readable yet.
     */
    fun readableBundle(bookId: String): File?

    /** The conversion's health for the book (M2), or null when it has no bundle. */
    fun health(bookId: String): BookHealth?

    /**
     * Converts [bookId] in the background unless it is converted or converting already. A scanned book never converted
     * before asks first ([FlowNotice.ScanAsk]) and converts once [answerScan] says so.
     */
    suspend fun convert(bookId: String)

    /**
     * The answer to [FlowNotice.ScanAsk]: [convert] reads the book by OCR now; otherwise the book goes back to its PDF,
     * so it does not ask every time it opens. [dontAsk] stops asking for every book.
     */
    suspend fun answerScan(bookId: String, convert: Boolean, dontAsk: Boolean) = Unit

    /**
     * Sets the book's view (true Dynamic Flow); turning it on converts the book when it has no bundle yet. With [carry]
     * the highlights, notes and bookmarks of the view being left are brought across ("convert the changes"): at once,
     * or, into a Dynamic Flow not converted yet, as soon as it is.
     */
    suspend fun setFlowMode(bookId: String, on: Boolean, carry: Boolean = false)

    /** Remembers where the reader is in the book's Dynamic Flow, and the PDF page that is in (null keeps the page). */
    suspend fun saveFlowPlace(bookId: String, locator: String, pdfPage: Int?)

    /**
     * Whether the book has highlights, notes or bookmarks in its Dynamic Flow ([inFlow]) or in its PDF: switching away
     * from a view with none has nothing to carry, so it doesn't ask.
     */
    suspend fun hasNotes(bookId: String, inFlow: Boolean): Boolean

    fun notificationPermissionAsked()

    /** Tells the app something about Dynamic Flow (carrying annotations runs in the app and reports here). */
    fun notify(notice: FlowNotice)

    /** Removes the bundle: the book was deleted. */
    fun delete(bookId: String)

    /** No Dynamic Flow: for tests, and fakes that don't need it. */
    @Suppress("TooManyFunctions")
    object None : DynamicFlow {
        override val statuses: StateFlow<Map<String, FlowStatus>> = MutableStateFlow(emptyMap())
        override val notices: SharedFlow<FlowNotice> = MutableSharedFlow()
        override val wantsNotificationPermission: StateFlow<Boolean> = MutableStateFlow(false)

        override fun status(bookId: String): Flow<FlowStatus> = kotlinx.coroutines.flow.flowOf(FlowStatus.None)

        override fun bundle(bookId: String): File? = null

        override fun readableBundle(bookId: String): File? = null

        override fun health(bookId: String): BookHealth? = null

        override suspend fun convert(bookId: String) = Unit

        override suspend fun setFlowMode(bookId: String, on: Boolean, carry: Boolean) = Unit

        override suspend fun saveFlowPlace(bookId: String, locator: String, pdfPage: Int?) = Unit

        override suspend fun hasNotes(bookId: String, inFlow: Boolean) = false

        override fun notificationPermissionAsked() = Unit

        override fun notify(notice: FlowNotice) = Unit

        override fun delete(bookId: String) = Unit
    }
}

@Singleton
// Hilt injects one dependency per parameter; one function per thing the app asks, plus the watch (FlowWatch).
@Suppress("LongParameterList", "TooManyFunctions")
class DefaultDynamicFlow @Inject constructor(
    @ApplicationContext private val context: Context,
    private val books: BookDao,
    private val reading: BookReadingDao,
    private val annotations: AnnotationDao,
    private val bookmarks: BookmarkDao,
    @IoDispatcher private val io: CoroutineDispatcher,
    // Lazy: the carrier tells this class what it carried, so neither can be made inside the other.
    private val carrier: dagger.Lazy<AnnotationCarrier>,
    private val pdf: com.flowfolio.core.pdf.PdfEngine,
    private val settings: com.flowfolio.core.data.SettingsRepository
) : DynamicFlow {
    private val files = FlowFiles(context.filesDir)
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val _statuses = MutableStateFlow<Map<String, FlowStatus>>(emptyMap())
    override val statuses: StateFlow<Map<String, FlowStatus>> = _statuses.asStateFlow()
    private val _notices = MutableSharedFlow<FlowNotice>(extraBufferCapacity = NOTICE_BUFFER)
    override val notices: SharedFlow<FlowNotice> = _notices.asSharedFlow()
    private val _wantsNotificationPermission = MutableStateFlow(false)
    override val wantsNotificationPermission: StateFlow<Boolean> = _wantsNotificationPermission.asStateFlow()
    private val titles = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Scanned books the reader agreed to have read by OCR (TM1): they convert without asking again. */
    private val agreed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Books whose PDF changes go into their Dynamic Flow once it is converted. */
    private val carryWhenReady = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Books converted again over an older bundle: their Dynamic Flow highlights are found again once it is done. */
    private val reanchorWhenReady = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** When each converting or queued book was last heard from ([SystemClock.elapsedRealtime]), for [FlowWatch]. */
    private val lastHeard = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** How many times each book was lost with the conversion process this run. */
    private val losses = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private var watch: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val bookId = intent.getStringExtra(FlowProtocol.EXTRA_BOOK_ID) ?: return
            lastHeard[bookId] = SystemClock.elapsedRealtime()
            when (intent.action) {
                FlowProtocol.ACTION_PROGRESS -> {
                    val fraction = intent.getFloatExtra(FlowProtocol.EXTRA_FRACTION, 0f)
                    val pause = intent.getStringExtra(FlowProtocol.EXTRA_PAUSE)?.let { p ->
                        FlowPause.entries.firstOrNull {
                            it.name ==
                                p
                        }
                    }
                    val readable = intent.getIntExtra(FlowProtocol.EXTRA_CHAPTERS, 0)
                    val total = intent.getIntExtra(FlowProtocol.EXTRA_TOTAL_CHAPTERS, 0)
                    set(
                        bookId,
                        if (fraction < 0f) {
                            FlowStatus.Queued
                        } else {
                            FlowStatus.Converting(fraction, pause, readable, total)
                        }
                    )
                }

                FlowProtocol.ACTION_DONE -> {
                    val failure = intent.getStringExtra(FlowProtocol.EXTRA_FAILURE)
                        ?.let { f -> FlowFailure.entries.firstOrNull { it.name == f } ?: FlowFailure.OTHER }
                    val title = titles[bookId].orEmpty()
                    lastHeard -= bookId
                    if (failure == null) {
                        losses -= bookId
                        set(bookId, FlowStatus.Ready)
                        _notices.tryEmit(FlowNotice.Done(bookId, title))
                        if (reanchorWhenReady.remove(bookId)) carrier.get().reanchor(bookId)
                        if (carryWhenReady.remove(bookId)) carrier.get().carry(bookId, fromFlow = false)
                    } else {
                        set(bookId, FlowStatus.Failed(failure))
                        _notices.tryEmit(FlowNotice.Failed(bookId, title, failure))
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(FlowProtocol.ACTION_PROGRESS)
            addAction(FlowProtocol.ACTION_DONE)
        }
        // Only the app's own conversion process sends these (to the app's package); nothing else may.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    private fun set(bookId: String, status: FlowStatus) = _statuses.update { it + (bookId to status) }

    private fun fail(bookId: String, failure: FlowFailure) {
        lastHeard -= bookId
        set(bookId, FlowStatus.Failed(failure))
        _notices.tryEmit(FlowNotice.Failed(bookId, titles[bookId].orEmpty(), failure))
    }

    /** Looks for books lost with the conversion process while any book is converting or queued (see [FlowWatch]). */
    @Synchronized
    private fun watch() {
        if (watch?.isActive == true) return
        watch = scope.launch {
            while (_statuses.value.values.any { it.waiting() }) {
                delay(FlowWatch.CHECK_MS)
                val lost = FlowWatch.lost(
                    _statuses.value,
                    lastHeard,
                    SystemClock.elapsedRealtime(),
                    converterAlive()
                )
                lost.forEach { lose(it) }
            }
        }
    }

    /** The conversion process died with [bookId] unfinished: convert it again once, then report it stopped. */
    private suspend fun lose(bookId: String) {
        val previous = losses[bookId] ?: 0
        losses[bookId] = previous + 1
        Log.w(TAG, "FF_FLOW $bookId lost with the conversion process (${previous + 1})")
        withContext(io) { files.partial(bookId).delete() }
        if (FlowWatch.restarts(previous)) {
            set(bookId, FlowStatus.None)
            convert(bookId)
        } else {
            fail(bookId, FlowFailure.INTERRUPTED)
        }
    }

    /** Whether the app's conversion process (`:convert`, [FlowService]) is running: an app may list its own. */
    private fun converterAlive(): Boolean {
        val processes = context.getSystemService(ActivityManager::class.java)?.runningAppProcesses.orEmpty()
        return processes.any { it.processName == context.packageName + CONVERT_PROCESS }
    }

    override fun status(bookId: String): Flow<FlowStatus> = statuses
        .map { it[bookId] ?: if (files.ready(bookId)) FlowStatus.Ready else FlowStatus.None }
        .distinctUntilChanged()

    override fun bundle(bookId: String): File? = files.epub(bookId).takeIf { files.ready(bookId) }

    override fun readableBundle(bookId: String): File? =
        files.readable(bookId, converting = _statuses.value[bookId] is FlowStatus.Converting)

    override fun health(bookId: String): BookHealth? = files.health(bookId)

    override suspend fun convert(bookId: String) {
        val current = _statuses.value[bookId]
        val started = current is FlowStatus.Converting || current == FlowStatus.Queued || files.ready(bookId)
        val book = if (started) null else withContext(io) { books.get(bookId)?.toBook() }
        if (book?.format != BookFormat.PDF) return
        titles[bookId] = book.title
        // A scan never converted before: say what OCR means and how long it takes, and convert once agreed (TM1).
        if (bookId !in agreed && neverConverted(bookId) && settings.settings.first().scanNotice) {
            val minutes = scanMinutes(book)
            if (minutes != null) {
                _notices.tryEmit(FlowNotice.ScanAsk(bookId, book.title, minutes))
                return
            }
        }
        if (files.outdated(bookId)) reanchorWhenReady += bookId
        set(bookId, FlowStatus.Converting(0f))
        lastHeard[bookId] = SystemClock.elapsedRealtime()
        val (file, uri) = when (val s = book.storage) {
            is BookStorage.Copied -> s.path to null
            is BookStorage.Linked -> null to s.uri
        }
        // Android refuses a foreground service to an app in the background (a restart by the watch, say): say so.
        val launched = runCatching { FlowService.start(context, bookId, book.title, file, uri) }
        if (launched.isFailure) {
            Log.w(TAG, "could not start converting $bookId", launched.exceptionOrNull())
            fail(bookId, FlowFailure.INTERRUPTED)
            return
        }
        _notices.tryEmit(FlowNotice.Started(bookId, book.title))
        watch()
        // Android 13 and later ask before an app may post notifications: asked at the first conversion.
        _wantsNotificationPermission.value = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    }

    override suspend fun setFlowMode(bookId: String, on: Boolean, carry: Boolean) {
        withContext(io) { reading.setFlowMode(bookId, on) }
        when {
            !carry -> carryWhenReady -= bookId
            on && !files.ready(bookId) -> carryWhenReady += bookId
            else -> carrier.get().carry(bookId, fromFlow = !on)
        }
        if (on) convert(bookId)
    }

    override suspend fun answerScan(bookId: String, convert: Boolean, dontAsk: Boolean) {
        if (dontAsk) settings.setScanNotice(false)
        if (convert) {
            agreed += bookId
            convert(bookId)
        } else {
            withContext(io) { reading.setFlowMode(bookId, false) }
        }
    }

    /** Nothing of a conversion is on disk: no bundle, no partial one, no pages already read by OCR. */
    private fun neverConverted(bookId: String) = !files.epub(bookId).exists() &&
        !files.partial(bookId).exists() && !files.ocrCache(bookId).exists()

    /**
     * About how many minutes OCR will take when [book] looks scanned (most of its first pages carry almost no text),
     * else null. Any trouble opening it is left to the conversion, which reports it.
     */
    private suspend fun scanMinutes(book: com.flowfolio.core.model.Book): Int? = withContext(io) {
        runCatching {
            val fd = when (val s = book.storage) {
                is BookStorage.Copied -> android.os.ParcelFileDescriptor.open(
                    File(s.path),
                    android.os.ParcelFileDescriptor.MODE_READ_ONLY
                )

                is BookStorage.Linked -> context.contentResolver.openFileDescriptor(android.net.Uri.parse(s.uri), "r")
            } ?: return@runCatching null
            // The file stays open while PDFium reads it.
            fd.use { file ->
                pdf.open(file).use { doc ->
                    val sample = (0 until minOf(doc.pageCount, SCAN_SAMPLE_PAGES))
                    val scanned = sample.count { i ->
                        doc.pageText(i).text.count { !it.isWhitespace() } < SCANNED_MAX_CHARS
                    }
                    if (scanned * 2 <= sample.count()) {
                        null
                    } else {
                        ((doc.pageCount * OCR_SECONDS_PER_PAGE) / SECONDS_PER_MINUTE).coerceAtLeast(1)
                    }
                }
            }
        }.getOrNull()
    }

    override suspend fun saveFlowPlace(bookId: String, locator: String, pdfPage: Int?) = withContext(io) {
        if (pdfPage ==
            null
        ) {
            reading.setFlowLocator(bookId, locator)
        } else {
            reading.updateFlowProgress(bookId, locator, pdfPage)
        }
    }

    override suspend fun hasNotes(bookId: String, inFlow: Boolean): Boolean = withContext(io) {
        annotations.countInView(bookId, inFlow) > 0 || bookmarks.countInView(bookId, inFlow) > 0
    }

    override fun notificationPermissionAsked() {
        _wantsNotificationPermission.value = false
    }

    override fun notify(notice: FlowNotice) {
        _notices.tryEmit(notice)
    }

    override fun delete(bookId: String) {
        scope.launch { files.delete(bookId) }
        _statuses.update { it - bookId }
    }

    private companion object {
        /** How many first pages are looked at to tell a scan, and how little text a scanned page has (TM1). */
        private const val SCAN_SAMPLE_PAGES = 4
        private const val SCANNED_MAX_CHARS = 20

        /**
         * Seconds OCR takes a page, for the notice's estimate: a cautious placeholder until it is measured on the
         * Fold (CLAUDE.md, known issue 55).
         */
        private const val OCR_SECONDS_PER_PAGE = 4
        private const val SECONDS_PER_MINUTE = 60

        const val NOTICE_BUFFER = 8
        const val TAG = "FlowFolioFlow"

        /** The conversion process's suffix, as `android:process` names it in the manifest. */
        const val CONVERT_PROCESS = ":convert"
    }
}
