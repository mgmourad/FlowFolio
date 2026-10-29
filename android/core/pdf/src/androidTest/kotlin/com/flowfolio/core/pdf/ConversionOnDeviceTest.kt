package com.flowfolio.core.pdf

import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flowfolio.core.conversion.ConversionException
import com.flowfolio.core.conversion.Converter
import com.flowfolio.core.conversion.epub.SourceInfo
import com.flowfolio.core.pdf.pdfium.PdfiumConversionSource
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The converter on the device (Phase 3, M1 and M2): the same pipeline as the JVM corpus tests, reading through our JNI
 * bridge and writing WebP images. The textbook test needs the book pushed first (see [TextbookOnDeviceTest]); its EPUB
 * is left in the test app's cache for the reader check:
 *   adb shell run-as com.flowfolio.core.pdf.test cat cache/university-physics-volume-1.epub > ...
 */
@RunWith(AndroidJUnit4::class)
class ConversionOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val book = File("/data/local/tmp/flowfolio/university-physics-volume-1.pdf")

    @Test
    fun robustnessPdfsConvertOrAreRefused() {
        val names = context.assets.list("pdfs").orEmpty()
        assertTrue(names.isNotEmpty())
        for (name in names) {
            val pdf = File(context.cacheDir, name).also { f ->
                context.assets.open("pdfs/$name").use { input -> f.outputStream().use { input.copyTo(it) } }
            }
            val epub = File(context.cacheDir, "$name.epub")
            val result = runCatching { convert(pdf, epub) }
            val e = result.exceptionOrNull()
            // Only what PDFium can't open, or what the converter refuses with a reason, may fail; else it's a bug.
            assertTrue("$name: $e", e == null || e is PdfException || e is ConversionException)
            if (e ==
                null
            ) {
                assertTrue("$name: no package", ZipFile(epub).use { it.getEntry("OEBPS/content.opf") != null })
            }
            Log.i(TAG, "FF_CONVERT_ROBUST $name ${e?.javaClass?.simpleName ?: "ok"}")
        }
    }

    @Test
    fun textbookConvertsOnTheDevice() {
        assumeTrue("textbook not on device", book.canRead())
        val epub = File(context.cacheDir, "university-physics-volume-1.epub")
        val stages = LinkedHashMap<String, Long>()
        var stage = ""
        var stageStart = SystemClock.elapsedRealtime()
        var peakJava = 0L
        var peakNative = 0L
        val started = stageStart
        val result = convert(book, epub) { p ->
            if (p.stage != stage) {
                val now = SystemClock.elapsedRealtime()
                if (stage.isNotEmpty()) stages[stage] = now - stageStart
                stage = p.stage
                stageStart = now
            }
            if (p.done % SAMPLE_EVERY == 0) {
                val rt = Runtime.getRuntime()
                peakJava = maxOf(peakJava, rt.totalMemory() - rt.freeMemory())
                peakNative = maxOf(peakNative, Debug.getNativeHeapAllocatedSize())
            }
        }
        val totalMs = SystemClock.elapsedRealtime() - started
        stages[stage] = SystemClock.elapsedRealtime() - stageStart
        val s = result.sidecar.health.summary
        Log.i(
            TAG,
            "FF_CONVERT pages=${s.pages} chapters=${s.chapters} fallback=${s.fallbackPages} totalMs=$totalMs " +
                "stages=$stages peakJavaMB=${peakJava / MB} peakNativeMB=${peakNative / MB} " +
                "epubMB=${epub.length() / MB}"
        )
        assertEquals(959, s.pages)
        assertEquals(29, s.chapters)
        assertTrue(ZipFile(epub).use { z -> z.entries().asSequence().any { it.name.endsWith(".webp") } })
    }

    private fun convert(pdf: File, epub: File, progress: (Converter.Progress) -> Unit = {}): Converter.Result =
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfiumConversionSource.open(fd).use { src ->
                epub.outputStream().buffered().use { out ->
                    Converter().convert(
                        src,
                        SourceInfo(pdf.name, "device-test", src.pageCount, src.metadata("Title")),
                        out,
                        "2026-10-27T00:00:00Z",
                        progress
                    )
                }
            }
        }

    private companion object {
        const val TAG = "FlowFolioConvert"
        const val SAMPLE_EVERY = 20
        const val MB = 1_048_576L
    }
}
