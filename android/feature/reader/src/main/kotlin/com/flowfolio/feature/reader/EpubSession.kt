package com.flowfolio.feature.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.flowfolio.core.data.OpenedEpub
import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.core.model.PageMargins
import com.flowfolio.core.model.PixelSize
import com.flowfolio.core.model.ReaderText
import com.flowfolio.core.model.ReadingFont
import com.flowfolio.core.reflow.Block
import com.flowfolio.core.reflow.Locator
import com.flowfolio.core.reflow.PageColors
import com.flowfolio.core.reflow.PageDrawer
import com.flowfolio.core.reflow.PageSpec
import com.flowfolio.core.reflow.Paginator
import com.flowfolio.core.reflow.ReflowBook
import com.flowfolio.core.reflow.linkAreas
import com.flowfolio.feature.reader.cache.PageKey
import com.flowfolio.feature.reader.cache.PageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * An open EPUB in the reader (ADR-0019): the book laid out for the current page pane and text settings, and its pages
 * drawn as images for the page cache. Another pane (fold, unfold, spreads on or off) or other text settings (M3) lay
 * the book out again; positions are kept as [Locator]s so the reader stays on the same text.
 */
@Suppress("TooManyFunctions") // the open book's one place for layout, drawing, links and islands
internal class EpubSession private constructor(
    val opened: OpenedEpub,
    private val book: ReflowBook,
    private val heading: android.graphics.Typeface?,
    /** The reading fonts, loaded once each when first used (null: the system serif). */
    private val fonts: (ReadingFont) -> android.graphics.Typeface?,
    /** Where islands are kept on disk ([IslandStore]), all books. */
    private val islandRoot: java.io.File
) : PageSource {
    private val lock = Mutex()
    private var paginator: Paginator? = null
    private var drawer = PageDrawer(book, ::image) { null }

    /** Equations, SVG and complex tables, laid out and drawn by the island WebView. */
    val islands = EpubIslands(book)

    /** The text size and column width of the current layout (what islands are measured for). */
    var column: Pair<Float, Int>? = null
        private set

    /** The pane the book is laid out for (pixels), or null before the first layout. */
    var pane: PixelSize? = null
        private set

    /** The pane and text settings of the current layout; null before the first. */
    var laidOut: EpubLayout? = null
        private set

    private var margins = Margins(0, 0, 0, 0)

    val pageCount: Int get() = book.pages.size

    /** Page images decoded at their drawn size, under a small byte budget. */
    private val images = object : LruCache<String, Bitmap>(IMAGE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    /** Raised by every layout: page numbers may now show other text, so cached page images no longer match them. */
    @Volatile private var layouts = 0

    /** Changes whenever a page's image would: a new layout, or islands drawn. Part of every [PageKey]. */
    val revision: Int get() = layouts + islands.revision

    /** The island folder of the last layout, for [trimIslands] to keep. */
    @Volatile private var trimLater: java.io.File? = null

    /**
     * Keeps the island cache under its cap, sparing the current layout's folder. It walks every stored image (about
     * 0.8 s for a big textbook), so it runs in the background after a layout, never in its way.
     */
    suspend fun trimIslands() = withContext(Dispatchers.IO) {
        trimLater?.let { IslandStore.trim(islandRoot, keep = it) }
    }

    /** Lays the book out for [layout] ([density] pixels per dp); returns the page now showing [keep]. */
    suspend fun layOut(layout: EpubLayout, density: Float, keep: Locator?, chapterFirst: Boolean = false): Int =
        lock.withLock {
            withContext(Dispatchers.Default) {
                val size = layout.pane
                val text = layout.text
                val bodyPx = text.sizeSp * density
                margins = Margins.forPane(size, density, text.margins)
                val spec = PageSpec(
                    width = (size.width - margins.left - margins.right).coerceAtLeast(1),
                    height = (size.height - margins.top - margins.bottom).coerceAtLeast(1),
                    bodyPx = bodyPx,
                    locale = book.locale,
                    heading = heading,
                    body = fonts(text.font),
                    lineSpacing = text.lineSpacing.factor,
                    justify = text.justify,
                    hyphenate = text.hyphenate
                )
                val store = IslandStore(islandRoot, opened.book.id, bodyPx, spec.width)
                val t1 = android.os.SystemClock.elapsedRealtime()
                islands.prepare(bodyPx, spec.width, store)
                val t2 = android.os.SystemClock.elapsedRealtime()
                // Up to the reader's chapter now, the chapters after it in [finishLayout] (M3's 300 ms target).
                val first = if (chapterFirst) keep?.chapter ?: 0 else -1
                paginator = book.paginate(spec, density, first, islands::size)
                android.util.Log.i(
                    "FlowFolioLayout",
                    "islands ${t2 - t1} ms, paginate ${android.os.SystemClock.elapsedRealtime() - t2} ms"
                )
                trimLater = store.dir
                pane = size
                laidOut = layout
                column = bodyPx to spec.width
                drawer = PageDrawer(book, ::image, islands::image)
                layouts++
                keep?.let(book::pageOf) ?: 0
            }
        }

    /**
     * Lays out the chapters a chapter-first [layOut] only estimated, without holding up pages being drawn meanwhile.
     * Returns where each old page number went (null for estimated pages, whose images must go), or null when there was
     * nothing to do or another layout started meanwhile.
     */
    suspend fun finishLayout(): ((Int) -> Int?)? {
        val (p, generation, done) = lock.withLock { Triple(paginator, book.generation, book.complete) }
        if (p == null || done) return null
        val rest = withContext(Dispatchers.Default) { book.layOutRest(p) }
        return lock.withLock {
            if (book.generation != generation) return@withLock null
            val before = book.pages
            book.applyRest(rest)
            val now = java.util.IdentityHashMap<com.flowfolio.core.reflow.Page, Int>()
            book.pages.forEachIndexed { i, page -> now[page] = i }
            val moves: (Int) -> Int? = { old -> before.getOrNull(old)?.let { now[it] } }
            moves
        }
    }

    /** Lays [chapter] out again (its islands got their sizes); returns the page now showing [keep]. */
    suspend fun relayOut(chapter: Int, keep: Locator?): Int = lock.withLock {
        withContext(Dispatchers.Default) {
            val first = book.pageOf(Locator(chapter, 0))
            paginator?.let { book.repaginate(chapter, it) }
            val page = keep?.let(book::pageOf) ?: 0
            // Pages before the chapter keep their numbers and text; only a chapter near or before the reader's place
            // can change pages that are cached.
            if (first <= page + NEAR_PAGES) layouts++
            page
        }
    }

    fun locatorOf(page: Int): Locator = book.locatorOf(page)

    /** The chapter [page] is in. */
    fun chapterOf(page: Int): Int = book.pages.getOrNull(page)?.chapter ?: 0

    /** Islands to draw for [pages] (the ones in view and about to be). */
    fun islandsToRender(pages: List<Int>, tone: PageTone): IslandWork? =
        islands.toRender(pages.mapNotNull(book.pages::getOrNull), colorsOf(tone).ink)

    /** Islands to draw ahead for the disk cache, nearest the reader's [page] first. */
    fun islandsToCache(page: Int, tone: PageTone): IslandWork? = islands.toCache(chapterOf(page), colorsOf(tone).ink)

    fun pageOf(locator: Locator): Int = book.pageOf(locator)

    /** Contents with the page each entry starts on, and the printed page each screen page starts in. */
    fun navigation(): BookNavigation = BookNavigation(
        outline = book.contents().map { (depth, title, page) -> OutlineEntry(depth, page, title) },
        labels = book.printedPages()
    )

    /** Draws one page. Pages of another pane size (an old layout) are not drawn. */
    override suspend fun render(key: PageKey): ImageBitmap? = lock.withLock {
        val p = paginator
        val page = book.pages.getOrNull(key.page)
        if (p == null || page == null || key.size != pane) return@withLock null
        withContext(Dispatchers.Default) {
            val bmp = Bitmap.createBitmap(key.size.width, key.size.height, Bitmap.Config.ARGB_8888)
            val colors = colorsOf(key.tone)
            // The page's equations and tables, from memory or the disk cache, before it is drawn.
            islands.useInk(colors.ink)
            islands.loadFor(page)
            bmp.eraseColor(colors.paper)
            drawer.draw(PageDrawer.Target(Canvas(bmp), margins.left.toFloat(), margins.top.toFloat(), p), page, colors)
            // 16-bit GPU pages, as for PDFs (ADR-0013).
            val small = bmp.copy(Bitmap.Config.RGB_565, false) ?: bmp
            if (small !== bmp) bmp.recycle()
            val image = small.copy(Bitmap.Config.HARDWARE, false) ?: small
            if (image !== small) small.recycle()
            image.asImageBitmap()
        }
    }

    /**
     * [page] drawn small, [size] in pixels, for the riffle scrubber's loupe (ADR-0022): the full page scaled down, its
     * equations and images as far as they are ready. Null before the first layout.
     */
    suspend fun thumbnail(page: Int, size: PixelSize, tone: PageTone): ImageBitmap? = lock.withLock {
        val p = paginator
        val pane = pane
        val laidOut = book.pages.getOrNull(page)
        if (minOf(size.width, size.height) <= 0) return@withLock null
        if (p == null || pane == null || laidOut == null) return@withLock null
        withContext(Dispatchers.Default) {
            val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            val colors = colorsOf(tone)
            bmp.eraseColor(colors.paper)
            val canvas = Canvas(bmp)
            canvas.scale(size.width.toFloat() / pane.width, size.height.toFloat() / pane.height)
            drawer.draw(PageDrawer.Target(canvas, margins.left.toFloat(), margins.top.toFloat(), p), laidOut, colors)
            val small = bmp.copy(Bitmap.Config.RGB_565, false) ?: bmp
            if (small !== bmp) bmp.recycle()
            val image = small.copy(Bitmap.Config.HARDWARE, false) ?: small
            if (image !== small) small.recycle()
            image.asImageBitmap()
        }
    }

    /**
     * [page]'s links as the reader taps them: boxes on the page image (0 to 1), each leading to a web address, a
     * footnote's popover, or a page in the reading order; links to anything else are left out. Empty before layout.
     */
    suspend fun links(page: Int): List<ReaderLink> = lock.withLock {
        val p = paginator
        val pane = pane
        val laidOut = book.pages.getOrNull(page)
        if (p == null || pane == null || laidOut == null) return@withLock emptyList()
        book.linkAreas(laidOut, p).mapNotNull { area ->
            linkTarget(book, area.href)?.let { target ->
                ReaderLink(
                    (margins.left + area.left) / pane.width,
                    (margins.top + area.top) / pane.height,
                    (margins.left + area.right) / pane.width,
                    (margins.top + area.bottom) / pane.height,
                    target
                )
            }
        }
    }

    /** The text of the footnote or endnote [href] points at, or null. */
    fun footnote(href: String): CharSequence? = book.footnote(href)

    /** The shape of a page (width / height), for sizing thumbnails. */
    val pageAspect: Float get() = pane?.let { it.width.toFloat() / it.height } ?: DEFAULT_ASPECT

    /** A book image decoded close to its drawn size [w] x [h], from the cache when possible. */
    private fun image(block: Block.Image, w: Int, h: Int): Bitmap? {
        val key = "${block.path}@$w"
        return images.get(key) ?: book.archive.read(block.path)?.let { decode(it, w, h) }?.also { images.put(key, it) }
    }

    private fun decode(bytes: ByteArray, w: Int, h: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
            }
        )
    }

    fun close() {
        images.evictAll()
        opened.archive.close()
    }

    /**
     * White space around the text: wider at the outer edges, clear of the status bar at the top. Kept tight so the page
     * fills the screen; the reader's bars may cover a line while they show (owner, 2026-10-21).
     */
    private data class Margins(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        companion object {
            fun forPane(size: PixelSize, density: Float, sides: PageMargins): Margins {
                val side = maxOf((sides.minDp * density).toInt(), (size.width * sides.fraction).toInt())
                return Margins(side, (TOP_DP * density).toInt(), side, (BOTTOM_DP * density).toInt())
            }
        }
    }

    companion object {
        /** Parses the book's chapters off the main thread. */
        suspend fun open(
            opened: OpenedEpub,
            heading: android.graphics.Typeface?,
            fonts: (ReadingFont) -> android.graphics.Typeface?,
            islandRoot: java.io.File
        ): EpubSession = withContext(Dispatchers.Default) {
            EpubSession(opened, ReflowBook(opened.archive, opened.pkg), heading, fonts, islandRoot)
        }

        fun colorsOf(tone: PageTone): PageColors {
            val ink = (tone.ink ?: androidx.compose.ui.graphics.Color.Black).toArgb()
            return PageColors(paper = tone.paper.toArgb(), ink = ink, faint = (ink and RGB) or FAINT_ALPHA)
        }

        private const val TOP_DP = 56f
        private const val BOTTOM_DP = 64f
        private const val IMAGE_BYTES = 24 * 1024 * 1024
        private const val DEFAULT_ASPECT = 0.7f

        /** Pages past the reader's place that may be cached (the planned neighbours, with room to spare). */
        private const val NEAR_PAGES = 16
        private const val RGB = 0x00FFFFFF
        private const val FAINT_ALPHA = 0x22000000
    }
}

/** What an EPUB is laid out for: the page pane in pixels and the reader's text settings. */
internal data class EpubLayout(val pane: PixelSize, val text: ReaderText)
