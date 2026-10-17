package com.flowfolio.feature.reader

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.OpenedBook
import com.flowfolio.core.data.ReaderRepository
import com.flowfolio.core.model.PixelSize
import com.flowfolio.core.pdf.PdfException
import com.flowfolio.feature.reader.cache.PageCache
import com.flowfolio.feature.reader.cache.PageKey
import com.flowfolio.feature.reader.cache.PdfPageSource
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
        val anchorPage: Int
    ) : ReaderState
}

/** Window in pixels and dp, as measured by the reader's root layout. */
data class Viewport(val widthPx: Int, val heightPx: Int, val widthDp: Float, val heightDp: Float)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: ReaderRepository,
    private val saved: SavedStateHandle
) : ViewModel() {
    private val bookId: String = checkNotNull(saved[ARG_BOOK_ID]) { "reader needs a bookId" }
    private val forcedLayout: ReaderLayout? = saved.get<String>(ARG_LAYOUT)?.let {
        runCatching { ReaderLayout.valueOf(it.uppercase()) }.getOrNull()
    }

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private var opened: OpenedBook? = null
    private var source: PdfPageSource? = null
    private var cache: PageCache? = null
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

    init {
        open(password = null)
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
            // A recreated reader (rotation, process death) keeps its own position; a fresh open resumes where the
            // book was left (milestone 4).
            val start = if (saved.contains(KEY_ANCHOR)) anchorPage else book.book.lastPage ?: 0
            anchorPage = start.coerceIn(0, (book.document.pageCount - 1).coerceAtLeast(0))
            publishReady()
            replan()
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
        val ready = _state.value as? ReaderState.Ready ?: return
        if (unit == ready.unit) return
        val pageCount = ready.pageCount
        anchorPage = when (ready.layout) {
            ReaderLayout.SINGLE -> unit

            ReaderLayout.SPREAD -> if (Spreads.pagesOf(
                    unit,
                    pageCount
                ).contains(anchorPage)
            ) {
                anchorPage
            } else {
                Spreads.anchorOf(unit, pageCount)
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

    /** Android asked apps to free memory: keep only what is on screen. */
    fun onTrimMemory() {
        val ready = _state.value as? ReaderState.Ready ?: return
        cache?.trimToVisible(wanted, keep = if (ready.layout == ReaderLayout.SPREAD) 2 else 1)
    }

    private fun layoutFor(v: Viewport?): ReaderLayout =
        forcedLayout ?: v?.let { chooseLayout(it.widthDp, it.heightDp) } ?: ReaderLayout.SINGLE

    private fun publishReady(unitOverride: Int? = null) {
        val book = opened ?: return
        val pageCount = book.document.pageCount
        val layout = layoutFor(viewport)
        val unit = unitOverride ?: if (layout == ReaderLayout.SPREAD) Spreads.unitOf(anchorPage) else anchorPage
        _state.update {
            ReaderState.Ready(
                title = book.book.title,
                pageCount = pageCount,
                layout = layout,
                unit = unit,
                unitCount = if (layout == ReaderLayout.SPREAD) Spreads.unitCount(pageCount) else pageCount,
                anchorPage = anchorPage
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
            val pages = units.flatMap { u -> if (spread) Spreads.pagesOf(u, ready.pageCount) else listOf(u) }
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
        source?.release()
        opened?.document?.close()
    }

    companion object {
        const val ARG_BOOK_ID = "bookId"
        const val ARG_LAYOUT = "layout"
        private const val KEY_ANCHOR = "anchorPage"

        /** Long enough for any frame that still referenced an evicted page to finish. */
        private const val RELEASE_DELAY_MS = 1_000L

        /** Hard cap for page images; the wish list (4 units) normally needs far less. */
        private const val MAX_CACHE_BYTES = 96L * 1024 * 1024
    }
}
