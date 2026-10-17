package com.flowfolio.feature.reader

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.OpenedBook
import com.flowfolio.core.data.ReaderRepository
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.model.PixelSize
import com.flowfolio.core.model.UserSettings
import com.flowfolio.core.pdf.PdfException
import com.flowfolio.core.pdf.PdfLink
import com.flowfolio.feature.reader.cache.PageCache
import com.flowfolio.feature.reader.cache.PageKey
import com.flowfolio.feature.reader.cache.PdfPageSource
import com.flowfolio.feature.reader.cache.TileCache
import com.flowfolio.feature.reader.cache.TileKey
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.Spreads
import com.flowfolio.feature.reader.layout.chooseLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.FileNotFoundException
import javax.inject.Inject
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

sealed interface ReaderState {
    data object Loading : ReaderState

    data class NeedsPassword(val wrongPassword: Boolean) : ReaderState

    data class Failed(val missingFile: Boolean) : ReaderState

    data class Ready(
        val title: String,
        val pageCount: Int,
        val layout: ReaderLayout,
        /** Page in single layout, spread unit in spread layout. */
        val unit: Int,
        val unitCount: Int,
        val anchorPage: Int,
        /** Spread pairing (reader setting): the cover alone, or pages paired from the first. */
        val coverAlone: Boolean = true
    ) : ReaderState
}

/** Window in pixels and dp, as measured by the reader's root layout. */
data class Viewport(val widthPx: Int, val heightPx: Int, val widthDp: Float, val heightDp: Float)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: ReaderRepository,
    settingsRepository: SettingsRepository,
    private val saved: SavedStateHandle
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
    private var source: PdfPageSource? = null
    private var cache: PageCache? = null
    private var tileCache: TileCache? = null
    private val _tiles = MutableStateFlow<Map<TileKey, ImageBitmap>>(emptyMap())

    /** Zoom tiles that are ready (empty unless the reader is zoomed in). */
    val tiles: StateFlow<Map<TileKey, ImageBitmap>> = _tiles.asStateFlow()
    private val sizes = HashMap<Int, com.flowfolio.core.model.PageSize>()
    private val currentSizes = MutableStateFlow<Map<Int, PixelSize>>(emptyMap())
    private val cacheImages = MutableStateFlow<Map<PageKey, ImageBitmap>>(emptyMap())
    private var viewport: Viewport? = null
    private var wanted: List<PageKey> = emptyList()
    private var planJob: Job? = null
    private var progressJob: Job? = null

    /** Page index -> image at the current layout's size, for pages that are ready. */
    val pageImages: StateFlow<Map<Int, ImageBitmap>> = combine(cacheImages, currentSizes) { images, current ->
        current.mapNotNull { (page, size) -> images[PageKey(page, size)]?.let { page to it } }.toMap()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private var anchorPage: Int
        get() = saved[KEY_ANCHOR] ?: 0
        set(value) {
            saved[KEY_ANCHOR] = value
        }

    /** Reader settings (spreads on/off, pairing); a change re-lays the book out at the same page. */
    private var settings = UserSettings()

    init {
        open(password = null)
        viewModelScope.launch {
            settingsRepository.settings.collect {
                settings = it
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
            } catch (_: PdfException) {
                _state.value = ReaderState.Failed(missingFile = false)
                return@launch
            }
            opened = book
            val pageSource = PdfPageSource(book.document)
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

    /** Outline first (the contents list), then labels; both go through the PDFium thread between page renders. */
    private fun loadNavigation(book: OpenedBook) {
        viewModelScope.launch {
            val outline = runCatching { book.document.outline() }.getOrDefault(emptyList())
            _navigation.value = BookNavigation(outline = outline)
            val labels = (0 until book.document.pageCount).map { page ->
                runCatching { book.document.pageLabel(page) }.getOrNull()
            }
            _navigation.value = BookNavigation(outline, labels)
        }
    }

    /** Jumps to [page] (contents, Go to page, scrubber). The curl follows the published unit. */
    fun goToPage(page: Int) {
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
        // One small UPDATE per turn; conflated so a fast run of turns writes only the latest page.
        progressJob?.cancel()
        progressJob = viewModelScope.launch { repository.saveProgress(bookId, page) }
    }

    /** The zoomed view settled: render these tiles, most important first. Empty when leaving zoom (frees them all). */
    fun showTiles(keys: List<TileKey>) {
        if (keys.isEmpty()) tileCache?.clear() else tileCache?.request(keys)
    }

    /** Android asked apps to free memory: keep only what is on screen. */
    fun onTrimMemory() {
        tileCache?.clear()
        val ready = _state.value as? ReaderState.Ready ?: return
        cache?.trimToVisible(wanted, keep = if (ready.layout == ReaderLayout.SPREAD) 2 else 1)
    }

    private fun layoutFor(v: Viewport?): ReaderLayout = when {
        forcedLayout != null -> forcedLayout
        !settings.readerSpreads || v == null -> ReaderLayout.SINGLE
        else -> chooseLayout(v.widthDp, v.heightDp)
    }

    private fun publishReady(unitOverride: Int? = null) {
        val book = opened ?: return
        val pageCount = book.document.pageCount
        val layout = layoutFor(viewport)
        val coverAlone = settings.spreadCoverAlone
        val spread = layout == ReaderLayout.SPREAD
        val unit = unitOverride ?: if (spread) Spreads.unitOf(anchorPage, coverAlone) else anchorPage
        _state.update {
            ReaderState.Ready(
                title = book.book.title,
                pageCount = pageCount,
                layout = layout,
                unit = unit,
                unitCount = if (spread) Spreads.unitCount(pageCount, coverAlone) else pageCount,
                anchorPage = anchorPage,
                coverAlone = coverAlone
            )
        }
    }

    /** Works out which pages to hold, at which size, in priority order, and hands that to the cache. */
    private fun replan() {
        val ready = _state.value as? ReaderState.Ready
        val v = viewport
        val book = opened
        if (ready == null || v == null || book == null) return
        planJob?.cancel()
        planJob = viewModelScope.launch {
            val spread = ready.layout == ReaderLayout.SPREAD
            val paneWidth = if (spread) v.widthPx / 2 else v.widthPx
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
                PageKey(page, size.fitInside(paneWidth, v.heightPx))
            }
            wanted = keys
            currentSizes.value = keys.associate { it.page to it.size }
            cache?.request(keys, newBudgetBytes = minOf(keys.sumOf { it.size.argbBytes }, MAX_CACHE_BYTES))
            loadLinks(book, pages)
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

    override fun onCleared() {
        cache?.clear()
        tileCache?.clear()
        source?.release()
        opened?.document?.close()
    }

    companion object {
        const val ARG_BOOK_ID = "bookId"
        const val ARG_LAYOUT = "layout"
        private const val KEY_ANCHOR = "anchorPage"

        /** Long enough for any frame that still referenced an evicted page to finish. */
        private const val RELEASE_DELAY_MS = 1_000L

        /** Zoom tiles held at once: 40 x 512 x 512 x 2 bytes = 20 MB, enough to cover the inner screen ~1.5 times. */
        private const val MAX_TILES = 40

        /** Hard cap for page images; the wish list (4 units) normally needs far less. */
        private const val MAX_CACHE_BYTES = 96L * 1024 * 1024
    }
}
