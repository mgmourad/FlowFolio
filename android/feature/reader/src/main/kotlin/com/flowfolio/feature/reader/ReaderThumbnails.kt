package com.flowfolio.feature.reader

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.flowfolio.core.model.PixelSize
import com.flowfolio.feature.reader.riffle.PageThumbnailProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The riffle scrubber's page thumbnails (ADR-0022): 16-bit images at loupe size, drawn by the book's own page source
 * ([render]), held under [BUDGET_BYTES] apart from the page cache. Prefetching follows the finger: a new request
 * drops the old one, so a fast riffle never queues work for pages already passed. A change of size or paper starts
 * afresh.
 */
internal class ReaderThumbnails(
    private val scope: CoroutineScope,
    private val render: suspend (page: Int, size: PixelSize, tone: PageTone) -> ImageBitmap?
) : PageThumbnailProvider {
    private var size = PixelSize(1, 1)
    private var tone = PageTone.PRINTED
    private var prefetching: Job? = null
    private val images = object : LruCache<Int, ImageBitmap>(BUDGET_BYTES) {
        override fun sizeOf(key: Int, value: ImageBitmap) = value.width * value.height * BYTES_PER_PIXEL

        // Recycled once the loupe can no longer be drawing them (they are GPU-backed: the GC would lag behind).
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: ImageBitmap, newValue: ImageBitmap?) {
            scope.launch {
                delay(RELEASE_DELAY_MS)
                oldValue.asAndroidBitmap().recycle()
            }
        }
    }

    /** Thumbnails [size] pixels on [tone]'s paper; anything drawn otherwise is dropped. */
    @Synchronized
    fun use(size: PixelSize, tone: PageTone) {
        if (size == this.size && tone == this.tone) return
        this.size = size
        this.tone = tone
        prefetching?.cancel()
        images.evictAll()
    }

    @Synchronized
    override fun cached(page: Int): ImageBitmap? = images.get(page)

    override suspend fun load(page: Int): ImageBitmap? = cached(page) ?: draw(page)

    private suspend fun draw(page: Int): ImageBitmap? {
        val (want, paper) = synchronized(this) { size to tone }
        val image = render(page, want, paper)
        if (image != null) {
            synchronized(this) {
                if (want == size && paper == tone) images.put(page, image)
            }
        }
        return image
    }

    override fun prefetch(pages: List<Int>) {
        prefetching?.cancel()
        prefetching = scope.launch {
            for (page in pages) if (cached(page) == null) load(page)
        }
    }

    fun clear() {
        prefetching?.cancel()
        images.evictAll()
    }

    private companion object {
        /** About 120 loupe-sized pages on the unfolded Fold. */
        const val BUDGET_BYTES = 16 * 1024 * 1024
        const val BYTES_PER_PIXEL = 2
        const val RELEASE_DELAY_MS = 1_000L
    }
}
