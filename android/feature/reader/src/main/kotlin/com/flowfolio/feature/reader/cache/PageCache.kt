package com.flowfolio.feature.reader.cache

import androidx.compose.ui.graphics.ImageBitmap
import com.flowfolio.core.model.PixelSize
import com.flowfolio.feature.reader.PageTone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One rendered page at one size and paper tone. Size and tone are part of the key, so a layout or paper
 * change never shows a stale image.
 */
data class PageKey(
    val page: Int,
    val size: PixelSize,
    val tone: PageTone = PageTone.PRINTED,
    /** Raised when something drawn on the page changed (an EPUB's equations became ready), to draw it again. */
    val revision: Int = 0,
    /**
     * The EPUB layout the page belongs to. An image of an earlier [revision] in the same layout shows the same text
     * and may stand in until the new one is drawn; one from another layout may show other text.
     */
    val layout: Int = 0
)

/** Renders pages. The reader's implementation returns GPU-backed (HARDWARE) images to avoid a second CPU copy. */
fun interface PageSource {
    suspend fun render(key: PageKey): ImageBitmap?
}

/**
 * Page images held under one byte budget (Phase 1 memory design). The reader calls [request] with the pages it wants,
 * most important first (visible pages, then the ones a turn reveals, then prefetch). Only the prefix of that list that
 * fits the budget is rendered, in order; when over budget, held pages outside that prefix go first, least recently
 * used first. Evicted images go to [onEvicted]; the owner recycles them after a short delay (not immediately, because
 * Compose may still be drawing them for one more frame).
 */
class PageCache(
    private val source: PageSource,
    private val scope: CoroutineScope,
    budgetBytes: Long,
    /** Called for every image that leaves the cache, so its owner can release GPU memory promptly. */
    private val onEvicted: (ImageBitmap) -> Unit = {}
) {
    private val lru = LinkedHashMap<PageKey, ImageBitmap>(INITIAL_CAPACITY, LOAD_FACTOR, true)
    private val _images = MutableStateFlow<Map<PageKey, ImageBitmap>>(emptyMap())
    private var job: Job? = null

    /** Images currently held, for the UI to draw. */
    val images: StateFlow<Map<PageKey, ImageBitmap>> = _images.asStateFlow()

    var budgetBytes: Long = budgetBytes
        private set

    val heldBytes: Long get() = synchronized(lru) { lru.keys.sumOf { it.size.argbBytes } }

    /** Replaces the wish list. Any render in flight for the previous list is abandoned after its current page. */
    fun request(wanted: List<PageKey>, newBudgetBytes: Long = budgetBytes) {
        budgetBytes = newBudgetBytes
        job?.cancel()
        trim(wanted)
        job = scope.launch {
            val missing = fitting(wanted).filterNot { key -> synchronized(lru) { lru.containsKey(key) } }
            for (key in missing) {
                val image = source.render(key) ?: continue
                synchronized(lru) { lru[key] = image }
                trim(wanted)
            }
        }
    }

    /**
     * Page numbers moved (an EPUB's estimated chapters were laid out): each held image goes to the key [move] gives it,
     * or is released when it gives none, so pages already drawn are not drawn again.
     */
    fun rekey(move: (PageKey) -> PageKey?) {
        job?.cancel()
        synchronized(lru) {
            val held = lru.entries.map { it.key to it.value }
            lru.clear()
            for ((key, image) in held) {
                val moved = move(key)
                if (moved == null) onEvicted(image) else lru[moved] = image
            }
        }
        publish()
    }

    /** System memory pressure: keep only the first [keep] wanted pages (the visible ones). */
    fun trimToVisible(wanted: List<PageKey>, keep: Int) {
        val visible = wanted.take(keep).toSet()
        synchronized(lru) {
            val iterator = lru.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key !in visible) {
                    iterator.remove()
                    onEvicted(entry.value)
                }
            }
        }
        publish()
    }

    fun clear() {
        job?.cancel()
        synchronized(lru) {
            lru.values.forEach(onEvicted)
            lru.clear()
        }
        publish()
    }

    /** The prefix of [wanted] that fits in the budget on its own. */
    private fun fitting(wanted: List<PageKey>): List<PageKey> {
        var total = 0L
        return wanted.distinct().takeWhile { key ->
            total += key.size.argbBytes
            total <= budgetBytes || key == wanted.first()
        }
    }

    private fun trim(wanted: List<PageKey>) {
        val keep = fitting(wanted)
        val keepSet = keep.toSet()
        synchronized(lru) {
            // Evict pages outside the fitting prefix of the wish list, least recently used first. What remains is a
            // subset of that prefix, which by construction fits the budget (except a single page larger than it).
            val iterator = lru.entries.iterator()
            while (bytesLocked() > budgetBytes && iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key !in keepSet) {
                    iterator.remove()
                    onEvicted(entry.value)
                }
            }
        }
        publish()
    }

    private fun bytesLocked(): Long = lru.keys.sumOf { it.size.argbBytes }

    private fun publish() {
        _images.value = synchronized(lru) { HashMap(lru) }
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
