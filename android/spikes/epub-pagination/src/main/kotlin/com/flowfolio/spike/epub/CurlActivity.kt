package com.flowfolio.spike.epub

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.flowfolio.core.pagecurl.page.PageCurl
import com.flowfolio.core.pagecurl.page.rememberPageCurlState
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Page curl over EPUB pages, for frame timing (`dumpsys gfxinfo`): `--es engine native|web --es book <file>`.
 * Pages are made two ahead and one behind, as the reader's cache does; native pages are drawn off the main thread
 * and kept as 16-bit HARDWARE bitmaps like PDF pages, web pages are WebView snapshots (main thread only).
 */
class CurlActivity : ComponentActivity() {
    private val pages = mutableStateMapOf<Int, ImageBitmap>()
    private val count = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val dm = resources.displayMetrics
        val pageW = if (dm.widthPixels / dm.density >= BenchActivity.WIDE_DP) dm.widthPixels / 2 else dm.widthPixels
        val pageH = dm.heightPixels
        val margin = (BenchActivity.MARGIN_DP * dm.density).toInt()
        val bodyPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, BenchActivity.BODY_SP, dm)
        val engine = intent.getStringExtra("engine") ?: "native"
        val file = File(getExternalFilesDir("books"), intent.getStringExtra("book") ?: "moby-dick.epub")
        val book = EpubBook.open(file)
        val web = WebView(this)
        val compose = ComposeView(this)
        setContentView(FrameLayout(this).apply {
            addView(web, FrameLayout.LayoutParams(pageW, pageH))
            addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        })
        val source: PageSource = if (engine == "web") WebPages(WebEngine(web, book), book, margin) else
            NativePages(book, pageW, pageH, margin, bodyPx)
        compose.setContent {
            val curl = rememberPageCurlState()
            LaunchedEffect(Unit) {
                count.value = source.prepare()
                Log.i(BenchActivity.TAG, "CURL_READY pages=${count.value}")
            }
            LaunchedEffect(curl.current, count.value) {
                if (count.value == 0) return@LaunchedEffect
                val keep = (curl.current - 1)..(curl.current + 2)
                pages.keys.filter { it !in keep }.forEach { pages.remove(it) }
                for (i in listOf(curl.current, curl.current + 1, curl.current + 2, curl.current - 1)) {
                    if (i in 0 until count.value && i !in pages) source.render(i, pageW, pageH)?.let { pages[i] = it }
                }
            }
            val pageWdp = with(LocalDensity.current) { pageW.toDp() }
            Row(Modifier.fillMaxSize().background(Color.White)) {
                if (pageW < dm.widthPixels) Box(Modifier.width(pageWdp).fillMaxHeight())
                Box(Modifier.width(pageWdp).fillMaxHeight()) {
                    if (count.value > 0) {
                        PageCurl(count = count.value, state = curl, gradientShadow = true) { i ->
                            Box(Modifier.fillMaxSize().background(Color.White)) {
                                pages[i]?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.None) }
                            }
                        }
                    }
                }
            }
        }
    }

    interface PageSource {
        /** Paginates the first chapters; returns the page count. */
        suspend fun prepare(): Int

        suspend fun render(index: Int, w: Int, h: Int): ImageBitmap?
    }

    private class NativePages(val book: EpubBook, val w: Int, val h: Int, val margin: Int, val bodyPx: Float) : PageSource {
        private val paginator = NativePaginator(w - 2 * margin, h - 2 * margin, bodyPx, Locale.ENGLISH) { null }
        private val flat = ArrayList<Pair<Chapter, Page>>()

        override suspend fun prepare(): Int = withContext(Dispatchers.Default) {
            book.spine.take(CHAPTERS).forEach { path ->
                val c = XhtmlBlocks.parse(book.read(path) ?: return@forEach, path) { 0 }
                paginator.paginate(c).forEach { flat += c to it }
            }
            flat.size
        }

        override suspend fun render(index: Int, w: Int, h: Int): ImageBitmap? = withContext(Dispatchers.Default) {
            val (c, page) = flat[index]
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(AColor.WHITE)
            paginator.draw(Canvas(bmp), page, c, margin.toFloat(), margin.toFloat()) { null }
            val small = bmp.copy(Bitmap.Config.RGB_565, false)
            bmp.recycle()
            (small.copy(Bitmap.Config.HARDWARE, false) ?: small).asImageBitmap()
        }
    }

    private class WebPages(val engine: WebEngine, val book: EpubBook, val margin: Int) : PageSource {
        private val index = ArrayList<Pair<String, Int>>() // chapter path, page in chapter
        private var loaded: String? = null

        override suspend fun prepare(): Int {
            book.spine.take(CHAPTERS).forEach { path ->
                val n = engine.paginate(path, FONT, margin).pages
                repeat(n) { index += path to it }
            }
            return index.size
        }

        override suspend fun render(index: Int, w: Int, h: Int): ImageBitmap? {
            val (path, page) = this.index[index]
            if (loaded != path) {
                engine.paginate(path, FONT, margin)
                loaded = path
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            engine.snapshot(page, bmp)
            val small = bmp.copy(Bitmap.Config.RGB_565, false)
            bmp.recycle()
            return (small.copy(Bitmap.Config.HARDWARE, false) ?: small).asImageBitmap()
        }
    }

    private companion object {
        const val CHAPTERS = 8
        const val FONT = 100
    }
}
