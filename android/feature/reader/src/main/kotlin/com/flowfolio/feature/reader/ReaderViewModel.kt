package com.flowfolio.feature.reader

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.FolioScanner
import com.flowfolio.core.data.NotYetReadableException
import com.flowfolio.core.data.OpenedBook
import com.flowfolio.core.data.ReaderRepository
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.designsystem.R as DesignR
import com.flowfolio.core.epub.EpubException
import com.flowfolio.core.model.PageLabels
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.PixelSize
import com.flowfolio.core.model.UserSettings
import com.flowfolio.core.pdf.PdfDocument
import com.flowfolio.core.pdf.PdfException
import com.flowfolio.core.pdf.PdfLink
import com.flowfolio.core.reflow.Locator
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReaderState {
    data object Loading : ReaderState

    data class NeedsPassword(val wrongPassword: Boolean) : ReaderState

    data class Failed(val missingFile: Boolean, val notYetReadable: Boolean = false) : ReaderState

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
        /**
         * The pages are laid out by FlowFolio (an EPUB): selection, highlights, ribbons and zoom are PDF-only until
         * Phase 2 milestone 4.
         */
        val reflowable: Boolean = false
    ) : ReaderState
}

/** Window in pixels and dp, as measured by the reader's root layout, and whether the app is showing dark mode. */
data class Viewport(
    val widthPx: Int,
    val heightPx: Int,
    val widthDp: Float,
    val heightDp: Float,
    val dark: Boolean = false
)

