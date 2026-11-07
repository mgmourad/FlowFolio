package com.flowfolio.feature.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.flowfolio.core.data.OpenedEpub
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.core.model.PageMargins
import com.flowfolio.core.model.PageRect
import com.flowfolio.core.model.PixelSize
import com.flowfolio.core.model.ReaderText
import com.flowfolio.core.model.ReadingFont
import com.flowfolio.core.model.chapterOfLocator
import com.flowfolio.core.reflow.Block
import com.flowfolio.core.reflow.CodeColors
import com.flowfolio.core.reflow.Locator
import com.flowfolio.core.reflow.PageColors
import com.flowfolio.core.reflow.PageDrawer
import com.flowfolio.core.reflow.PageSpec
import com.flowfolio.core.reflow.Paginator
import com.flowfolio.core.reflow.ReflowBook
import com.flowfolio.core.reflow.imageAreas
import com.flowfolio.core.reflow.islandAreas
import com.flowfolio.core.reflow.linkAreas
import com.flowfolio.core.reflow.pageGlyphs
import com.flowfolio.core.reflow.scrollExtent
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
    private val islandRoot: java.io.File,
    /** The book's own body font (ADR-0030), or null when it ships none: "Book's font" is then Literata. */
    val bookFont: android.graphics.Typeface?
) : PageSource {
    private val lock = Mutex()
    private var paginator: Paginator? = null
    private var drawer = PageDrawer(book, ::image) { null }

    /** Images set in lines of text (a converted book's inline equations), read for the pages drawn. */
    private val inlineImages = com.flowfolio.core.reflow.InlineImages(book)

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

    /** How Scroll shows each page of the current layout (continuous text); null before the first layout. */
    @Volatile var scrollBands: ScrollBands? = null
        private set

    /** Measures [scrollBands] for the pages as they now are. Call holding the lock, after any change of pages. */
    private fun measureBands() {
        val pane = pane ?: return
        val room = (pane.height - margins.top).coerceAtLeast(1)
        scrollBands = ScrollBands(margins.top, IntArray(book.pages.size) { book.scrollExtent(it).coerceAtMost(room) })
    }

    val pageCount: Int get() = book.pages.size

    /** Page images decoded at their drawn size, under a small byte budget. */
    private val images = object : LruCache<String, Bitmap>(IMAGE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    /** The 16-bit page every [render] draws into before its GPU copy; one pane-sized bitmap, reused. */
    private var scratchPage: Bitmap? = null

    private fun scratch(width: Int, height: Int): Bitmap = scratchPage
        ?.takeIf { it.width == width && it.height == height && !it.isRecycled }
        ?: Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).also {
            scratchPage?.recycle()
            scratchPage = it
        }

    /** Raised by every layout: page numbers may now show other text, so cached page images no longer match them. */
    @Volatile private var layouts = 0

    /** Changes whenever a page's image would: a new layout, or islands drawn. */
    val revision: Int get() = layouts + islands.revision

    /** The current layout, for [PageKey.layout]: pages keep their text until it changes. */
    val layout: Int get() = layouts

    /** Raised as islands are drawn, for [PageKey.revision]. */
    val islandRevision: Int get() = islands.revision

    /** The island folder of the last layout, for [trimIslands] to keep. */
    @Volatile private var trimLater: java.io.File? = null

    /**
     * Keeps the island cache under its cap, sparing the current layout's folder. It walks every stored image (about
     * 0.8 s for a big textbook), so it runs in the background after a layout, never in its way.
     */
    suspend fun trimIslands() = withContext(Dispatchers.IO) {
        trimLater?.let { IslandStore.trim(islandRoot, keep = it) }
    }

    @Volatile private var packed = false

    /** Packs the islands' markup once, after the first layout (M3 EPUB memory): not in the way of opening. */
    suspend fun packIslands() {
        if (packed) return
        packed = true
        withContext(Dispatchers.Default) { book.packIslands() }
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
                    body = if (text.font ==
                        ReadingFont.BOOK
                    ) {
                        bookFont ?: fonts(ReadingFont.LITERATA)
                    } else {
                        fonts(text.font)
                    },
                    lineSpacing = text.lineSpacing.factor,
                    justify = text.justify,
                    hyphenate = text.hyphenate,
                    code = CODE_COLORS
                )
                val store = IslandStore(islandRoot, opened.book.id, bodyPx, spec.width)
                val t1 = android.os.SystemClock.elapsedRealtime()
                // Equations already drawn at another text size stand in, scaled, until this size's are drawn.
                val nearest = IslandStore.nearest(islandRoot, opened.book.id, bodyPx, except = store.dir)
                islands.prepare(bodyPx, spec.width, store, nearest)
                val t2 = android.os.SystemClock.elapsedRealtime()
                // The reader's chapter now, every other chapter in [finishLayout] (ADR-0041: 300 ms to the page).
                val first = if (chapterFirst) keep?.chapter ?: 0 else -1
                paginator = book.paginate(spec, density, first, islands::size, layout.spreadShift)
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
                measureBands()
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
            measureBands()
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
            measureBands()
            val page = keep?.let(book::pageOf) ?: 0
            // Pages before the chapter keep their numbers and text; only a chapter near or before the reader's place
            // can change pages that are cached.
            if (first <= page + NEAR_PAGES) layouts++
            page
        }
    }

    fun locatorOf(page: Int): Locator = book.locatorOf(page)

    /** A converted PDF's place for PDF page [page] (ADR-0045); null for other EPUBs. */
    fun locatorOfSourcePage(page: Int): Locator? = book.locatorOfSourcePage(page)

    /** The PDF page (0-based) [locator] is in, for a converted PDF; null for other EPUBs. */
    fun sourcePageOf(locator: Locator): Int? = book.sourcePageOf(locator)

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

    /** Page renders waiting for the lock: background work ([background]) lets them go first. */
    private val rendersWaiting = java.util.concurrent.atomic.AtomicInteger()

    /**
     * [block] under the lock once no page render is waiting for it: thumbnails, links and placing highlights come after
     * the pages a reader is waiting for (M5: after a re-layout the reader's page waited 50 ms behind them).
     */
    private suspend fun <T> background(block: suspend () -> T): T {
        while (rendersWaiting.get() > 0) kotlinx.coroutines.delay(YIELD_MS)
        return lock.withLock { block() }
    }

    /** Draws one page. Pages of another pane size (an old layout) are not drawn. */
    override suspend fun render(key: PageKey): ImageBitmap? {
        rendersWaiting.incrementAndGet()
        return try {
            lock.withLock { renderLocked(key) }
        } finally {
            rendersWaiting.decrementAndGet()
        }
    }

    private suspend fun renderLocked(key: PageKey): ImageBitmap? = run {
        val p = paginator
        val page = book.pages.getOrNull(key.page)
        if (p == null || page == null || key.size != pane) return@run null
        withContext(Dispatchers.Default) {
            val colors = colorsOf(key.tone)
            // The page's equations and tables, from memory or the disk cache, before it is drawn.
            islands.useInk(colors.ink)
            islands.loadFor(page)
            inlineImages.loadFor(page)
            // Drawn straight into one reused 16-bit page (the paper is opaque), then uploaded to the GPU (ADR-0013).
            val bmp = scratch(key.size.width, key.size.height)
            bmp.eraseColor(colors.paper)
            drawer.draw(PageDrawer.Target(Canvas(bmp), margins.left.toFloat(), margins.top.toFloat(), p), page, colors)
            (bmp.copy(Bitmap.Config.HARDWARE, false) ?: bmp.copy(Bitmap.Config.RGB_565, false)).asImageBitmap()
        }
    }

    /**
     * [page] drawn small, [size] in pixels, for the riffle scrubber's loupe (ADR-0022): the full page scaled down, its
     * equations and images as far as they are ready. Null before the first layout.
     */
    suspend fun thumbnail(page: Int, size: PixelSize, tone: PageTone): ImageBitmap? = background {
        val p = paginator
        val pane = pane
        val laidOut = book.pages.getOrNull(page)
        if (minOf(size.width, size.height) <= 0) return@background null
        if (p == null || pane == null || laidOut == null) return@background null
        withContext(Dispatchers.Default) {
            val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            val colors = colorsOf(tone)
            bmp.eraseColor(colors.paper)
            val canvas = Canvas(bmp)
            canvas.scale(size.width.toFloat() / pane.width, size.height.toFloat() / pane.height)
            inlineImages.loadFor(laidOut)
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
    suspend fun links(page: Int): List<ReaderLink> = background {
        val p = paginator
        val pane = pane
        val laidOut = book.pages.getOrNull(page)
        if (p == null || pane == null || laidOut == null) return@background emptyList()
        val text = book.linkAreas(laidOut, p).mapNotNull { area ->
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
        text + shrunkIslands(laidOut, p, pane) + pictures(laidOut, pane)
    }

    /** [page]'s pictures big enough to be worth a closer look (not ornaments): tapped, they open zoomable (M4). */
    private fun pictures(page: com.flowfolio.core.reflow.Page, pane: PixelSize): List<ReaderLink> {
        val least = (column?.first ?: 0f) * PICTURE_MIN_EMS
        return book.imageAreas(page).filter { it.right - it.left >= least && it.bottom - it.top >= least }.map {
            ReaderLink(
                (margins.left + it.left) / pane.width,
                (margins.top + it.top) / pane.height,
                (margins.left + it.right) / pane.width,
                (margins.top + it.bottom) / pane.height,
                LinkTarget.Picture(it.path)
            )
        }
    }

    /**
     * The book picture [path] for the image viewer: decoded at up to twice [maxSide] pixels on its longer side (so
     * pinching it up stays sharp), or null when it cannot be read.
     */
    suspend fun picture(path: String, maxSide: Int): ImageBitmap? = lock.withLock {
        withContext(Dispatchers.IO) {
            val bytes = book.archive.read(path) ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@withContext null
            (bmp.copy(Bitmap.Config.HARDWARE, false)?.also { bmp.recycle() } ?: bmp).asImageBitmap()
        }
    }

    /** Page texts read for selecting and placing highlights, by laid-out page (a new layout makes new pages). */
    private val glyphs = object : LinkedHashMap<com.flowfolio.core.reflow.Page, EpubPageText>(
        GLYPH_PAGES,
        LOAD_FACTOR,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<com.flowfolio.core.reflow.Page, EpubPageText>?) =
            size > GLYPH_PAGES
    }

    /** [page]'s text and where each character is (fractions of the page image), for selecting (M4). */
    suspend fun pageText(page: Int): EpubPageText? = lock.withLock { withContext(Dispatchers.Default) { textOf(page) } }

    /** [page]'s text; call holding the lock. */
    private fun textOf(page: Int): EpubPageText? {
        val p = paginator
        val pane = pane
        val laidOut = book.pages.getOrNull(page)?.takeIf { it.slices.isNotEmpty() }
        if (p == null || pane == null) return null
        return laidOut?.let {
            glyphs.getOrPut(it) { EpubPageText.of(book.pageGlyphs(it, p), margins.left, margins.top, pane) }
        }
    }

    /**
     * Where [all] the book's highlights are in the current layout (ADR-0040): each one's first page, and its rectangles
     * on the pages in [near] (the ones shown or about to be); a highlight running onto later pages near the reader
     * gets a copy for each of them in [Placement.extra].
     */
    suspend fun place(all: List<Annotation>, near: Set<Int>): Placement = background {
        withContext(Dispatchers.Default) {
            val primary = ArrayList<Annotation>(all.size)
            val extra = ArrayList<Annotation>()
            for (a in all) {
                val chapter = chapterOfLocator(a.locator)
                if (chapter == null || book.pages.isEmpty()) {
                    primary += a
                    continue
                }
                val first = book.pageOf(Locator(chapter, a.start))
                val last = maxOf(first, book.pageOf(Locator(chapter, a.end)))
                for (page in first..last) {
                    val rects = if (page in near) rectsOn(page, a.start, a.end) else emptyList()
                    if (page == first) {
                        primary += a.copy(page = page, rects = rects)
                    } else if (rects.isNotEmpty()) {
                        extra += a.copy(page = page, rects = rects)
                    }
                }
            }
            Placement(primary, extra, exact = book.complete)
        }
    }

    /** The rectangles chapter offsets [start]..[end] cover on [page]; call holding the lock. */
    private fun rectsOn(page: Int, start: Int, end: Int): List<PageRect> {
        val text = textOf(page)
        val range = text?.glyphs?.indicesOf(start, end)
        return if (text == null || range == null) emptyList() else TextSelection.rects(text.text, range)
    }

    /** Where [marks] are in the current layout: on the page now showing each EPUB ribbon's text (ADR-0040). */
    suspend fun placeRibbons(marks: List<Bookmark>): List<Bookmark> = background {
        if (book.pages.isEmpty()) return@background marks
        marks.map { m -> Locator.decode(m.locator)?.let { m.copy(page = book.pageOf(it)) } ?: m }
    }

    /** Reads the islands of [pages] (the ones in view after a layout) ahead of drawing them, off the drawing lock. */
    suspend fun preloadIslands(pages: List<Int>) = withContext(Dispatchers.IO) {
        islands.preload(pages.mapNotNull { book.pages.getOrNull(it) })
    }

    /** Every chapter is laid out exactly: page numbers are final for this layout. */
    val exact: Boolean get() = book.complete

    /** [page]'s equations and tables drawn noticeably smaller than their size, to fit the column: tapped, they zoom. */
    private fun shrunkIslands(page: com.flowfolio.core.reflow.Page, p: Paginator, pane: PixelSize): List<ReaderLink> =
        book.islandAreas(page, p).mapNotNull { area ->
            val width = islands.size(area.id)?.first
            if (width == null || width < (area.right - area.left) * ZOOM_WORTH) return@mapNotNull null
            ReaderLink(
                (margins.left + area.left) / pane.width,
                (margins.top + area.top) / pane.height,
                (margins.left + area.right) / pane.width,
                (margins.top + area.bottom) / pane.height,
                LinkTarget.Island(area.id)
            )
        }

    /** Island [id]'s image at its own size in [tone]'s ink, for the zoomed view; null when it isn't drawn yet. */
    suspend fun islandImage(id: Int, tone: PageTone): ImageBitmap? = lock.withLock {
        withContext(Dispatchers.Default) {
            islands.useInk(colorsOf(tone).ink)
            islands.image(id)?.let { it.copy(Bitmap.Config.HARDWARE, false) ?: it }?.asImageBitmap()
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
        inlineImages.clear()
        scratchPage = null // a render may still hold the lock; the collector frees it
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
            val started = android.os.SystemClock.elapsedRealtime()
            val book = ReflowBook(opened.archive, opened.pkg)
            android.util.Log.i("FlowFolioLayout", "parsed in ${android.os.SystemClock.elapsedRealtime() - started} ms")
            EpubSession(opened, book, heading, fonts, islandRoot, runCatching { BookTypeface.load(opened) }.getOrNull())
        }

        fun colorsOf(tone: PageTone): PageColors {
            val ink = (tone.ink ?: androidx.compose.ui.graphics.Color.Black).toArgb()
            return PageColors(paper = tone.paper.toArgb(), ink = ink, faint = (ink and RGB) or FAINT_ALPHA)
        }

        /** Syntax-highlight colours (Phase 4 M4): editor-standard, readable on the dark papers the reader favours. */
        private val CODE_COLORS = CodeColors(
            keyword = 0xFFC586C0.toInt(),
            string = 0xFFCE9178.toInt(),
            comment = 0xFF6A9955.toInt(),
            number = 0xFFB5CEA8.toInt()
        )

        private const val TOP_DP = 56f
        private const val BOTTOM_DP = 64f
        private const val IMAGE_BYTES = 24 * 1024 * 1024
        private const val DEFAULT_ASPECT = 0.7f

        /** Pages past the reader's place that may be cached (the planned neighbours, with room to spare). */
        private const val NEAR_PAGES = 16

        /** An island drawn at under 1 / this of its size is worth zooming. */
        private const val ZOOM_WORTH = 1.15f

        /** Pictures smaller than this many ems on either side are ornaments, not worth opening. */
        private const val PICTURE_MIN_EMS = 3f
        private const val GLYPH_PAGES = 8

        /** How often background work looks again whether page renders still wait. */
        private const val YIELD_MS = 8L
        private const val LOAD_FACTOR = 0.75f
        private const val RGB = 0x00FFFFFF
        private const val FAINT_ALPHA = 0x22000000
    }
}

/**
 * What an EPUB is laid out for: the page pane in pixels, the reader's text settings and, in a spread, its pairing
 * ([spreadShift] as `Spreads` counts it; null in single-page layout), which puts chapters on their own spreads
 * (ADR-0041).
 */
internal data class EpubLayout(val pane: PixelSize, val text: ReaderText, val spreadShift: Int? = null)

/**
 * Scroll's view of an EPUB's pages: page `i` is shown from [top] pixels down its image (where its text box starts)
 * for `heights[i]` pixels, so pages stacked in a column read as one continuous text without page margins between them.
 */
class ScrollBands(val top: Int, val heights: IntArray)
