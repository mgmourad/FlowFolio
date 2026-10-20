package com.flowfolio.feature.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.util.LruCache
import com.flowfolio.core.reflow.Block
import com.flowfolio.core.reflow.IslandSpan
import com.flowfolio.core.reflow.Page
import com.flowfolio.core.reflow.ReflowBook
import com.flowfolio.core.reflow.Slice

/** An island just drawn, to store under [ink]; when not [inUse], its bitmap can be recycled once stored. */
internal class DrawnIsland(val id: Int, val ink: Int?, val bitmap: Bitmap, val inUse: Boolean)

/** Work for the island WebView: lay out [items] (and draw the ones in [render]) in the page of [chapterPath]. */
internal data class IslandWork(
    val chapter: Int,
    val chapterPath: String,
    val items: List<IslandItem>,
    val bodyPx: Float,
    val widthPx: Int,
    /** Islands to draw once laid out; empty when only their sizes are needed. */
    val render: Set<Int> = emptySet(),
    /** The paper's ink (ARGB) for SVG and tables; equations are drawn in black and tinted when shown. */
    val ink: Int = BLACK,
    /** Drawing ahead for the disk cache (ADR-0021): waits while pages are being turned. */
    val background: Boolean = false
) {
    private companion object {
        const val BLACK = 0xFF000000.toInt()
    }
}

/**
 * The book's islands (ADR-0019, ADR-0021): their measured sizes for the current text size and column width, and their
 * images. Sizes feed pagination; images are drawn into pages. Both are kept on disk ([IslandStore]); images in use are
 * also kept in memory under a byte budget, tinted to the paper's ink.
 */
internal class EpubIslands(private val book: ReflowBook) {
    private var measuredFor: Pair<Float, Int>? = null
    private var store: IslandStore? = null
    private val sizes = HashMap<Int, IslandBox>()
    private val spans: Map<Int, IslandSpan> = book.chapters.flatMap { c ->
        c.blocks.filterIsInstance<Block.Text>().flatMap {
            it.text.getSpans(0, it.text.length, IslandSpan::class.java).asList()
        }
    }.associateBy { it.id }