// One handler per reader event, with a PDF path and an EPUB path for opening, layout and planning.
@Suppress("TooManyFunctions")
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: ReaderRepository,
    settingsRepository: SettingsRepository,
    private val saved: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val bookId: String = checkNotNull(saved[ARG_BOOK_ID]) { "reader needs a bookId" }
    private val forcedLayout: ReaderLayout? = saved.get<String>(ARG_LAYOUT)?.let {
        runCatching { ReaderLayout.valueOf(it.uppercase()) }.getOrNull()
    }

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _navigation = MutableStateFlow(BookNavigation())

    /** Outline and printed page labels, loaded in the background after the book opens. */
    val navigation: StateFlow<BookNavigation> = _navigation.asStateFlow()

    private val _links = MutableStateFlow<Map<Int, List<PdfLink>>>(emptyMap())

    /** Links of the pages read so far (and the ones about to be shown), by page. */
    val links: StateFlow<Map<Int, List<PdfLink>>> = _links.asStateFlow()

    private var opened: OpenedBook? = null

    /** Page thumbnails for the riffle scrubber's loupe (ADR-0022), once the book is open. */
    internal var thumbnails: ReaderThumbnails? = null
        private set

    private val _cover = MutableStateFlow<android.graphics.Bitmap?>(null)

    /** The book's cover, small, for the riffle scrubber's board colours; null without one. */
    internal val cover: StateFlow<android.graphics.Bitmap?> = _cover.asStateFlow()

    /** The open EPUB, when the book is one (ADR-0019). */
    private var epub: EpubSession? = null

    /** Where an EPUB reader is: kept across layouts (fold, unfold, spreads) and saved with the book. */
    private var locator: Locator?
        get() = Locator.decode(saved[KEY_LOCATOR])
        set(value) {
            saved[KEY_LOCATOR] = value?.encode()
        }
    private var layoutJob: Job? = null

    /** An EPUB's saved page when it has no locator yet, used by its first layout. */
    private var startPage: Int? = null

    /** The pane a layout is under way for. */
    private var pendingPane: PixelSize? = null

    private val _islandWork = MutableStateFlow<IslandWork?>(null)

    /** The next equations, SVG or tables for the island WebView to lay out or draw (EPUBs only). */
    internal val islandWork: StateFlow<IslandWork?> = _islandWork.asStateFlow()

    /** The open EPUB's files, for the island WebView. */
    internal val epubArchive get() = epub?.opened?.archive

    /** The open document (for page text when selecting); null until the book is open. */
    internal val document: PdfDocument? get() = opened?.document
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
        current.mapNotNull { (page, key) -> images[key]?.let { page to it } }.toMap()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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

    init {
        open(password = null)
        viewModelScope.launch {
            settingsRepository.settings.collect {
                settings = it
                _userSettings.value = it
                publishReady()
                replan()
            }
        }
    }

    fun submitPassword(password: String) = open(password)

    private fun open(password: String?) {
        viewModelScope.launch {
            _state.value = ReaderState.Loading
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
                viewModelScope.launch { c.images.collect { cacheImages.value = it } }
            }
            tileCache = TileCache(pageSource, viewModelScope, MAX_TILES, onEvicted = ::releaseLater).also { c ->
                viewModelScope.launch { c.tiles.collect { _tiles.value = it } }
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

    /** An EPUB: parse it, then lay it out once the reader knows its pane ([ensureEpubLayout]). */
    private suspend fun openEpub() {
        val session = try {
            // Headings in the app's display face (Fraunces), as on the library's covers.
            val heading = runCatching { context.resources.getFont(DesignR.font.fraunces) }.getOrNull()
            EpubSession.open(repository.openEpub(bookId), heading, java.io.File(context.cacheDir, ISLAND_DIR))
        } catch (_: FileNotFoundException) {
            _state.value = ReaderState.Failed(missingFile = true)
            return
        } catch (_: EpubException) {
            _state.value = ReaderState.Failed(missingFile = false)
            return
        }
        epub = session
        thumbnails = ReaderThumbnails(viewModelScope) { page, size, tone -> session.thumbnail(page, size, tone) }
        loadCover(session.opened.book.coverPath)
        if (locator == null) locator = Locator.decode(session.opened.book.lastLocator)
        // No locator but a saved page (set by the measurement scripts): open there once laid out.
        if (locator == null) startPage = session.opened.book.lastPage
        cache = PageCache(session, viewModelScope, budgetBytes = 0, onEvicted = ::releaseLater).also { c ->
            viewModelScope.launch { c.images.collect { cacheImages.value = it } }
        }
        ensureEpubLayout()
    }

    /**
     * Lays an EPUB out again when its page pane changed size (first open, fold, unfold, spreads on or off), keeping the
     * reader on the same text. Returns true while a layout is under way (callers wait for it).
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
        return when {
            session == null -> false

            v == null || pane == null -> true

            session.pane == pane -> false

            pendingPane == pane -> true

            else -> {
                startLayout(session, v, pane)
                true
            }
        }
    }

    private fun startLayout(session: EpubSession, v: Viewport, pane: PixelSize) {
        layoutJob?.cancel()
        pendingPane = pane
        layoutJob = viewModelScope.launch {
            val density = v.widthPx / v.widthDp
            anchorPage = session.layOut(pane, EpubSession.BODY_SP * density, density, locator)
            startPage?.let { page ->
                anchorPage = page.coerceIn(0, (session.pageCount - 1).coerceAtLeast(0))
                locator = session.locatorOf(anchorPage)
                startPage = null
            }
            pendingPane = null
            _navigation.value = session.navigation()
            publishReady()
            replan()
            nextIslandWork()
        }
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
                repository.saveEpubProgress(bookId, at.encode(), page, session.pageCount)
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

    private fun publishReady(unitOverride: Int? = null) {
        val session = epub
        val book = opened?.book ?: session?.opened?.book
        val pageCount = opened?.document?.pageCount ?: session?.pageCount
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
                tone = settings.paperStyle.tone(appDark = viewport?.dark == true),
                turn = settings.pageTurn,
                pageAspect = session?.pane?.let { it.width.toFloat() / it.height }
                    ?: sizes[anchorPage]?.let { it.widthPt / it.heightPt } ?: LETTER_ASPECT,
                reflowable = session != null
            )
        }
    }

    /** Works out which pages to hold, at which size, in priority order, and hands that to the cache. */
    private fun replan() {
        val ready = _state.value as? ReaderState.Ready
        val v = viewport
        val session = epub
        val book = opened
        when {
            ready == null || v == null -> Unit
            session != null -> if (!ensureEpubLayout()) planEpub(ready, session)
            book != null -> planPdf(ready, v, book)
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
        val keys = pages.map { PageKey(it, pane, ready.tone, session.revision) }
        wanted = keys
        currentKeys.value = keys.associateBy { it.page }
        cache?.request(keys, newBudgetBytes = minOf(keys.sumOf { it.size.argbBytes }, MAX_CACHE_BYTES))
        if (pendingPane == null) nextIslandWork()
    }

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
            _links.update { it + (page to found) }
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

    /** The cover, decoded small (its colours are all that is needed), off the main thread. */
    private fun loadCover(path: String?) {
        path ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = COVER_SAMPLE }
            _cover.value = runCatching { android.graphics.BitmapFactory.decodeFile(path, options) }.getOrNull()
        }
    }

    override fun onCleared() {
        thumbnails?.clear()
        cache?.clear()
        tileCache?.clear()
        source?.release()
        opened?.document?.close()
        epub?.close()
    }

    companion object {
        const val ARG_BOOK_ID = "bookId"
        const val ARG_LAYOUT = "layout"
        private const val KEY_ANCHOR = "anchorPage"
        private const val KEY_LOCATOR = "locator"

        /** The island disk cache (ADR-0021), under the app's cache directory. */
        private const val ISLAND_DIR = "islands"

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
