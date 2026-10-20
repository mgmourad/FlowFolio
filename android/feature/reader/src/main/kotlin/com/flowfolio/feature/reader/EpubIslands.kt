package com.flowfolio.feature.reader

import android.graphics.Bitmap
import android.util.LruCache
import com.flowfolio.core.reflow.Block
import com.flowfolio.core.reflow.IslandSpan
import com.flowfolio.core.reflow.Page
import com.flowfolio.core.reflow.ReflowBook
import com.flowfolio.core.reflow.Slice

/** Work for the island WebView: lay out [items] (and draw the ones in [render]) in the page of [chapterPath]. */
internal data class IslandWork(
    val chapter: Int,
    val chapterPath: String,
    val items: List<IslandItem>,
    val bodyPx: Float,
    val widthPx: Int,
    /** Islands to draw once laid out; empty when only their sizes are needed. */
    val render: Set<Int> = emptySet(),
    /** The paper's ink (ARGB): islands are drawn in it on a transparent background. */
    val ink: Int = BLACK
) {
    private companion object {
        const val BLACK = 0xFF000000.toInt()
    }
}

/**
 * The book's islands (ADR-0019): their measured sizes for the current text size and column width, and their images.
 * Sizes feed pagination; images are drawn into pages. Images are cached under a byte budget and drawn again when a page
 * that shows them comes back.
 */
internal class EpubIslands(private val book: ReflowBook) {
    private var measuredFor: Pair<Float, Int>? = null
    private val sizes = HashMap<Int, IslandBox>()
    private val spans: Map<Int, IslandSpan> = book.chapters.flatMap { c ->
        c.blocks.filterIsInstance<Block.Text>().flatMap {
            it.text.getSpans(0, it.text.length, IslandSpan::class.java).asList()
        }
    }.associateBy { it.id }
    private val images = object : LruCache<Int, Bitmap>(IMAGE_BYTES) {
        override fun sizeOf(key: Int, value: Bitmap) = value.allocationByteCount

        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            spans[key]?.let { if (it.image === oldValue) it.image = null }
        }
    }

    /** The ink the cached images were drawn in; another paper draws them again. */
    private var imageInk: Int? = null

    /** Raised whenever images are added, so the reader draws the pages showing them again. */
    var revision = 0
        private set

    val any: Boolean get() = book.chapters.indices.any { book.islands(it).isNotEmpty() }

    /** A new text size or column width: sizes and images no longer fit. Inline islands are sized to match. */
    @Synchronized
    fun prepare(bodyPx: Float, widthPx: Int) {
        if (measuredFor != bodyPx to widthPx) {
            measuredFor = bodyPx to widthPx
            sizes.clear()
            images.evictAll()
        }
        applySpans()
    }

    /**
     * Inline islands take their measured size in the text (until then, a few ems), scaled down to the column when
     * wider.
     */
    private fun applySpans() {
        val column = measuredFor?.second ?: 0
        spans.values.forEach { span ->
            val box = sizes[span.id]
            val scale = if (box != null && column in 1 until box.w) column.toFloat() / box.w else 1f
            span.width = box?.let { (it.w * scale).toInt() } ?: 0
            span.ascent = box?.let { (it.baseline * scale).toInt() } ?: 0
            span.descent = box?.let { ((it.h - it.baseline) * scale).toInt() } ?: 0
        }
    }

    @Synchronized
    fun size(id: Int): Pair<Int, Int>? = sizes[id]?.let { it.w to it.h }

    @Synchronized
    fun image(id: Int): Bitmap? = images.get(id)

    /**
     * The next chapter whose islands still need sizes: [current] first (it is being read), then the nearest ones
     * around it. One chapter at a time, so the WebView's page stays small and only that chapter is laid out again.
     */
    @Synchronized
    fun toMeasure(current: Int, onlyCurrent: Boolean = false): IslandWork? {
        val (bodyPx, width) = measuredFor ?: return null
        val order = if (onlyCurrent) {
            listOf(current)
        } else {
            (0 until book.chapters.size).sortedBy { kotlin.math.abs(it - current) }
        }
        return order.firstOrNull { c -> items(c).any { it.id !in sizes } }?.let { chapter ->
            IslandWork(chapter, book.chapters[chapter].path, items(chapter), bodyPx, width)
        }
    }

    /** Islands on [pages] that have a size but no image yet (in [ink]): drawn in their chapter's own page. */
    @Synchronized
    fun toRender(pages: List<Page>, ink: Int): IslandWork? {
        val (bodyPx, width) = measuredFor ?: return null
        if (imageInk != ink) {
            images.evictAll()
            imageInk = ink
        }
        return pages.asSequence()
            .map { page -> page.chapter to idsOn(page).filter { it in sizes && images.get(it) == null }.toSet() }
            .firstOrNull { (_, missing) -> missing.isNotEmpty() }
            ?.let { (chapter, missing) ->
                IslandWork(chapter, book.chapters[chapter].path, items(chapter), bodyPx, width, missing, ink)
            }
    }

    /**
     * Sizes for [work]'s islands; any the WebView could not lay out keep a placeholder size, so they are not retried.
     */
    @Synchronized
    fun addSizes(work: IslandWork, boxes: Map<Int, IslandBox>) {
        if (measuredFor != work.bodyPx to work.widthPx) return
        work.items.forEach { item ->
            sizes[item.id] = boxes[item.id]
                ?: IslandBox(item.id, work.widthPx / 2, (work.bodyPx * 2).toInt(), work.bodyPx.toInt(), 0, 0)
        }
        applySpans()
    }

    @Synchronized
    fun addImages(work: IslandWork, drawn: Map<Int, Bitmap>) {
        if (work.ink != imageInk) return
        drawn.forEach { (id, bmp) ->
            images.put(id, bmp)
            spans[id]?.image = bmp
        }
        revision++
    }

    private fun items(chapter: Int): List<IslandItem> {
        val inline = spans.keys
        return book.islands(chapter).map { (id, xml) -> IslandItem(id, xml, id in inline) }
    }

    private fun idsOn(page: Page): List<Int> {
        val blocks = book.chapters[page.chapter].blocks
        return page.slices.flatMap { s ->
            when (val b = blocks[s.block]) {
                is Block.Island -> listOf(b.id)
                is Block.Text -> if (s is Slice.Lines) inlineIds(b, s) else emptyList()
                is Block.Image -> emptyList()
            }
        }
    }

    private fun inlineIds(b: Block.Text, s: Slice.Lines): List<Int> {
        val from = s.layout.getLineStart(s.first)
        val to = s.layout.getLineEnd(s.last - 1)
        return b.text.getSpans(from, to, IslandSpan::class.java).map { it.id }
    }

    private companion object {
        const val IMAGE_BYTES = 32 * 1024 * 1024
    }
}