    /** Equations, which are stored in black whatever the paper. */
    private val math: Set<Int> = book.chapters.indices.flatMap { book.islands(it) }
        .filter { (_, xml) -> xml.startsWith("<math") }.map { it.first }.toSet()
    private val images = object : LruCache<Int, Bitmap>(IMAGE_BYTES) {
        override fun sizeOf(key: Int, value: Bitmap) = value.allocationByteCount

        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            spans[key]?.let { if (it.image === oldValue) it.image = null }
        }
    }

    /** The ink the images in memory are drawn in; another paper loads them again. */
    @Volatile private var imageInk: Int? = null

    /** Raised whenever images are added, so the reader draws the pages showing them again. */
    var revision = 0
        private set

    val any: Boolean get() = book.chapters.indices.any { book.islands(it).isNotEmpty() }

    /**
     * A layout for [bodyPx] text in a column [widthPx] wide, with [store] holding what earlier visits measured and drew
     * for it. Sizes found there lay the book out correctly at once. Off the main thread (reads the disk).
     */
    fun prepare(bodyPx: Float, widthPx: Int, store: IslandStore?) {
        val stored = store?.sizes().orEmpty()
        synchronized(this) {
            if (measuredFor != bodyPx to widthPx) {
                measuredFor = bodyPx to widthPx
                sizes.clear()
                images.evictAll()
            }
            this.store = store
            sizes.putAll(stored)
            applySpans()
        }
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

    /** Pages are drawn in [ink]: images in memory in another ink are dropped (they load again from disk). */
    @Synchronized
    fun useInk(ink: Int) {
        if (imageInk != ink) {
            images.evictAll()
            imageInk = ink
        }
    }

    /** An island's image in the current ink: from memory, else from disk (off the main thread), else null. */
    fun image(id: Int): Bitmap? = synchronized(this) { images.get(id) } ?: loadStored(id)

    private fun loadStored(id: Int): Bitmap? {
        val ink = imageInk
        val bmp = ink?.let { store?.load(id, inkOf(id, it)) }
        if (ink == null || bmp == null) return null
        if (id in math) tint(bmp, ink)
        synchronized(this) {
            if (imageInk == ink) remember(id, bmp)
        }
        return bmp
    }

    /** Loads the images of [page]'s islands, so inline ones are in their spans before the page is drawn. */
    fun loadFor(page: Page) = book.islandIdsOn(page).forEach(::image)

    /**
     * The next chapter whose islands still need sizes: [current] first (it is being read), then the nearest ones
     * around it. One chapter at a time, so the WebView's page stays small and only that chapter is laid out again.
     */
    @Synchronized
    fun toMeasure(current: Int, onlyCurrent: Boolean = false): IslandWork? {
        val (bodyPx, width) = measuredFor ?: return null
        val order = if (onlyCurrent) listOf(current) else book.nearestChapters(current)
        return order.firstOrNull { c -> items(c).any { it.id !in sizes } }?.let { chapter ->
            IslandWork(chapter, book.chapters[chapter].path, items(chapter), bodyPx, width)
        }
    }

    /** Islands on [pages] (in view, or about to be) that have a size but no image in memory or on disk. */
    @Synchronized
    fun toRender(pages: List<Page>, ink: Int): IslandWork? {
        val (bodyPx, width) = measuredFor ?: return null
        useInk(ink)
        return pages.asSequence()
            .map { page ->
                page.chapter to book.islandIdsOn(page).filter { id ->
                    id in sizes && images.get(id) == null && store?.has(id, inkOf(id, ink)) != true
                }.toSet()
            }
            .firstOrNull { (_, missing) -> missing.isNotEmpty() }
            ?.let { (chapter, missing) ->
                IslandWork(chapter, book.chapters[chapter].path, items(chapter), bodyPx, width, missing, ink)
            }
    }

    /**
     * Islands not yet on disk, from the chapter nearest [current] that has some: drawn ahead while the reader is still,
     * a batch at a time, so later page turns never wait for the WebView (ADR-0021).
     */
    @Synchronized
    fun toCache(current: Int, ink: Int): IslandWork? {
        val disk = store
        val layout = measuredFor
        if (disk == null || layout == null) return null
        val (bodyPx, width) = layout
        // Islands in memory were just drawn for the pages in view and are being written already.
        fun due(id: Int) = id in sizes && images.get(id) == null && !disk.has(id, inkOf(id, ink))
        return book.nearestChapters(current).asSequence()
            .map { c -> c to items(c).map { it.id }.filter(::due) }
            .firstOrNull { (_, ids) -> ids.isNotEmpty() }
            ?.let { (chapter, ids) ->
                val batch = ids.take(CACHE_BATCH).toSet()
                IslandWork(chapter, book.chapters[chapter].path, items(chapter), bodyPx, width, batch, ink, true)
            }
    }

    /**
     * Sizes for [work]'s islands; any the WebView could not lay out keep a placeholder size, so they are not retried in
     * this visit (and are not stored, so the next visit tries again). Returns the sizes to store, or null.
     */
    @Synchronized
    fun addSizes(work: IslandWork, boxes: Map<Int, IslandBox>): List<IslandBox>? {
        if (measuredFor != work.bodyPx to work.widthPx) return null
        work.items.forEach { item ->
            sizes[item.id] = boxes[item.id]
                ?: IslandBox(item.id, work.widthPx / 2, (work.bodyPx * 2).toInt(), work.bodyPx.toInt(), 0, 0)
        }
        applySpans()
        return work.items.mapNotNull { boxes[it.id] }.takeIf { it.size == work.items.size }
    }

    /**
     * Freshly drawn images: shown at once when drawn for the pages in view (equations tinted to the ink), and returned
     * with the ink each is stored under, for the disk.
     */
    @Synchronized
    fun addImages(work: IslandWork, drawn: Map<Int, Bitmap>): List<DrawnIsland> {
        val ink = imageInk
        val show = ink != null && !work.background && work.ink == ink
        val out = drawn.map { (id, bmp) ->
            // Equations are shown as a tinted copy; the black original is only for the disk.
            val shown = if (!show) {
                null
            } else if (id in math) {
                bmp.copy(Bitmap.Config.ARGB_8888, true)
            } else {
                bmp
            }
            if (shown != null && ink != null) {
                if (shown !== bmp) tint(shown, ink)
                remember(id, shown)
            }
            DrawnIsland(id, inkOf(id, work.ink), bmp, inUse = shown === bmp)
        }
        if (show) revision++
        return out
    }

    /** Where images and sizes are kept, for writing what the WebView produced. */
    val disk: IslandStore? get() = store

    private fun remember(id: Int, bmp: Bitmap) {
        images.put(id, bmp)
        spans[id]?.image = bmp
    }

    private fun inkOf(id: Int, ink: Int): Int? = if (id in math) null else ink

    private fun items(chapter: Int): List<IslandItem> {
        val inline = spans.keys
        return book.islands(chapter).map { (id, xml) -> IslandItem(id, xml, id in inline) }
    }

    private companion object {
        const val IMAGE_BYTES = 32 * 1024 * 1024

        /** Islands drawn ahead per WebView page: small pages, and a page turn never waits long for the WebView. */
        const val CACHE_BATCH = 24

        /** Recolours a black-on-transparent equation to [ink], keeping its edges (alpha). */
        fun tint(bmp: Bitmap, ink: Int) = Canvas(bmp).drawColor(ink, PorterDuff.Mode.SRC_IN)
    }
}

/** Chapters ordered by distance from [current], [current] first. */
private fun ReflowBook.nearestChapters(current: Int) = chapters.indices.sortedBy { kotlin.math.abs(it - current) }

/** The islands shown on [page]: block ones, and inline ones in its lines. */
private fun ReflowBook.islandIdsOn(page: Page): List<Int> {
    val blocks = chapters[page.chapter].blocks
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
