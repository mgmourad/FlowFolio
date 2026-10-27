package com.flowfolio.core.reflow

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.core.epub.EpubArchive
import com.flowfolio.core.epub.EpubException
import com.flowfolio.core.epub.EpubParser
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Phase 2 robustness corpus (M5): every EPUB in /data/local/tmp/flowfolio, the IDPF samples and our generated
 * hostile books (`tools/testepubs/make_test_epubs.py`), goes through everything the reader does with a book, on real
 * Android: open and parse, lay out at the cover screen's and the inner screen's sizes, read every page's text and
 * character boxes, links, pictures and islands, the contents and printed pages, and draw pages. Each book must either
 * be refused cleanly ([EpubException]) or come through, within a time limit, without any other exception. DRM and a
 * book without a container must be refused.
 */
@RunWith(AndroidJUnit4::class)
class RobustnessOnDeviceTest {
    private val dir = File("/data/local/tmp/flowfolio")

    @Test
    fun everyCorpusBookIsReadOrRefusedCleanly() {
        val books = dir.listFiles { f -> f.name.endsWith(".epub") }.orEmpty().sortedBy { it.name }
        assumeTrue("no corpus pushed to $dir", books.isNotEmpty())
        val pool = Executors.newSingleThreadExecutor()
        val outcomes = books.associate { file ->
            val started = System.nanoTime()
            val outcome = try {
                pool.submit<String> { exercise(file) }.get(LIMIT_S, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                fail("${file.name} took over $LIMIT_S s: $e")
                ""
            } catch (e: java.util.concurrent.ExecutionException) {
                fail("${file.name} failed: ${e.cause}")
                ""
            }
            val ms = (System.nanoTime() - started) / NANOS_PER_MS
            Log.i(TAG, "FF_ROBUST ${file.name} $outcome ${ms}ms")
            file.name to outcome
        }
        pool.shutdownNow()
        outcomes["hostile-drm.epub"]?.let { assertEquals("refused DRM", it) }
        outcomes["hostile-no-container.epub"]?.let { assertTrue(it, it.startsWith("refused")) }
        // Every IDPF sample and our own books open.
        outcomes.filterKeys {
            !it.startsWith("hostile")
        }.forEach { (name, o) -> assertTrue("$name: $o", o.startsWith("read")) }
    }

    /** Everything the reader does with [file]; returns "refused <reason>" or "read <pages>". */
    private fun exercise(file: File): String {
        val archive = try {
            EpubArchive.open(file)
        } catch (e: EpubException) {
            return "refused ${e.reason}"
        }
        return archive.use {
            val pkg = try {
                EpubParser.parse(it)
            } catch (e: EpubException) {
                return@use "refused ${e.reason}"
            }
            val book = ReflowBook(it, pkg)
            book.contents()
            val pages = SIZES.map { (w, h) -> layOutAndRead(book, w, h) }
            "read ${pages.joinToString("/")}"
        }
    }

    /** Lays [book] out for a [width] x [height] page box and reads and draws its pages; returns the page count. */
    private fun layOutAndRead(book: ReflowBook, width: Int, height: Int): Int {
        val paginator = book.paginate(PageSpec(width, height, BODY_PX, Locale.ENGLISH), DENSITY)
        book.printedPages()
        val drawer = PageDrawer(book, { block, w, h -> picture(book, block, w, h) }) { null }
        val canvasBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val canvas = Canvas(canvasBitmap)
        val colors = PageColors(Color.WHITE, Color.BLACK, Color.LTGRAY)
        val last = book.pages.lastIndex
        book.pages.forEachIndexed { i, page ->
            val glyphs = book.pageGlyphs(page, paginator)
            for (k in 1 until glyphs.length) {
                check(glyphs.offsets[k] >= glyphs.offsets[k - 1]) { "page $i offsets go back at $k" }
            }
            book.linkAreas(page, paginator).forEach { link -> book.footnote(link.href) }
            book.imageAreas(page)
            book.islandAreas(page, paginator)
            if (i == 0 || i == last / 2 || i == last) {
                drawer.draw(PageDrawer.Target(canvas, 0f, 0f, paginator), page, colors)
            }
        }
        canvasBitmap.recycle()
        return book.pages.size
    }

    /** A book picture decoded small, as the reader does; null when it cannot be read (too big, damaged, missing). */
    private fun picture(book: ReflowBook, block: Block.Image, w: Int, h: Int): Bitmap? {
        val bytes = book.archive.read(block.path) ?: return null
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

    private companion object {
        const val TAG = "FlowFolioReflowTest"

        /** No book may take longer than this to read through at both sizes. */
        const val LIMIT_S = 120L
        const val NANOS_PER_MS = 1_000_000L
        const val BODY_PX = 51f
        const val DENSITY = 3f

        /** The cover screen's and the inner screen's page boxes on the reference Fold (pixels, margins removed). */
        val SIZES = listOf(900 to 2300, 940 to 2280)
    }
}
