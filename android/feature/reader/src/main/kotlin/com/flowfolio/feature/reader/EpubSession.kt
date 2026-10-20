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
import com.flowfolio.core.model.PixelSize
import com.flowfolio.core.reflow.Block
import com.flowfolio.core.reflow.Locator
import com.flowfolio.core.reflow.PageColors
import com.flowfolio.core.reflow.PageDrawer
import com.flowfolio.core.reflow.PageSpec
import com.flowfolio.core.reflow.Paginator
import com.flowfolio.core.reflow.ReflowBook
import com.flowfolio.feature.reader.cache.PageKey
import com.flowfolio.feature.reader.cache.PageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * An open EPUB in the reader (ADR-0019): the book laid out for the current page pane, and its pages drawn as images
 * for the page cache. A pane of another size (fold, unfold, spreads on or off) lays the book out again; positions are
 * kept as [Locator]s so the reader stays on the same text.
 */
internal class EpubSession private constructor(
    val opened: OpenedEpub,
    private val book: ReflowBook,
    private val heading: android.graphics.Typeface?
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

    private var margins = Margins(0, 0, 0, 0)

    val pageCount: Int get() = book.pages.size

    /** Page images decoded at their drawn size, under a small byte budget. */
    private val images = object : LruCache<String, Bitmap>(IMAGE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    /** Lays the book out for a pane of [size] with text at [bodyPx]; returns the page now showing [keep]. */
    suspend fun layOut(size: PixelSize, bodyPx: Float, density: Float, keep: Locator?): Int = lock.withLock {
        withContext(Dispatchers.Default) {
            margins = Margins.forPane(size, density)
            val spec = PageSpec(
                width = (size.width - margins.left - margins.right).coerceAtLeast(1),
                height = (size.height - margins.top - margins.bottom).coerceAtLeast(1),
                bodyPx = bodyPx,
                locale = book.locale,
                heading = heading
            )
            islands.prepare(bodyPx, spec.width)
            paginator = book.paginate(spec, density, islands::size)
            pane = size
            column = bodyPx to spec.width
            drawer = PageDrawer(book, ::image, islands::image)
            keep?.let(book::pageOf) ?: 0
        }
    }

    /** Lays [chapter] out again (its islands got their sizes); returns the page now showing [keep]. */
    suspend fun relayOut(chapter: Int, keep: Locator?): Int = lock.withLock {
        withContext(Dispatchers.Default) {
            paginator?.let { book.repaginate(chapter, it) }
            keep?.let(book::pageOf) ?: 0
        }
    }

    fun locatorOf(page: Int): Locator = book.locatorOf(page)

    /** The chapter [page] is in. */
    fun chapterOf(page: Int): Int = book.pages.getOrNull(page)?.chapter ?: 0

    /** Islands to draw for [pages] (the ones in view and about to be). */
    fun islandsToRender(pages: List<Int>, tone: PageTone): IslandWork? =
        islands.toRender(pages.mapNotNull(book.pages::getOrNull), colorsOf(tone).ink)

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

    /** White space around the text: wider at the outer edges, clear of the status bar at the top. */
    private data class Margins(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        companion object {
            fun forPane(size: PixelSize, density: Float): Margins {
                val side = maxOf((SIDE_DP * density).toInt(), (size.width * SIDE_FRACTION).toInt())
                return Margins(side, (TOP_DP * density).toInt(), side, (BOTTOM_DP * density).toInt())
            }
        }
    }

    companion object {
        /** Parses the book's chapters off the main thread. */
        suspend fun open(opened: OpenedEpub, heading: android.graphics.Typeface?): EpubSession =
            withContext(Dispatchers.Default) { EpubSession(opened, ReflowBook(opened.archive, opened.pkg), heading) }

        fun colorsOf(tone: PageTone): PageColors {
            val ink = (tone.ink ?: androidx.compose.ui.graphics.Color.Black).toArgb()
            return PageColors(paper = tone.paper.toArgb(), ink = ink, faint = (ink and RGB) or FAINT_ALPHA)
        }

        /** Body text: 18 sp, before the text settings of milestone 3. */
        const val BODY_SP = 18f
        private const val SIDE_DP = 28f
        private const val SIDE_FRACTION = 0.06f
        private const val TOP_DP = 56f
        private const val BOTTOM_DP = 64f
        private const val IMAGE_BYTES = 24 * 1024 * 1024
        private const val RGB = 0x00FFFFFF
        private const val FAINT_ALPHA = 0x22000000
    }
}
