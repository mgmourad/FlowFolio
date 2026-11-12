package com.flowfolio.core.conversion.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

/** [TesseractPage] turns Tesseract's raw lines into a page: empty lines dropped, logical order kept. */
class TesseractPageTest {
    @Test
    fun arabicLinesKeepTesseractsLogicalOrder() {
        // Tesseract already returns Arabic in logical order; it must not be reordered.
        val logical = "رئاسة الجمهورية العربية السورية"
        val page = TesseractPage.build(
            listOf(TesseractLine(logical, PixelBox(0, 0, 10, 10), 0.9f)),
            "tesseract-ara"
        )
        assertEquals(listOf(logical), page.lines.map { it.text })
        assertEquals("tesseract-ara", page.engine)
        assertEquals(0.9f, page.confidence, 1e-4f)
    }

    @Test
    fun latinLinesAreKept() {
        val page = TesseractPage.build(
            listOf(TesseractLine("plain text", PixelBox(0, 0, 10, 10), 0.8f)),
            "tesseract-eng"
        )
        assertEquals(listOf("plain text"), page.lines.map { it.text })
    }

    @Test
    fun emptyAndBlankLinesAreDropped() {
        val page = TesseractPage.build(
            listOf(
                TesseractLine("word", PixelBox(0, 0, 10, 10), 0.9f),
                TesseractLine("", PixelBox(0, 20, 10, 30), 0.9f),
                TesseractLine("   ", PixelBox(0, 40, 10, 50), 0.9f)
            ),
            "tesseract-eng"
        )
        assertEquals(listOf("word"), page.lines.map { it.text })
    }

    @Test
    fun confidenceIsTheMeanOfTheLines() {
        val page = TesseractPage.build(
            listOf(
                TesseractLine("a", PixelBox(0, 0, 10, 10), 0.6f),
                TesseractLine("b", PixelBox(0, 20, 10, 30), 0.9f)
            ),
            "t"
        )
        assertEquals(0.75f, page.confidence, 1e-4f)
    }
}
