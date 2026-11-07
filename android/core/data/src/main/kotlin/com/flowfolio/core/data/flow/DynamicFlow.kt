package com.flowfolio.core.data.flow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.flowfolio.core.data.IoDispatcher
import com.flowfolio.core.data.toBook
import com.flowfolio.core.database.AnnotationDao
import com.flowfolio.core.database.BookDao
import com.flowfolio.core.database.BookReadingDao
import com.flowfolio.core.database.BookmarkDao
import com.flowfolio.core.model.BookFormat
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dynamic Flow in the app (ADR-0045): starts conversions in the conversion process, follows them through its
 * broadcasts, and keeps each PDF's view. [statuses] holds every book this run knows about; [notices] tells the app what
 * to show.
 */
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

    /** Converts [bookId] in the background unless it is converted or converting already. */
    suspend fun convert(bookId: String)

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
    object None : DynamicFlow {
        override val statuses: StateFlow<Map<String, FlowStatus>> = MutableStateFlow(emptyMap())
        override val notices: SharedFlow<FlowNotice> = MutableSharedFlow()
        override val wantsNotificationPermission: StateFlow<Boolean> = MutableStateFlow(false)

        override fun status(bookId: String): Flow<FlowStatus> = kotlinx.coroutines.flow.flowOf(FlowStatus.None)

        override fun bundle(bookId: String): File? = null

        override fun readableBundle(bookId: String): File? = null

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
// Hilt injects one dependency per parameter.
@Suppress("LongParameterList")
class DefaultDynamicFlow @Inject constructor(
    @ApplicationContext private val context: Context,
    private val books: BookDao,
    private val reading: BookReadingDao,
    private val annotations: AnnotationDao,
    private val bookmarks: BookmarkDao,
    @IoDispatcher private val io: CoroutineDispatcher,
    // Lazy: the carrier tells this class what it carried, so neither can be made inside the other.
    private val carrier: dagger.Lazy<AnnotationCarrier>
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

    /** Books whose PDF changes go into their Dynamic Flow once it is converted. */
    private val carryWhenReady = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Books converted again over an older bundle: their Dynamic Flow highlights are found again once it is done. */
    private val reanchorWhenReady = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val bookId = intent.getStringExtra(FlowProtocol.EXTRA_BOOK_ID) ?: return
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
                    if (failure == null) {
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

    override fun status(bookId: String): Flow<FlowStatus> = statuses
        .map { it[bookId] ?: if (files.ready(bookId)) FlowStatus.Ready else FlowStatus.None }
        .distinctUntilChanged()

    override fun bundle(bookId: String): File? = files.epub(bookId).takeIf { files.ready(bookId) }

    override fun readableBundle(bookId: String): File? =
        files.readable(bookId, converting = _statuses.value[bookId] is FlowStatus.Converting)

    override suspend fun convert(bookId: String) {
        val current = _statuses.value[bookId]
        val started = current is FlowStatus.Converting || current == FlowStatus.Queued || files.ready(bookId)
        val book = if (started) null else withContext(io) { books.get(bookId)?.toBook() }
        if (book?.format != BookFormat.PDF) return
        titles[bookId] = book.title
        if (files.outdated(bookId)) reanchorWhenReady += bookId
        set(bookId, FlowStatus.Converting(0f))
        val (file, uri) = when (val s = book.storage) {
            is BookStorage.Copied -> s.path to null
            is BookStorage.Linked -> null to s.uri
        }
        FlowService.start(context, bookId, book.title, file, uri)
        _notices.tryEmit(FlowNotice.Started(bookId, book.title))
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
        const val NOTICE_BUFFER = 8
    }
}
