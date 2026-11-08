package com.flowfolio.feature.reader

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.Ambience
import com.flowfolio.core.data.FolioScanner
import com.flowfolio.core.data.NotYetReadableException
import com.flowfolio.core.data.OpenedBook
import com.flowfolio.core.data.OpenedEpub
import com.flowfolio.core.data.ReaderRepository
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.data.flow.DynamicFlow
import com.flowfolio.core.designsystem.R as DesignR
import com.flowfolio.core.designsystem.fontRes
import com.flowfolio.core.epub.EpubException
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.AppStyle
import com.flowfolio.core.model.BookHealth
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.FlowStatus
import com.flowfolio.core.model.HighlightColor
import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.core.model.PageLabels
import com.flowfolio.core.model.PageRect
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.PixelSize
import com.flowfolio.core.model.ReaderText
import com.flowfolio.core.model.ReadingFont
import com.flowfolio.core.model.UserSettings
import com.flowfolio.core.model.atmosphereInForce
import com.flowfolio.core.pdf.PageText
import com.flowfolio.core.pdf.PdfDocument
import com.flowfolio.core.pdf.PdfException
import com.flowfolio.core.reflow.CodeFace
import com.flowfolio.core.reflow.Locator
import com.flowfolio.core.voice.SpeechText
import com.flowfolio.core.voice.Voice
import com.flowfolio.core.voice.VoiceEngine
import com.flowfolio.core.voice.VoicePackDownloader
import com.flowfolio.core.voice.VoicePackState
import com.flowfolio.core.voice.VoicePacks
import com.flowfolio.core.voice.VoiceSpeakers
import com.flowfolio.feature.reader.cache.PageCache
import com.flowfolio.feature.reader.cache.PageKey
import com.flowfolio.feature.reader.cache.PdfPageSource
import com.flowfolio.feature.reader.cache.TileCache
import com.flowfolio.feature.reader.cache.TileKey
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.Spreads
import com.flowfolio.feature.reader.layout.chooseLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface ReaderState {
    data object Loading : ReaderState

    data class NeedsPassword(val wrongPassword: Boolean) : ReaderState

    data class Failed(val missingFile: Boolean, val epub: Boolean = false) : ReaderState

    data class Ready(
        val title: String,
        val pageCount: Int,
        val layout: ReaderLayout,
        /** Page in single layout, spread unit in spread layout. */
        val unit: Int,
        val unitCount: Int,
        val anchorPage: Int,
        /** Spread pairing (reader setting): the cover alone, or pages paired from the first. */
        val coverAlone: Boolean = true,
        /** The paper the pages are shown on (the Aa sheet's swatch, Auto resolved for the app's theme). */
        val tone: PageTone = PageTone.PRINTED,
        /** How pages turn. */
        val turn: PageTurn = PageTurn.CURL,
        /** Width / height of the page in view, for sizing pages not rendered yet (Scroll). */
        val pageAspect: Float = ReaderViewModel.LETTER_ASPECT,
        /** The pages are laid out by FlowFolio (an EPUB): a pinch sets the text size instead of zooming the page. */
        val reflowable: Boolean = false,
        /** An EPUB's pages as Scroll shows them, one continuous column of text; null for PDFs. */
        val scrollBands: ScrollBands? = null,
        /** The open EPUB's own body font (ADR-0030), shown on the "Book's font" card; null when it ships none. */
        val bookFont: android.graphics.Typeface? = null
    ) : ReaderState
}

/**
 * Dynamic Flow for the open book (ADR-0045): [available] for PDFs; [reading] while the reader shows its Dynamic Flow;
 * [wanted] once the reader chose it (the PDF shows until the book is converted); [status] its conversion.
 */
data class FlowState(
    val available: Boolean = false,
    val reading: Boolean = false,
    val wanted: Boolean = false,
    val status: FlowStatus = FlowStatus.None,
    /** The conversion's health once the book is converted (M2); null before then. */
    val health: BookHealth? = null
)

/** Window in pixels and dp, as measured by the reader's root layout, and whether the app is showing dark mode. */
data class Viewport(
    val widthPx: Int,
    val heightPx: Int,
    val widthDp: Float,
    val heightDp: Float,
    val dark: Boolean = false
)

