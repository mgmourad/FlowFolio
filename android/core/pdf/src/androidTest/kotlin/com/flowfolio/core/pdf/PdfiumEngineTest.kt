package com.flowfolio.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.core.model.PageSize
import com.flowfolio.core.pdf.pdfium.PdfiumEngine
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfiumEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val engine = PdfiumEngine()

    private fun asset(name: String): File = File(context.cacheDir, name).also { file ->
        context.assets.open("pdfs/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
    }

    private suspend fun open(file: File, password: String? = null): PdfDocument =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { engine.open(it, password) }

    private suspend fun open(asset: String, password: String? = null) = open(asset(asset), password)

    private suspend fun PdfDocument.renderFull(index: Int, width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { render(index, it) }

    private fun Bitmap.isDark(x: Int, y: Int) = getPixel(x, y).let {
        Color.red(it) < 64 && Color.green(it) < 64 &&
            Color.blue(it) < 64
    }

    private fun Bitmap.isWhite(x: Int, y: Int) = getPixel(x, y) == Color.WHITE

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
            fail("expected ${T::class.simpleName}")
        } catch (t: Throwable) {
            if (t !is T) throw AssertionError("expected ${T::class.simpleName}, got $t", t)
        }
    }

    @Test
    fun pageLabelsAndOutline() = runBlocking {
        open("labels-outline.pdf").use { doc ->
            assertEquals(5, doc.pageCount)
            assertEquals(listOf("i", "ii", "1", "2", "3"), (0 until 5).map { doc.pageLabel(it) })
            assertEquals(listOf(OutlineEntry(0, 0, "Front matter"), OutlineEntry(0, 2, "Chapter 1")), doc.outline())
            assertEquals("FlowFolio Test Book", doc.title())
        }
    }

    @Test
    fun missingLabelsAndOutlineAreEmptyNotErrors() = runBlocking {
        open("rotated.pdf").use { doc ->
            assertNull(doc.pageLabel(0))
            assertEquals(emptyList<OutlineEntry>(), doc.outline())
            assertNull(doc.title())
        }
    }

    @Test
    fun rendersInkWhereThePageDrawsIt() = runBlocking {
        open("labels-outline.pdf").use { doc ->
            assertEquals(PageSize(612f, 792f), doc.pageSize(0))
            val bmp = doc.renderFull(0, 612, 792)
            // Black bar at x 36..156, y 36..60 from the bottom (PDF origin), i.e. 732..756 from the top.
            assertTrue(bmp.isDark(96, 744))
            assertTrue(bmp.isWhite(5, 5))
            assertTrue(bmp.isWhite(600, 780))
        }
    }

    @Test
    fun tileRenderMatchesCropOfFullRender() = runBlocking {
        open("labels-outline.pdf").use { doc ->
            val full = doc.renderFull(0, 1224, 1584)
            // At 2x the bar spans x 72..312, y 1464..1512. The tile covers x 40..340, y 1380..1580.
            val tile = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
            doc.render(0, tile, RenderRegion(1224, 1584, offsetX = 40, offsetY = 1380))
            var maxDiff = 0
            for (y in 0 until 200) {
                for (x in 0 until 300) {
                    val a = full.getPixel(40 + x, 1380 + y)
                    val b = tile.getPixel(x, y)
                    maxDiff =
                        maxOf(
                            maxDiff,
                            abs(Color.red(a) - Color.red(b)),
                            abs(Color.green(a) - Color.green(b)),
                            abs(Color.blue(a) - Color.blue(b))
                        )
                }
            }
            assertTrue("tile differs from crop by $maxDiff", maxDiff <= 2)
            assertTrue("tile should contain the bar", tile.isDark(100, 108))
        }
    }

    @Test
    fun rotatedPagesReportRotatedSizes() = runBlocking {
        open("rotated.pdf").use { doc ->
            val sizes = (0 until 4).map { doc.pageSize(it) }
            assertEquals(PageSize(612f, 792f), sizes[0])
            assertEquals(PageSize(792f, 612f), sizes[1])
            assertEquals(PageSize(612f, 792f), sizes[2])
            assertEquals(PageSize(792f, 612f), sizes[3])
            for (i in 0 until 4) {
                val px = sizes[i].fitInside(800, 800)
                doc.renderFull(i, px.width, px.height).recycle()
            }
        }
    }

    @Test
    fun mixedAndExtremePageSizesRenderAtFittedSize() = runBlocking {
        open("mixed-sizes.pdf").use { doc ->
            assertEquals(4, doc.pageCount)
            assertEquals(PageSize(14400f, 14400f), doc.pageSize(3))
            for (i in 0 until 4) {
                val px = doc.pageSize(i).fitInside(1080, 2520)
                val bmp = doc.renderFull(i, px.width, px.height)
                assertTrue(bmp.width <= 1080 && bmp.height <= 2520)
                bmp.recycle()
            }
        }
    }

    @Test
    fun zeroPageDocumentOpensEmptyOrFailsCleanly() = runBlocking {
        try {
            open("zero-pages.pdf").use { doc ->
                assertEquals(0, doc.pageCount)
                assertThrows<IndexOutOfBoundsException> { runBlocking { doc.pageSize(0) } }
            }
        } catch (_: PdfException) {
            // Also acceptable: the engine rejects a page-less document.
        }
    }

    @Test
    fun encryptedDocumentNeedsTheRightPassword() = runBlocking {
        assertThrows<PdfException.PasswordRequired> { runBlocking { open("encrypted-rc4.pdf") } }
        assertThrows<PdfException.PasswordRequired> { runBlocking { open("encrypted-rc4.pdf", "wrong") } }
        open("encrypted-rc4.pdf", "flowfolio").use { doc ->
            assertEquals(2, doc.pageCount)
            assertTrue("decrypted content should draw the bar", doc.renderFull(1, 612, 792).isDark(96, 744))
        }
    }

    @Test
    fun brokenCrossReferenceTableIsRepaired() = runBlocking {
        open("broken-xref.pdf").use { doc ->
            assertEquals(1, doc.pageCount)
            assertTrue(doc.renderFull(0, 612, 792).isDark(96, 744))
        }
    }

    @Test
    fun nonPdfAndEmptyFilesFailWithTypedErrors() = runBlocking {
        assertThrows<PdfException.InvalidFormat> { runBlocking { open("not-a-pdf.pdf") } }
        val empty = File(context.cacheDir, "empty.pdf").apply { writeBytes(ByteArray(0)) }
        assertThrows<PdfException.FileError> { runBlocking { open(empty) } }
    }

    @Test
    fun damagedVariantsNeverCrash() = runBlocking {
        val sources =
            listOf("labels-outline.pdf", "rotated.pdf", "mixed-sizes.pdf", "encrypted-rc4.pdf", "broken-xref.pdf")
        var opened = 0
        var rejected = 0
        for (name in sources) {
            val bytes = asset(name).readBytes()
            val variants = Damage.truncations(bytes) + Damage.bitFlips(bytes, variants = 30, seed = name.hashCode())
            for ((i, variant) in variants.withIndex()) {
                val file = File(context.cacheDir, "damaged-$i-$name").apply { writeBytes(variant) }
                if (Damage.exercise(engine, file, password = "flowfolio")) opened++ else rejected++
                file.delete()
            }
        }
        // Reaching here means no native crash. Both outcomes are fine; log the split for the phase report.
        println("FF_ROBUSTNESS small-files opened=$opened rejected=$rejected")
        assertTrue(opened + rejected > 0)
    }

    @Test
    fun concurrentRendersFromManyCoroutinesAreCorrect() = runBlocking {
        val a = open("labels-outline.pdf")
        val b = open("rotated.pdf")
        try {
            val results = (0 until 24).map { i ->
                async(Dispatchers.Default) {
                    val doc = if (i % 2 == 0) a else b
                    doc.renderFull(0, 612, 792).isDark(96, 744)
                }
            }.awaitAll()
            assertTrue(results.all { it })
        } finally {
            a.close()
            b.close()
        }
    }

    @Test
    fun useAfterCloseFailsCleanlyAndDoubleCloseIsSafe() = runBlocking {
        val doc = open("labels-outline.pdf")
        doc.close()
        doc.close()
        assertThrows<PdfException.Closed> { runBlocking { doc.pageSize(0) } }
        assertThrows<PdfException.Closed> { runBlocking { doc.renderFull(0, 10, 10) } }
    }

    @Test
    fun outOfRangePagesAndBadTargetsAreRejected() = runBlocking {
        open("labels-outline.pdf").use { doc ->
            assertThrows<IndexOutOfBoundsException> { runBlocking { doc.pageSize(5) } }
            assertThrows<IndexOutOfBoundsException> { runBlocking { doc.pageLabel(-1) } }
            val immutable = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, false)
            assertThrows<IllegalArgumentException> { runBlocking { doc.render(0, immutable) } }
            val rgb565 = Bitmap.createBitmap(10, 10, Bitmap.Config.RGB_565)
            assertThrows<IllegalArgumentException> { runBlocking { doc.render(0, rgb565) } }
        }
    }
}
