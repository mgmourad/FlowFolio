package com.flowfolio.spike.pdf

import android.graphics.Bitmap
import android.os.Bundle
import android.util.LruCache
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.lifecycleScope
import com.flowfolio.spike.pdf.engine.FrameworkEngine
import com.flowfolio.spike.pdf.engine.PdfDoc
import com.flowfolio.spike.pdf.engine.PdfEngine
import com.flowfolio.spike.pdf.engine.PdfiumEngine
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.rememberPageCurlState
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Page-curl probe. `--es engine pdfium|framework --ez spread true|false --ei start 100`.
 * Spread mode shows two pages per curl unit (naive: the whole spread curls).
 */
@OptIn(ExperimentalPageCurlApi::class)
class CurlActivity : ComponentActivity() {
    private var doc by mutableStateOf<PdfDoc?>(null)

    // Byte-budgeted LRU: never holds more than this many bytes of page bitmaps.
    private val cache = object : LruCache<Int, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: Int, value: Bitmap) = value.allocationByteCount
    }
    private val renderLock = Mutex()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val engine: PdfEngine = if (intent.getStringExtra("engine") ==
            "framework"
        ) {
            FrameworkEngine()
        } else {
            PdfiumEngine()
        }
        val spread = intent.getBooleanExtra("spread", false)
        val start = intent.getIntExtra("start", 100)
        val book =
            File(getExternalFilesDir("books"), intent.getStringExtra("book") ?: "university-physics-volume-1.pdf")
        val bounds = windowManager.currentWindowMetrics.bounds
        val pageWidth = if (spread) bounds.width() / 2 else bounds.width()

        lifecycleScope.launch { doc = engine.open(book) }

        setContent {
            val d = doc ?: return@setContent
            val units = if (spread) (d.pageCount + 2) / 2 else d.pageCount
            val state = rememberPageCurlState(initialCurrent = if (spread) (start + 1) / 2 else start)
            var current by remember { mutableIntStateOf(state.current) }
            LaunchedEffect(state.current) {
                current = state.current
                // Prefetch the next two and previous one curl units in reading direction.
                for (u in listOf(current + 1, current + 2, current - 1)) {
                    pagesOf(u, spread, d.pageCount).forEach { load(d, it, pageWidth) }
                }
            }
            CurlBook(units, state, spread, d, pageWidth)
        }
    }

    @Composable
    private fun CurlBook(
        units: Int,
        state: eu.wewox.pagecurl.page.PageCurlState,
        spread: Boolean,
        d: PdfDoc,
        pageWidth: Int
    ) {
        PageCurl(count = units, state = state, modifier = Modifier.fillMaxSize()) { unit ->
            Row(Modifier.fillMaxSize().background(Color.White)) {
                for (p in pagesOf(unit, spread, d.pageCount, keepBlank = true)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { if (p >= 0) PageImage(d, p, pageWidth) }
                }
            }
        }
    }

    @Composable
    private fun PageImage(d: PdfDoc, page: Int, width: Int) {
        val bitmap by produceState(cache.get(page), page) { value = load(d, page, width) }
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    private suspend fun load(d: PdfDoc, page: Int, width: Int): Bitmap = renderLock.withLock {
        cache.get(page) ?: run {
            val size = d.pageSizePt(page)
            d.render(page, width, (width * size.height / size.width).roundToInt()).also { cache.put(page, it) }
        }
    }

    /** Spread unit 0 = cover alone on the right; unit n = pages 2n-1 and 2n (print convention). */
    private fun pagesOf(unit: Int, spread: Boolean, count: Int, keepBlank: Boolean = false): List<Int> {
        if (unit < 0) return emptyList()
        val pages = if (!spread) {
            listOf(unit)
        } else if (unit == 0) {
            listOf(-1, 0)
        } else {
            listOf(2 * unit - 1, 2 * unit)
        }
        return pages.map { if (it >= count) -1 else it }.filter { keepBlank || it >= 0 }
    }

    override fun onDestroy() {
        super.onDestroy()
        doc?.close()
    }

    private companion object {
        const val CACHE_BYTES = 128 * 1024 * 1024
    }
}
