package com.flowfolio.core.conversion

import com.flowfolio.core.conversion.epub.SourceInfo
import com.flowfolio.core.conversion.ocr.OcrCache
import com.flowfolio.core.conversion.ocr.OcrImage
import com.flowfolio.core.conversion.ocr.OcrLine
import com.flowfolio.core.conversion.ocr.OcrPage
import com.flowfolio.core.conversion.ocr.OcrReader
import com.flowfolio.core.conversion.ocr.OcrResult
import com.flowfolio.core.conversion.ocr.PixelBox
import com.flowfolio.core.conversion.pdf.Box
import com.flowfolio.core.conversion.pdf.Crop
import com.flowfolio.core.conversion.pdf.EncodedImage
import com.flowfolio.core.conversion.pdf.FontInfo
import com.flowfolio.core.conversion.pdf.OutlineItem
import com.flowfolio.core.conversion.pdf.PdfSource
import com.flowfolio.core.conversion.pdf.RawPage
import org.junit.Assert.assertEquals
import org.junit.Test

/** A stopped OCR conversion resumes: pages already read come from the cache and are not read again (TM1). */
class OcrResumeTest {
    @Test
    fun cachedPagesAreNotReadAgain() {
        val cache = MemoryCache()
        val text = OcrPage(
            listOf(
                OcrLine("The text this page was read as, long enough to keep.", PixelBox(100, 100, 1500, 140), 0.95f)
            ),
            "test"
        )
        // First run: both scanned pages are read and cached.
        val reader = CountingReader(text)
        Converter(Converter.Settings(ocr = reader, ocrCache = cache)).analyse(Scans(), info())
        assertEquals(2, reader.reads)
        assertEquals(setOf(0, 1), cache.pages.keys)
        // Resumed: nothing is read again, and the pages still become text.
        val again = CountingReader(text)
        val doc = Converter(Converter.Settings(ocr = again, ocrCache = cache)).analyse(Scans(), info())
        assertEquals(0, again.reads)
        assertEquals(listOf("ocr:test@0.95", "ocr:test@0.95"), doc.pages.map { it.ocr })
    }

    private fun info() = SourceInfo("scan.pdf", "0".repeat(64), 2, "Scan")

    private class CountingReader(private val page: OcrPage) : OcrReader {
        var reads = 0

        override fun read(image: OcrImage): OcrPage {
            reads++
            return page
        }
    }

    private class MemoryCache : OcrCache {
        val pages = HashMap<Int, OcrResult>()

        override fun get(index: Int) = pages[index]

        override fun put(index: Int, result: OcrResult) {
            pages[index] = result
        }
    }

    /** Two pages that are pictures of text: no characters, one image over the whole page. */
    private class Scans : PdfSource {
        override val pageCount = 2

        override fun metadata(key: String): String? = null

        override fun outline(): List<OutlineItem> = emptyList()

        override fun pageLabel(index: Int): String? = null

        override fun page(index: Int) = RawPage(
            index, 612f, 792f, "", FloatArray(0), FloatArray(0), IntArray(0), IntArray(0), IntArray(0),
            BooleanArray(0), listOf(FontInfo("", 0)), listOf(Box(0f, 0f, 612f, 792f)), emptyList()
        )

        override fun renderImages(index: Int, crops: List<Crop>, scale: Float): List<EncodedImage> =
            crops.map { EncodedImage(ByteArray(1), "png") }

        override fun renderPixels(index: Int, scale: Float) = OcrImage(1836, 2376, IntArray(1), scale)

        override fun close() = Unit
    }
}
