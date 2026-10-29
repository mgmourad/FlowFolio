package com.flowfolio.core.reflow

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Spanned
import android.util.LruCache

/**
 * The images set in lines of text ([InlineImageSpan]), read from the book for the pages about to be drawn and kept
 * under a small byte budget; a span whose image leaves the cache is cleared, and read again when its page is drawn.
 */
class InlineImages(private val book: ReflowBook, budgetBytes: Int = DEFAULT_BYTES) {
    private val cache = object : LruCache<InlineImageSpan, Bitmap>(budgetBytes) {
        override fun sizeOf(key: InlineImageSpan, value: Bitmap) = value.allocationByteCount

        override fun entryRemoved(evicted: Boolean, key: InlineImageSpan, oldValue: Bitmap, newValue: Bitmap?) {
            if (key.image === oldValue) key.image = null
        }
    }

    /** Reads the images of [page]'s inline spans that are not in memory. */
// Each continue skips what needs no reading.
    @Suppress("LoopWithTooManyJumpStatements")
    fun loadFor(page: Page) {
        for (slice in page.slices) {
            if (slice !is Slice.Lines) continue
            val text = slice.layout.text as? Spanned ?: continue
            val from = slice.layout.getLineStart(slice.first)
            val to = slice.layout.getLineEnd(slice.last - 1)
            for (span in text.getSpans(from, to, InlineImageSpan::class.java)) {
                if (span.image?.isRecycled == false) continue
                val bytes = book.archive.read(span.path) ?: continue
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                span.image = bmp
                cache.put(span, bmp)
            }
        }
    }

    fun clear() = cache.evictAll()

    private companion object {
        const val DEFAULT_BYTES = 16 * 1024 * 1024
    }
}