// One handler per reader event, with a PDF path and an EPUB path for opening, layout and planning.
@Suppress("TooManyFunctions", "LargeClass")
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: ReaderRepository,
    private val settingsRepository: SettingsRepository,
    private val saved: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val ambience: Ambience,
    dynamicFlow: DynamicFlow
) : ViewModel() {
    /** The ambience being started: cancelled when the atmosphere changes, so two sounds never race. */
    private var sound: Job? = null

    private val bookId: String = checkNotNull(saved[ARG_BOOK_ID]) { "reader needs a bookId" }
    private val forcedLayout: ReaderLayout? = saved.get<String>(ARG_LAYOUT)?.let {
        runCatching { ReaderLayout.valueOf(it.uppercase()) }.getOrNull()
    }

    private val dynamic = ReaderFlow(
        bookId,
        viewModelScope,
        repository,
        dynamicFlow,
        object : ReaderFlow.Host {
            override val anchorPage get() = this@ReaderViewModel.anchorPage
            override val showing get() = _state.value is ReaderState.Ready

            override fun reopen(view: Boolean) = this@ReaderViewModel.reopen(view)

            override suspend fun flowOnOpen() = settingsRepository.settings.first().dynamicFlowOnOpen

            override suspend fun savePlace() = saveFlowPlace()
        }
    )

    /** The book's Dynamic Flow: the "Aa" sheet's switch and its progress ring, the bar by the page number. */
    internal val flow: StateFlow<FlowState> = dynamic.state

    /** Collectors of the open book's caches, cancelled when the book is closed for a switch of view. */
    private val bookJobs = ArrayList<Job>()

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _navigation = MutableStateFlow(BookNavigation())

    /** Outline and printed page labels, loaded in the background after the book opens. */
    val navigation: StateFlow<BookNavigation> = _navigation.asStateFlow()

    private val _links = MutableStateFlow<Map<Int, List<ReaderLink>>>(emptyMap())

    /** The EPUB layout the links in [_links] were found in; a new layout moves them. */
    private var linksRevision = -1

    /** Links of the pages read so far (and the ones about to be shown), by page. */
    internal val links: StateFlow<Map<Int, List<ReaderLink>>> = _links.asStateFlow()

    private var opened: OpenedBook? = null

    /** Page thumbnails for the riffle scrubber's loupe (ADR-0022), once the book is open. */
    internal var thumbnails: ReaderThumbnails? = null
        private set

    private val _cover = MutableStateFlow<android.graphics.Bitmap?>(null)

    /** The book's cover, small, for the riffle scrubber's board colours; null without one. */
    internal val cover: StateFlow<android.graphics.Bitmap?> = _cover.asStateFlow()

    /** The open EPUB, when the book is one (ADR-0019). */
    private var epub: EpubSession? = null

    /** A fixed-layout EPUB's pages, when the book declares `rendition:layout: pre-paginated` (Phase 6 M2). */
    private var fixed: FixedEpubPageSource? = null

    /** The fixed-layout book itself, kept so the reader's title and progress are the book's. */
    private var fixedBook: com.flowfolio.core.model.Book? = null

    /** Where an EPUB reader is: kept across layouts (fold, unfold, spreads) and saved with the book. */
    private var locator: Locator?
        get() = Locator.decode(saved[KEY_LOCATOR])
        set(value) {
            saved[KEY_LOCATOR] = value?.encode()
        }
    private var layoutJob: Job? = null

    /** An EPUB's saved page when it has no locator yet, used by its first layout. */
    private var startPage: Int? = null

    private val _relayingOut = MutableStateFlow(false)

    /**
     * An EPUB is being laid out again (new text settings, fold or unfold), from the start of the layout until the pages
     * in view are drawn: the reader shows the hourglass meanwhile instead of half-drawn pages.
     */
    internal val relayingOut: StateFlow<Boolean> = _relayingOut.asStateFlow()

    /** The pane and text settings a layout is under way for. */
    private var pendingLayout: EpubLayout? = null

    /** The reading fonts loaded so far: each is read from the APK once, when first used. */
    private val fonts = java.util.concurrent.ConcurrentHashMap<ReadingFont, android.graphics.Typeface>()

    private val _islandWork = MutableStateFlow<IslandWork?>(null)

    /** The next equations, SVG or tables for the island WebView to lay out or draw (EPUBs only). */
    internal val islandWork: StateFlow<IslandWork?> = _islandWork.asStateFlow()

    /** The open EPUB's archive for the island WebView; null when the book has no islands (no WebView then). */
    internal val epubArchive get() = epub?.takeIf { it.islands.any }?.opened?.archive

    /** The open document (for page text when selecting); null until the book is open. */
    internal val document: PdfDocument? get() = opened?.document

    private val _layoutRevision = MutableStateFlow(0)

    /**
     * Raised whenever an EPUB's pages change (a layout, its later chapters settling, a chapter laid out again for its
     * islands): highlights and ribbons are placed again (ADR-0040).
     */
    internal val layoutRevision: StateFlow<Int> = _layoutRevision.asStateFlow()

    /** Where each EPUB page text handed out came from, so a selection on it finds its chapter offsets. */
    private val glyphsOf = java.util.WeakHashMap<com.flowfolio.core.pdf.PageText, EpubPageText>()

    /** [page]'s text and character boxes for selecting: PDFium's for a PDF, the layout's for an EPUB. */
    internal suspend fun pageText(page: Int): com.flowfolio.core.pdf.PageText? {
        val session = epub ?: return runCatching { document?.pageText(page) }.getOrNull()
        val text = session.pageText(page)
        if (text != null) synchronized(glyphsOf) { glyphsOf[text.text] = text }
        return text?.text
    }

    /**
     * A selection of [range] on an EPUB page's [text] as the book's locator (`chapter:offset` of its first character)
     * and its chapter offsets; null for a PDF (its highlights stay by page).
     */
    internal fun bookRange(text: com.flowfolio.core.pdf.PageText, range: IntRange): Pair<String, IntRange>? {
        val glyphs = synchronized(glyphsOf) { glyphsOf[text] }?.glyphs ?: return null
        val first = glyphs.offsets.getOrNull(range.first)
        val last = glyphs.offsets.getOrNull(range.last)
        return if (first == null || last == null) null else Locator(glyphs.chapter, first).encode() to first..last
    }

    /** The book's highlights placed in the current layout near [pages] (EPUBs); a PDF's stay as they are. */
    internal suspend fun place(all: List<Annotation>, pages: Set<Int>): Placement =
        epub?.place(all, pages) ?: Placement(all, emptyList(), exact = true)

    /** Ribbons on the pages now showing their text (EPUBs); a PDF's stay as they are. */
    internal suspend fun placeRibbons(marks: List<Bookmark>): List<Bookmark> = epub?.placeRibbons(marks) ?: marks

    /** The text at the top of [page] as a locator, for an EPUB ribbon; null for a PDF. */
    internal fun locatorOfPage(page: Int): String? = epub?.locatorOf(page)?.encode()

    /** The EPUB's pages are final for this layout (no chapters estimated), so placed pages may be saved. */
    internal val pagesFinal: Boolean get() = epub?.exact ?: true

    /** The EPUB picture [path] for the image viewer, decoded for a screen [maxSide] pixels on its longer side. */
    internal suspend fun picture(path: String, maxSide: Int): ImageBitmap? = epub?.picture(path, maxSide)
    private var source: PdfPageSource? = null
    private var cache: PageCache? = null
    private var tileCache: TileCache? = null
    private val _tiles = MutableStateFlow<Map<TileKey, ImageBitmap>>(emptyMap())

    /** Zoom tiles that are ready (empty unless the reader is zoomed in). */
    val tiles: StateFlow<Map<TileKey, ImageBitmap>> = _tiles.asStateFlow()
    private val sizes = HashMap<Int, com.flowfolio.core.model.PageSize>()
    private val currentKeys = MutableStateFlow<Map<Int, PageKey>>(emptyMap())
    private val cacheImages = MutableStateFlow<Map<PageKey, ImageBitmap>>(emptyMap())
    private var viewport: Viewport? = null
    private var wanted: List<PageKey> = emptyList()
    private var planJob: Job? = null
    private var progressJob: Job? = null

    /** Page index -> image at the current layout's size, for pages that are ready. */
    val pageImages: StateFlow<Map<Int, ImageBitmap>> = combine(cacheImages, currentKeys) { images, current ->
        current.mapNotNull { (page, key) -> (images[key] ?: earlierDrawing(images, key))?.let { page to it } }.toMap()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * The page as last drawn in the same layout, while it is drawn again with newly ready equations (ADR-0041): the
     * same text, so it shows instead of blank paper, and the hourglass need not wait for every equation.
     */
    private fun earlierDrawing(images: Map<PageKey, ImageBitmap>, key: PageKey): ImageBitmap? = images.entries
        .filter { (k, _) -> k.page == key.page && k.size == key.size && k.tone == key.tone && k.layout == key.layout }
        .maxByOrNull { it.key.revision }?.value

    private var anchorPage: Int
        get() = saved[KEY_ANCHOR] ?: 0
        set(value) {
            saved[KEY_ANCHOR] = value
        }

    /** Reader settings (spreads on/off, pairing); a change re-lays the book out at the same page. */
    private var settings = UserSettings()

    private val _userSettings = MutableStateFlow(UserSettings())

    /** The reader's settings as the chrome needs them: auto-hide, paper sounds, scrub haptics (spec 5.7). */
    internal val userSettings: StateFlow<UserSettings> = _userSettings.asStateFlow()

    /** Remembers that a first-use guide was shown (owner, 2026-11-07). */
    fun markGuideSeen(id: String) {
        viewModelScope.launch { settingsRepository.markGuideSeen(id) }
    }

    /** "Never show tips again": turns first-use tips off and remembers the guide as seen (owner, 2026-11-07). */
    fun disableTips(id: String) {
        viewModelScope.launch {
            settingsRepository.setTipsEnabled(false)
            settingsRepository.markGuideSeen(id)
        }
    }

    init {
        open(password = null)
        viewModelScope.launch {
            settingsRepository.settings.collect {
                settings = it
                _userSettings.value = it
                playAmbience(it)
                publishReady()
                replan()
            }
        }
    }

    fun submitPassword(password: String) = open(password)

    private fun open(password: String?, view: Boolean? = null) {
        viewModelScope.launch {
            _state.value = ReaderState.Loading
            if (dynamic.opensFlow(view)) {
                openEpub(flow = true)
                return@launch
            }
            val book = try {
                repository.open(bookId, password)
            } catch (_: PdfException.PasswordRequired) {
                _state.value = ReaderState.NeedsPassword(wrongPassword = password != null)
                return@launch
            } catch (_: FileNotFoundException) {
                _state.value = ReaderState.Failed(missingFile = true)
                return@launch
            } catch (_: NotYetReadableException) {
                openEpub()
                return@launch
            } catch (_: PdfException) {
                _state.value = ReaderState.Failed(missingFile = false)
                return@launch
            }
            opened = book
            val pageSource = PdfPageSource(book.document)
            thumbnails = ReaderThumbnails(viewModelScope) { page, size, tone ->
                pageSource.render(PageKey(page, size, tone))
            }
            loadCover(book.book.coverPath)
            source = pageSource
            cache = PageCache(pageSource, viewModelScope, budgetBytes = 0, onEvicted = ::releaseLater).also { c ->
                bookJobs += viewModelScope.launch { c.images.collect { cacheImages.value = it } }
            }
            tileCache = TileCache(pageSource, viewModelScope, MAX_TILES, onEvicted = ::releaseLater).also { c ->
                bookJobs += viewModelScope.launch { c.tiles.collect { _tiles.value = it } }
            }
            // A recreated reader (rotation, process death) keeps its own position; a fresh open resumes where the
            // book was left (milestone 4).
            val start = if (saved.contains(KEY_ANCHOR)) anchorPage else book.book.lastPage ?: 0
            anchorPage = start.coerceIn(0, (book.document.pageCount - 1).coerceAtLeast(0))
            publishReady()
            replan()
            loadNavigation(book)
        }
    }

    /** Switches the book's view, bringing the changes or not (the "Aa" sheet's Dynamic Flow switch; ADR-0045). */
    fun setFlow(on: Boolean, carry: Boolean) = dynamic.switch(on, carry)

    /** Dynamic Flow keeps the PDF's page up to date as it is read; makes sure the latest is saved before leaving it. */
    private suspend fun saveFlowPlace() {
        progressJob?.join()
        epub?.let { session ->
            val at = locator ?: session.locatorOf(anchorPage)
            dynamic.savePlace(at.encode(), session.sourcePageOf(at))
        }
    }

    /** Closes the open book and opens it again in the other view. */
    private fun reopen(view: Boolean) {
        closeBook()
        open(password = null, view = view)
    }

    /** Lets go of everything the open book holds, so it can open again (in the other view). */
    private fun closeBook() {
        stopNarration()
        layoutJob?.cancel()
        planJob?.cancel()
        bookJobs.forEach { it.cancel() }
        bookJobs.clear()
        thumbnails?.clear()
        thumbnails = null
        cache?.clear()
        cache = null
        tileCache?.clear()
        tileCache = null
        source?.release()
        source = null
        opened?.document?.close()
        opened = null
        epub?.close()
        epub = null
        fixed?.close()
        fixed = null
        fixedBook = null
        sizes.clear()
        wanted = emptyList()
        currentKeys.value = emptyMap()
        cacheImages.value = emptyMap()
        _tiles.value = emptyMap()
        _links.value = emptyMap()
        linksRevision = -1
        _navigation.value = BookNavigation()
        _islandWork.value = null
        pendingLayout = null
        startPage = null
        _relayingOut.value = false
        saved.remove<Int>(KEY_ANCHOR)
        saved.remove<String>(KEY_LOCATOR)
        _layoutRevision.update { it + 1 }
        dynamic.shown(reading = false)
    }

    /** An EPUB (or a PDF's Dynamic Flow, [flow]): parse it, then lay it out once the reader knows its pane. */
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private suspend fun openEpub(flow: Boolean = false) {
        val opened = try {
            if (flow) repository.openFlow(bookId) else repository.openEpub(bookId)
        } catch (_: FileNotFoundException) {
            _state.value = ReaderState.Failed(missingFile = true, epub = true)
            return
        } catch (_: EpubException) {
            _state.value = ReaderState.Failed(missingFile = false, epub = true)
            return
        }
        if (opened.pkg.fixedLayout) {
            openFixedLayout(opened)
            return
        }
        val session = try {
            // Headings in the app's display face (Fraunces), as on the library's covers.
            val heading = runCatching { context.resources.getFont(DesignR.font.fraunces) }.getOrNull()
            // Code in the app's own monospaced face (JetBrains Mono): the phone's may not be monospaced.
            runCatching { context.resources.getFont(DesignR.font.code_jetbrains_mono) }.getOrNull()?.let(CodeFace::use)
            EpubSession.open(opened, heading, ::readingFont, java.io.File(context.cacheDir, ISLAND_DIR))
        } catch (_: FileNotFoundException) {
            _state.value = ReaderState.Failed(missingFile = true, epub = true)
            return
        } catch (_: EpubException) {
            _state.value = ReaderState.Failed(missingFile = false, epub = true)
            return
        }
        epub = session
        thumbnails = ReaderThumbnails(viewModelScope) { page, size, tone -> session.thumbnail(page, size, tone) }
        loadCover(session.opened.book.coverPath)
        val book = session.opened.book
        if (flow) {
            // From the PDF's page when switching; else where Dynamic Flow was left; else the PDF's page.
            val fromPdf = dynamic.takeSourcePage()
            locator = when {
                fromPdf != null -> session.locatorOfSourcePage(fromPdf)
                locator != null -> locator
                else -> Locator.decode(book.lastLocator) ?: book.lastPage?.let(session::locatorOfSourcePage)
            }
            dynamic.shown(reading = true)
        } else {
            if (locator == null) locator = Locator.decode(book.lastLocator)
            // No locator but a saved page (set by the measurement scripts): open there once laid out.
            if (locator == null) startPage = book.lastPage
        }
        cache = PageCache(session, viewModelScope, budgetBytes = 0, onEvicted = ::releaseLater).also { c ->
            bookJobs += viewModelScope.launch { c.images.collect { cacheImages.value = it } }
        }
        ensureEpubLayout()
    }

    /** A fixed-layout EPUB: one WebView page per spine item, read like a PDF (page turns, no reflow). */
    private suspend fun openFixedLayout(opened: OpenedEpub) {
        val source = FixedEpubPageSource(context, opened.archive, opened.pkg)
        fixed = source
        fixedBook = opened.book
        thumbnails = ReaderThumbnails(viewModelScope) { page, size, tone -> source.render(PageKey(page, size, tone)) }
        loadCover(opened.book.coverPath)
        cache = PageCache(source, viewModelScope, budgetBytes = 0, onEvicted = ::releaseLater).also { c ->
            bookJobs += viewModelScope.launch { c.images.collect { cacheImages.value = it } }
        }
        sizes.clear()
        repeat(source.pageCount) { page -> sizes[page] = com.flowfolio.core.model.PageSize(FIXED_W, FIXED_H) }
        anchorPage =
            (saved[KEY_ANCHOR] ?: opened.book.lastPage ?: 0).coerceIn(0, (source.pageCount - 1).coerceAtLeast(0))
        loadFixedNavigation(opened)
        publishReady()
        replan()
    }

    /** A fixed-layout EPUB's contents, from its navigation document (each entry points at a spine item, a page). */
    private fun loadFixedNavigation(opened: OpenedEpub) {
        val pathToPage = opened.pkg.spine.filter { it.linear && it.mediaType.contains("html") }
            .map { it.path }.withIndex().associate { (i, path) -> path to i }
        val outline = ArrayList<OutlineEntry>()
        fun walk(points: List<com.flowfolio.core.epub.NavPoint>, depth: Int) {
            for (p in points) {
                pathToPage[p.href.substringBefore('#')]?.let { outline += OutlineEntry(depth, it, p.label) }
                walk(p.children, depth + 1)
            }
        }
        walk(opened.pkg.toc, 0)
        _navigation.value = BookNavigation(outline = outline)
    }

    /**
     * Lays an EPUB out again when its page pane changed size (first open, fold, unfold, spreads on or off) or its text
     * settings changed (M3), keeping the reader on the same text. Returns true while a layout is under way (callers
     * wait for it).
     */
    private fun ensureEpubLayout(): Boolean {
        val session = epub
        val v = viewport
        val pane = v?.let {
            PixelSize(
                if (layoutFor(it) ==
                    ReaderLayout.SPREAD
                ) {
                    it.widthPx / 2
                } else {
                    it.widthPx
                },
                it.heightPx
            )
        }
        val shift = if (layoutFor(v) == ReaderLayout.SPREAD) (if (settings.spreadCoverAlone) 1 else 0) else null
        val wanted = pane?.let { EpubLayout(it, settings.text, shift) }
        return when {
            session == null -> false

            v == null || wanted == null -> true

            session.laidOut == wanted -> false

            pendingLayout == wanted -> true

            else -> {
                startLayout(session, v, wanted)
                true
            }
        }
    }

    private fun startLayout(session: EpubSession, v: Viewport, layout: EpubLayout) {
        layoutJob?.cancel()
        pendingLayout = layout
        if (session.laidOut != null) _relayingOut.value = true
        layoutJob = viewModelScope.launch {
            val started = android.os.SystemClock.elapsedRealtime()
            // The reader's chapter first when re-laying out an open book (ADR-0041); the first layout (and a
            // start page) is whole.
            val chapterFirst = session.laidOut != null && startPage == null
            anchorPage = session.layOut(layout, v.widthPx / v.widthDp, locator, chapterFirst)
            android.util.Log.i(
                LAYOUT_TAG,
                "laid out ${session.pageCount} pages in ${android.os.SystemClock.elapsedRealtime() - started} ms"
            )
            startPage?.let { page ->
                anchorPage = page.coerceIn(0, (session.pageCount - 1).coerceAtLeast(0))
                locator = session.locatorOf(anchorPage)
                startPage = null
            }
            pendingLayout = null
            _layoutRevision.update { it + 1 }
            _navigation.value = session.navigation()
            publishReady()
            // The equations of the pages in view, read on all cores before they are drawn (ADR-0041).
            (_state.value as? ReaderState.Ready)?.let { session.preloadIslands(pagesInView(it)) }
            replan()
            nextIslandWork()
            // The pages in view drawn (or a moment passed): the hourglass goes and the reader sees the new pages. The
            // pages about to be shown are drawn after them (M5: waiting for those too kept the hourglass up twice as
            // long). Logged for the phase exit criterion: the reader's page within 300 ms of a text change.
            val inView = (_state.value as? ReaderState.Ready)?.let(::pagesInView) ?: listOf(anchorPage)
            withTimeoutOrNull(DRAWN_WAIT_MS) { pageImages.first { images -> inView.all { it in images } } }
            android.util.Log.i(
                LAYOUT_TAG,
                "pages in view shown ${android.os.SystemClock.elapsedRealtime() - started} ms after the change"
            )
            _relayingOut.value = false
            finishLayout(session)
            session.trimIslands()
            session.packIslands()
        }
    }

    /**
     * The rest of a chapter-first layout: page numbers settle, images already drawn move to their new numbers, and
     * the contents, scrubber and links follow.
     */
    private suspend fun finishLayout(session: EpubSession) {
        val moves = session.finishLayout() ?: return
        cache?.rekey { key -> moves(key.page)?.let { key.copy(page = it) } }
        anchorPage = session.pageOf(locator ?: session.locatorOf(anchorPage))
        _layoutRevision.update { it + 1 }
        _links.value = emptyMap()
        linksRevision = -1
        thumbnails?.clear()
        _navigation.value = session.navigation()
        publishReady()
        replan()
    }

    /** Outline first (the contents list), then labels; both go through the PDFium thread between page renders. */
    private fun loadNavigation(book: OpenedBook) {
        viewModelScope.launch {
            val doc = book.document
            val outline = runCatching { doc.outline() }.getOrDefault(emptyList())
            _navigation.value = BookNavigation(outline = outline)
            // Printed page numbers (owner, 2026-10-18): the PDF's labels, else the numbers printed in its margins,
            // found once and kept with the book; with none, pages count from the cover.
            val stored = book.book.pageLabels
            val runs = if (stored != null) {
                PageLabels.decode(stored)
            } else {
                val fromPdf = (0 until doc.pageCount).map { runCatching { doc.pageLabel(it) }.getOrNull() }
                val found = if (fromPdf.any { it != null }) PageLabels.compress(fromPdf) else FolioScanner.scan(doc)
                runCatching { repository.savePageLabels(book.book.id, PageLabels.encode(found)) }
                found
            }
            _navigation.value = BookNavigation(outline, PageLabels.expand(runs, doc.pageCount))
        }
    }

    /** Jumps to [page] (contents, Go to page, scrubber). The curl follows the published unit. */
    fun goToPage(page: Int) {
        lastTurnAt = android.os.SystemClock.uptimeMillis()
        val ready = _state.value as? ReaderState.Ready ?: return
        anchorPage = page.coerceIn(0, (ready.pageCount - 1).coerceAtLeast(0))
        publishReady()
        replan()
        saveProgress()
    }

    /** In-book search (Phase 6 M1): the hits for the query typed so far. */
    private val _searchResults = MutableStateFlow<List<SearchHit>>(emptyList())
    internal val searchResults: StateFlow<List<SearchHit>> = _searchResults.asStateFlow()

    /** The query as typed, kept so re-opening the search panel restores the last search (owner, 2026-11-07). */
    private val _searchQuery = MutableStateFlow("")
    internal val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** The transient highlight marking the search hit the reader last jumped to; null when search is idle. */
    private val _searchHighlight = MutableStateFlow<Annotation?>(null)
    internal val searchHighlight: StateFlow<Annotation?> = _searchHighlight.asStateFlow()

    private var searchJob: Job? = null

    /** Searches the open book for [query] in the current view (reflowed text or the PDF's page text). */
    internal fun search(query: String) {
        searchJob?.cancel()
        if (query != _searchQuery.value) {
            _searchQuery.value = query
            _searchHighlight.value = null // a different query invalidates the last jump's highlight
        }
        val q = query.trim()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            _searchResults.value = if (q.isEmpty()) emptyList() else doSearch(q).take(SEARCH_CAP)
        }
    }

    private suspend fun doSearch(query: String): List<SearchHit> {
        val session = epub
        return if (session != null) searchEpub(session, query) else searchPdf(query)
    }

    private suspend fun searchEpub(session: EpubSession, query: String): List<SearchHit> {
        val hits = ArrayList<SearchHit>()
        for (chapter in 0 until session.chapterCount) {
            val (text, offsets) = session.chapterText(chapter)
            if (text.isEmpty()) continue
            for (range in findMatches(text, query)) {
                val start = offsets[range.first]
                val end = offsets[range.last]
                val locator = Locator(chapter, start)
                val page = session.pageOf(locator)
                hits += SearchHit(
                    page,
                    locator.encode(),
                    start,
                    end,
                    text.substring(range),
                    snippet(text, range),
                    labelFor(page)
                )
                if (hits.size >= SEARCH_HARD_CAP) return hits
            }
        }
        return hits
    }

    @Suppress("NestedBlockDepth")
    private suspend fun searchPdf(query: String): List<SearchHit> {
        val doc = document
        val hits = ArrayList<SearchHit>()
        if (doc != null) {
            outer@ for (page in 0 until doc.pageCount) {
                val pageText = runCatching { doc.pageText(page) }.getOrNull() ?: continue
                for (range in findMatches(pageText.text, query)) {
                    hits += SearchHit(
                        page,
                        null,
                        range.first,
                        range.last + 1,
                        pageText.text.substring(range),
                        snippet(pageText.text, range),
                        labelFor(page)
                    )
                    if (hits.size >= SEARCH_HARD_CAP) break@outer
                }
            }
        }
        return hits
    }

    /** The section (else chapter) a page is in, for a result's label. */
    private fun labelFor(page: Int): String? {
        val outline = _navigation.value.outline
        return PageLookup.subtitleAt(outline, page) ?: PageLookup.chapterTitleAt(outline, page)
    }

    /** Jumps to [hit] and highlights its text (cleared when the next search starts). */
    internal fun jumpTo(hit: SearchHit) {
        goToPage(hit.page)
        viewModelScope.launch(Dispatchers.Default) { _searchHighlight.value = buildHighlight(hit) }
    }

    private suspend fun buildHighlight(hit: SearchHit): Annotation? {
        val base = searchAnnotation(hit)
        val session = epub
        if (session != null) return session.place(listOf(base), setOf(hit.page)).primary.firstOrNull()
        val pageText = runCatching { document?.pageText(hit.page) }.getOrNull()
        return pageText?.let { base.copy(page = hit.page, rects = rectsOf(it, hit.start, hit.end)) }
    }

    private fun searchAnnotation(hit: SearchHit): Annotation = Annotation(
        id = SEARCH_ANNOTATION_ID,
        bookId = bookId,
        page = hit.page,
        label = null,
        start = hit.start,
        end = hit.end,
        text = hit.text,
        rects = emptyList(),
        color = HighlightColor.YELLOW,
        note = null,
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
        locator = hit.locator
    )

    private fun rectsOf(text: PageText, start: Int, end: Int): List<PageRect> {
        val out = ArrayList<PageRect>()
        for (i in start until end.coerceAtMost(text.length)) {
            if (text.hasBox(i)) out += PageRect(text.left(i), text.top(i), text.right(i), text.bottom(i))
        }
        return out
    }

    /** Narrator Mode (Phase 6 M3): reads the page aloud with Kokoro (sherpa-onnx), downloaded on first use. */
    private var narrator: Narrator? = null

    // External app-specific storage: private to the app, adb-pushable for testing, and the natural home
    // for a ~150 MB downloaded model (user-manageable, never backed up).
    private val voiceDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "voice")

    private val downloader = VoicePackDownloader(VoicePacks.kokoroMultiLang, voiceDir)

    private val _narrating = MutableStateFlow(false)
    internal val narrating: StateFlow<Boolean> = _narrating.asStateFlow()

    private val _narratorRate = MutableStateFlow(1f)
    internal val narratorRate: StateFlow<Float> = _narratorRate.asStateFlow()

    private val _narratorContinuous = MutableStateFlow(true)
    internal val narratorContinuous: StateFlow<Boolean> = _narratorContinuous.asStateFlow()

    private val _narratorVoice = MutableStateFlow<String?>(VoiceSpeakers.defaults.first().name)
    internal val narratorVoice: StateFlow<String?> = _narratorVoice.asStateFlow()

    internal val narratorVoices: List<Voice> get() = VoiceSpeakers.defaults

    internal val voicePackState: StateFlow<VoicePackState> = downloader.state

    fun toggleNarration() {
        if (_narrating.value) stopNarration() else startNarration()
    }

    fun setNarratorRate(rate: Float) {
        _narratorRate.value = rate
        narrator?.setRate(rate)
    }

    fun setNarratorContinuous(on: Boolean) {
        _narratorContinuous.value = on
    }

    fun setNarratorVoice(name: String) {
        _narratorVoice.value = name
        narrator?.setVoice(name)
    }

    private fun startNarration() {
        if (narrator != null) {
            _narrating.value = true
            readPage(anchorPage)
            return
        }
        viewModelScope.launch {
            downloader.ensureDownloaded()
            if (downloader.state.value != VoicePackState.Ready) return@launch
            val engine = VoiceEngine.load(voiceDir.absolutePath)
            val n = Narrator(engine)
            n.setRate(_narratorRate.value)
            n.setVoice(_narratorVoice.value ?: VoiceSpeakers.defaults.first().name)
            narrator = n
            _narrating.value = true
            readPage(anchorPage)
        }
    }

    private fun stopNarration() {
        narrator?.stop()
        _narrating.value = false
    }

    private fun readPage(page: Int) {
        viewModelScope.launch {
            val text = SpeechText.normalize(pageText(page)?.text.orEmpty())
            if (text.isBlank()) {
                advanceOrStop(page)
                return@launch
            }
            narrator?.speak(text) { advanceOrStop(page) }
        }
    }

    private fun advanceOrStop(page: Int) {
        if (!_narrating.value) return
        val count = (_state.value as? ReaderState.Ready)?.pageCount ?: 0
        if (_narratorContinuous.value && page + 1 < count) {
            goToPage(page + 1)
            readPage(page + 1)
        } else {
            _narrating.value = false
        }
    }

    /** The reader's root layout reports the window size whenever it changes (fold, unfold, rotation, resize). */
    fun onViewport(viewport: Viewport) {
        if (viewport == this.viewport) return
        this.viewport = viewport
        publishReady()
        replan()
    }

    /** The page curl settled on [unit] (a page in single layout, a spread in spread layout). */
    fun onUnitShown(unit: Int) {
        lastTurnAt = android.os.SystemClock.uptimeMillis()
        val ready = _state.value as? ReaderState.Ready ?: return
        if (unit == ready.unit) return
        val pageCount = ready.pageCount
        anchorPage = when (ready.layout) {
            ReaderLayout.SINGLE -> unit

            ReaderLayout.SPREAD -> if (Spreads.pagesOf(unit, pageCount, ready.coverAlone).contains(anchorPage)) {
                anchorPage
            } else {
                Spreads.anchorOf(unit, pageCount, ready.coverAlone)
            }
        }
        publishReady(unit)
        replan()
        saveProgress()
    }

    private fun saveProgress() {
        val page = anchorPage
        epub?.let { session ->
            val at = session.locatorOf(page).also { locator = it }
            progressJob?.cancel()
            progressJob = viewModelScope.launch {
                if (dynamic.reading) {
                    // A PDF's Dynamic Flow: its own place, and the PDF page it is in (the library's progress).
                    dynamic.savePlace(at.encode(), session.sourcePageOf(at))
                } else {
                    repository.saveEpubProgress(bookId, at.encode(), page, session.pageCount)
                }
            }
            return
        }
        // One small UPDATE per turn; conflated so a fast run of turns writes only the latest page.
        progressJob?.cancel()
        progressJob = viewModelScope.launch { repository.saveProgress(bookId, page) }
    }

    /** The zoomed view settled: render these tiles, most important first. Empty when leaving zoom (frees them all). */
    fun showTiles(keys: List<TileKey>) {
        val tone = (_state.value as? ReaderState.Ready)?.tone ?: PageTone.PRINTED
        if (keys.isEmpty()) tileCache?.clear() else tileCache?.request(keys.map { it.copy(tone = tone) })
    }

    /** Android asked apps to free memory: keep only what is on screen. */
    fun onTrimMemory() {
        tileCache?.clear()
        val ready = _state.value as? ReaderState.Ready ?: return
        cache?.trimToVisible(wanted, keep = if (ready.layout == ReaderLayout.SPREAD) 2 else 1)
    }

    private fun layoutFor(v: Viewport?): ReaderLayout = when {
        forcedLayout != null -> forcedLayout
        !settings.readerSpreads || v == null || settings.pageTurn == PageTurn.SCROLL -> ReaderLayout.SINGLE
        else -> chooseLayout(v.widthDp, v.heightDp)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun publishReady(unitOverride: Int? = null) {
        val session = epub
        val book = opened?.book ?: session?.opened?.book ?: fixedBook
        val pageCount = opened?.document?.pageCount ?: session?.pageCount ?: fixed?.pageCount
        val layingOut = session != null && ensureEpubLayout()
        if (book == null || pageCount == null || layingOut) return
        val layout = layoutFor(viewport)
        val coverAlone = settings.spreadCoverAlone
        val spread = layout == ReaderLayout.SPREAD
        val unit = unitOverride ?: if (spread) Spreads.unitOf(anchorPage, coverAlone) else anchorPage
        _state.update {
            ReaderState.Ready(
                title = book.title,
                pageCount = pageCount,
                layout = layout,
                unit = unit,
                unitCount = if (spread) Spreads.unitCount(pageCount, coverAlone) else pageCount,
                anchorPage = anchorPage,
                coverAlone = coverAlone,
                tone = settings.paperStyle.tone(
                    appDark = viewport?.dark == true,
                    rustic = settings.appStyle == AppStyle.RUSTIC
                ),
                turn = settings.pageTurn,
                pageAspect = session?.pane?.let { it.width.toFloat() / it.height }
                    ?: sizes[anchorPage]?.let { it.widthPt / it.heightPt } ?: LETTER_ASPECT,
                reflowable = session != null,
                scrollBands = session?.scrollBands,
                bookFont = session?.bookFont
            )
        }
    }

    /** Works out which pages to hold, at which size, in priority order, and hands that to the cache. */
    private fun replan() {
        val ready = _state.value as? ReaderState.Ready
        val v = viewport
        val session = epub
        val book = opened
        val fixedSource = fixed
        when {
            ready == null || v == null -> Unit
            session != null -> if (!ensureEpubLayout()) planEpub(ready, session)
            fixedSource != null -> planFixed(ready, v)
            book != null -> planPdf(ready, v, book)
        }
    }

    /** Fixed-layout pages are drawn at the pane size; the cache holds the visible unit and the ones a turn reveals. */
    private fun planFixed(ready: ReaderState.Ready, v: Viewport) {
        planJob?.cancel()
        planJob = viewModelScope.launch {
            val spread = ready.layout == ReaderLayout.SPREAD
            val paneWidth = if (spread) v.widthPx / 2 else v.widthPx
            val paneHeight = if (ready.turn == PageTurn.SCROLL) UNBOUNDED_HEIGHT else v.heightPx
            val units = listOf(ready.unit, ready.unit + 1, ready.unit - 1, ready.unit + 2)
                .filter { it in 0 until ready.unitCount }
            val pages = units.flatMap { u ->
                if (spread) Spreads.pagesOf(u, ready.pageCount, ready.coverAlone) else listOf(u)
            }
            val keys = pages.map { page ->
                val size = sizes.getOrPut(page) { com.flowfolio.core.model.PageSize(FIXED_W, FIXED_H) }
                PageKey(page, size.fitInside(paneWidth, paneHeight), ready.tone)
            }
            wanted = keys
            currentKeys.value = keys.associateBy { it.page }
            cache?.request(keys, newBudgetBytes = minOf(keys.sumOf { it.size.argbBytes }, MAX_CACHE_BYTES))
        }
    }

    private fun planPdf(ready: ReaderState.Ready, v: Viewport, book: OpenedBook) {
        planJob?.cancel()
        planJob = viewModelScope.launch {
            val spread = ready.layout == ReaderLayout.SPREAD
            val paneWidth = if (spread) v.widthPx / 2 else v.widthPx
            // Scroll shows pages at the full width, however tall that makes them; the other styles fit the screen.
            val paneHeight = if (ready.turn == PageTurn.SCROLL) UNBOUNDED_HEIGHT else v.heightPx
            // Visible unit, the one a forward turn reveals, the one a backward turn reveals, then one more ahead.
            // (Dropping the extra one saved no measurable memory on the Fold, ADR-0013.)
            val order = listOf(ready.unit, ready.unit + 1, ready.unit - 1, ready.unit + 2)
            val units = order.filter { it in 0 until ready.unitCount }
            val pages = units.flatMap { u ->
                if (spread) Spreads.pagesOf(u, ready.pageCount, ready.coverAlone) else listOf(u)
            }
            val keys = pages.mapNotNull { page ->
                val size = sizes.getOrPut(page) {
                    try {
                        book.document.pageSize(page)
                    } catch (_: PdfException) {
                        return@mapNotNull null
                    }
                }
                PageKey(page, size.fitInside(paneWidth, paneHeight), ready.tone)
            }
            wanted = keys
            currentKeys.value = keys.associateBy { it.page }
            cache?.request(keys, newBudgetBytes = minOf(keys.sumOf { it.size.argbBytes }, MAX_CACHE_BYTES))
            loadLinks(book, pages)
        }
    }

    /** EPUB pages are exactly the pane: the visible unit, the ones a turn reveals, and one more ahead. */
    private fun planEpub(ready: ReaderState.Ready, session: EpubSession) {
        val pane = session.pane ?: return
        val spread = ready.layout == ReaderLayout.SPREAD
        val units =
            listOf(ready.unit, ready.unit + 1, ready.unit - 1, ready.unit + 2).filter { it in 0 until ready.unitCount }
        val pages =
            units.flatMap { u -> if (spread) Spreads.pagesOf(u, ready.pageCount, ready.coverAlone) else listOf(u) }
        val keys = pages.map { PageKey(it, pane, ready.tone, session.islandRevision, session.layout) }
        wanted = keys
        currentKeys.value = keys.associateBy { it.page }
        cache?.request(keys, newBudgetBytes = minOf(keys.sumOf { it.size.argbBytes }, MAX_CACHE_BYTES))
        if (pendingLayout == null) nextIslandWork()
        viewModelScope.launch { loadEpubLinks(session, pages) }
    }

    /** An EPUB's links for [pages], found in the current layout; a new layout drops the old ones. */
    private suspend fun loadEpubLinks(session: EpubSession, pages: List<Int>) {
        val revision = session.revision
        if (revision != linksRevision) {
            linksRevision = revision
            _links.value = emptyMap()
        }
        for (page in pages) {
            if (page in _links.value) continue
            val found = session.links(page)
            if (session.revision == linksRevision) _links.update { it + (page to found) }
        }
    }

    /** EPUB island [id] at its own size on the current paper, for the zoomed view; null when not drawn yet. */
    internal suspend fun islandImage(id: Int): ImageBitmap? {
        val tone = (_state.value as? ReaderState.Ready)?.tone ?: return null
        return epub?.islandImage(id, tone)
    }

    /** The text of the EPUB footnote or endnote [href] points at, or null. */
    internal fun footnote(href: String): CharSequence? = epub?.footnote(href)

    /**
     * What the island WebView does next: lay out the chapter being read (its pages depend on it), draw the islands on
     * the pages in view and about to be, then lay out the other chapters, nearest first.
     */
    private fun nextIslandWork() {
        val session = epub ?: return
        val chapter = session.chapterOf(anchorPage)
        val tone = (_state.value as? ReaderState.Ready)?.tone ?: PageTone.PRINTED
        _islandWork.value = session.islands.toMeasure(chapter, onlyCurrent = true)
            ?: session.islandsToRender(wanted.map { it.page }, tone)
            ?: session.islands.toMeasure(chapter)
            ?: session.islandsToCache(anchorPage, tone)
    }

    /** When the reader last turned or jumped (uptime ms): drawing ahead for the disk cache waits for a still moment. */
    @Volatile
    internal var lastTurnAt = 0L
        private set

    /**
     * The island WebView finished [work]: sizes lay the book out again (keeping the reader's place); images are drawn
     * into the pages showing them.
     */
    internal fun onIslandsDone(
        work: IslandWork,
        boxes: Map<Int, IslandBox>,
        images: Map<Int, android.graphics.Bitmap>
    ) {
        val session = epub ?: return
        if (session.column != work.bodyPx to work.widthPx) return
        val disk = session.islands.disk
        if (work.render.isEmpty()) {
            session.islands.addSizes(work, boxes)?.let { measured ->
                viewModelScope.launch(Dispatchers.IO) { disk?.saveSizes(work.chapter, measured) }
            }
            layoutJob = viewModelScope.launch {
                anchorPage = session.relayOut(work.chapter, locator)
                _layoutRevision.update { it + 1 }
                _navigation.value = session.navigation()
                publishReady()
                replan()
            }
        } else {
            val drawn = session.islands.addImages(work, images)
            viewModelScope.launch(Dispatchers.IO) {
                drawn.forEach { d ->
                    disk?.save(d.id, d.ink, d.bitmap)
                    // Freed now rather than at the next garbage collection: a first visit draws many.
                    if (!d.inUse) d.bitmap.recycle()
                }
                // Background work is only stored; what comes next waits until it is on disk.
                if (work.background) withContext(Dispatchers.Main) { nextIslandWork() }
            }
            if (!work.background) replan()
        }
    }

    /** Links are small; once read for a page they are kept for the session. */
    private suspend fun loadLinks(book: OpenedBook, pages: List<Int>) {
        for (page in pages) {
            if (page in _links.value) continue
            val found = runCatching { book.document.links(page) }.getOrDefault(emptyList())
            _links.update { it + (page to found.mapNotNull { link -> link.toReaderLink() }) }
        }
    }

    /**
     * Evicted pages are GPU-backed bitmaps whose memory the garbage collector does not see, so it can lag far behind
     * (measured: +29% graphics/total memory over 300 turns without this). Recycle them once Compose can no longer be
     * drawing them, unless the page came back on screen in the meantime.
     */
    private fun releaseLater(image: ImageBitmap) {
        viewModelScope.launch {
            delay(RELEASE_DELAY_MS)
            if (image !in pageImages.value.values) image.asAndroidBitmap().recycle()
        }
    }

    /** The EPUB text size, from a pinch on the page (M3); the book is laid out again at the reader's place. */
    fun setTextSize(sizeSp: Int) {
        val text = settings.text.copy(sizeSp = sizeSp.coerceIn(ReaderText.MIN_SIZE_SP, ReaderText.MAX_SIZE_SP))
        viewModelScope.launch { settingsRepository.setReaderText(text) }
    }

    /** [font] as a typeface with its bold and italic, loaded once; null (the system serif) if it can't be read. */
    private fun readingFont(font: ReadingFont): android.graphics.Typeface? = fonts[font]
        ?: runCatching { context.resources.getFont(font.fontRes) }.getOrNull()?.also { fonts[font] = it }

    /** The cover, decoded small (its colours are all that is needed), off the main thread. */
    private fun loadCover(path: String?) {
        path ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = COVER_SAMPLE }
            _cover.value = runCatching { android.graphics.BitmapFactory.decodeFile(path, options) }.getOrNull()
        }
    }

    /**
     * The Reading Atmosphere's ambience, kept in step with the settings (owner, M5): it starts when the atmosphere
     * is on and one is chosen, follows a change of atmosphere or volume, and falls silent the moment it is switched
     * off. It plays for as long as this ViewModel lives, which is exactly as long as the book is open.
     */
    private fun playAmbience(settings: UserSettings) {
        val chosen = settings.atmosphereInForce()?.takeIf { settings.atmosphereOn && !it.silent }
        sound?.cancel()
        if (chosen == null) {
            ambience.stop()
            return
        }
        sound = viewModelScope.launch { ambience.play(chosen.ambient, chosen.volume, chosen.soundUri) }
    }

    override fun onCleared() {
        ambience.stop()
        thumbnails?.clear()
        cache?.clear()
        tileCache?.clear()
        source?.release()
        opened?.document?.close()
        epub?.close()
        fixed?.close()
        narrator?.release()
    }

    companion object {
        /** How long the hourglass waits for the new pages to be drawn after a layout, at most. */
        private const val DRAWN_WAIT_MS = 2_000L

        /** Logcat tag for EPUB layout times (M3's 300 ms target for a text change). */
        private const val LAYOUT_TAG = "FlowFolioLayout"
        const val ARG_BOOK_ID = "bookId"
        const val ARG_LAYOUT = "layout"
        private const val KEY_ANCHOR = "anchorPage"
        private const val KEY_LOCATOR = "locator"

        /** In-book search (Phase 6 M1): results shown, and a hard stop before a huge book is searched in full. */
        private const val SEARCH_CAP = 50
        private const val SEARCH_HARD_CAP = 500

        /** The id of the transient highlight marking a search hit; persisted highlights start at 1. */
        private const val SEARCH_ANNOTATION_ID = -1L

        /** The island disk cache (ADR-0021), under the app's cache directory. */
        private const val ISLAND_DIR = "islands"

        /** The page size (points) a fixed-layout EPUB's pages are reported at (Phase 6 M2). */
        private const val FIXED_W = 600f
        private const val FIXED_H = 800f

        /** Long enough for any frame that still referenced an evicted page to finish. */
        private const val RELEASE_DELAY_MS = 1_000L

        /** Zoom tiles held at once: 40 x 512 x 512 x 2 bytes = 20 MB, enough to cover the inner screen ~1.5 times. */
        private const val MAX_TILES = 40

        /** Hard cap for page images; the wish list (4 units) normally needs far less. */
        private const val MAX_CACHE_BYTES = 96L * 1024 * 1024

        /** US Letter, the most common textbook page, until the real page size is known. */
        const val LETTER_ASPECT = 8.5f / 11f

        private const val UNBOUNDED_HEIGHT = 1 shl 20

        /** Covers are decoded at a quarter of their size: only their colours are needed. */
        private const val COVER_SAMPLE = 4
    }
}
