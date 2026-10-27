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
@Suppress("TooManyFunctions") // the book's one place for island sizes, images and the work queue
internal class EpubIslands(private val book: ReflowBook) {
    private var measuredFor: Pair<Float, Int>? = null
    private var store: IslandStore? = null
    private val sizes = HashMap<Int, IslandBox>()

    /**
     * Another text size's stored equations (M3: islands per layout): scaled, their sizes lay the book out and their
     * images show until this size's are measured and drawn. Ids in [estimated] and [standIns] are still to do.
     */
    private var fallback: IslandStore? = null
    private val estimated = HashSet<Int>()
    private val standIns = HashSet<Int>()
    private val spans: Map<Int, IslandSpan> = book.chapters.flatMap { c ->
        c.blocks.filterIsInstance<Block.Text>().flatMap {
            it.text.getSpans(0, it.text.length, IslandSpan::class.java).asList()
        }
    }.associateBy { it.id }

    /** Equations, which are stored in black whatever the paper. */
    private val math: Set<Int> = book.chapters.indices.flatMap { book.mathIds(it) }.toSet()
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

    /** The book has islands at all (without unpacking their markup). */
    val any: Boolean = spans.isNotEmpty() || book.chapters.any { c -> c.blocks.any { it is Block.Island } }

    /**
     * A layout for [bodyPx] text in a column [widthPx] wide, with [store] holding what earlier visits measured and drew
     * for it. Sizes found there lay the book out correctly at once. Off the main thread (reads the disk).
     */
    fun prepare(bodyPx: Float, widthPx: Int, store: IslandStore?, fallback: IslandStore? = null) {
        val stored = store?.sizes().orEmpty()
        // Equations this size has not measured yet take another size's measures, scaled (they do not wrap).
        val scale = fallback?.let { bodyPx / it.bodyPx } ?: 1f
        val borrowed = fallback?.sizes().orEmpty()
            .filter { (id, _) -> id in math && id !in stored }
            .mapValues { (_, b) -> b.scaled(scale) }
        synchronized(this) {
            if (measuredFor != bodyPx to widthPx) {
                measuredFor = bodyPx to widthPx
                sizes.clear()
                estimated.clear()
                standIns.clear()
                images.evictAll()
            }
            this.store = store
            this.fallback = fallback?.takeIf { borrowed.isNotEmpty() }
            borrowed.forEach { (id, box) -> if (id !in sizes) estimated += id }
            sizes.putAll(borrowed.filterKeys { it in estimated })
            sizes.putAll(stored)
            estimated.removeAll(stored.keys)
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
        val own = ink?.let { store?.load(id, inkOf(id, it)) }
        val bmp = own ?: ink?.let { standIn(id) }
        if (ink == null || bmp == null) return null
        if (id in math) tint(bmp, ink)
        synchronized(this) {
            if (imageInk == ink) {
                remember(id, bmp)
                if (own == null) standIns += id
            }
        }
        return bmp
    }

    /** Another size's stored equation, scaled to its estimated measure here; drawn properly later. */
    private fun standIn(id: Int): Bitmap? {
        val other = fallback?.takeIf { id in math }
        val box = synchronized(this) { sizes[id] }
        return if (other == null || box == null) null else other.load(id, null)?.let { scaledTo(it, box.w, box.h) }
    }

    /** Loads the images of [page]'s islands, so inline ones are in their spans before the page is drawn. */
    fun loadFor(page: Page) = book.islandIdsOn(page).forEach(::image)

    /**
     * Reads the images of [pages]' islands from disk on all cores at once, ahead of drawing them: an equation-dense
     * page took about 180 ms to draw on the Fold reading them one by one under the drawing lock (ADR-0041).
     */
    fun preload(pages: List<Page>) {
        pages.flatMap { book.islandIdsOn(it) }.distinct().parallelStream().forEach { image(it) }
    }

    /**
     * The next chapter whose islands still need sizes: [current] first (it is being read), then the nearest ones
     * around it. One chapter at a time, so the WebView's page stays small and only that chapter is laid out again.
     */
    @Synchronized
    fun toMeasure(current: Int, onlyCurrent: Boolean = false): IslandWork? {
        val (bodyPx, width) = measuredFor ?: return null
        val order = if (onlyCurrent) listOf(current) else book.nearestChapters(current)
        return order.firstOrNull { c -> items(c).any { it.id !in sizes || it.id in estimated } }?.let { chapter ->
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
                    id in sizes && id !in estimated && (images.get(id) == null || id in standIns) &&
                        store?.has(id, inkOf(id, ink)) != true
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
        fun due(id: Int) = id in sizes && id !in estimated && (images.get(id) == null || id in standIns) &&
            !disk.has(id, inkOf(id, ink))
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
            estimated -= item.id
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
        standIns -= id
        images.put(id, bmp)
        spans[id]?.image = bmp
    }

    private fun inkOf(id: Int, ink: Int): Int? = if (id in math) null else ink

    /** The last chapter's islands, unpacked: the queue asks for one chapter many times in a row. */
    private var lastItems: Pair<Int, List<IslandItem>>? = null

    private fun items(chapter: Int): List<IslandItem> = lastItems?.takeIf { it.first == chapter }?.second
        ?: book.islands(chapter).map { (id, xml) -> IslandItem(id, xml, id in spans) }.also {
            lastItems = chapter to it
        }

    private companion object {
        /**
         * Islands in memory are only needed while their pages are drawn; others load again from disk (ADR-0021). More
         * than a full page of islands, so one page's inline equations are never evicted while it is drawn.
         */
        const val IMAGE_BYTES = 16 * 1024 * 1024

        /** Islands drawn ahead per WebView page: small pages, and a page turn never waits long for the WebView. */
        const val CACHE_BATCH = 24

        /** Recolours a black-on-transparent equation to [ink], keeping its edges (alpha). */
        fun tint(bmp: Bitmap, ink: Int) = Canvas(bmp).drawColor(ink, PorterDuff.Mode.SRC_IN)
    }
}

/** [src] at [w] x [h], mutable (equations are tinted in place); [src] is released when a copy is made. */
private fun scaledTo(src: Bitmap, w: Int, h: Int): Bitmap {
    if (w == src.width && h == src.height) return src
    val scaled = Bitmap.createScaledBitmap(src, w, h, true)
    if (scaled !== src) src.recycle()
    return if (scaled.isMutable) scaled else scaled.copy(Bitmap.Config.ARGB_8888, true).also { scaled.recycle() }
}

private fun IslandBox.scaled(s: Float) =
    IslandBox(id, (w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1), (baseline * s).toInt(), top, left)

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
