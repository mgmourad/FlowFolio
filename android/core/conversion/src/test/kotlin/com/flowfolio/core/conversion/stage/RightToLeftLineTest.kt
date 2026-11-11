package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.pdf.FontInfo
import com.flowfolio.core.conversion.pdf.RawPage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Typed Arabic text (the owner's Arabic book, 2026-11-11). PDFium gives it in reading order, so each word lies to
 * the left of the one before, and that book gives its spaces a flat box on the baseline. Either used to cut the
 * line at every word, and the words came out reversed.
 */
class RightToLeftLineTest {
    /** [words] in reading order, set right to left from x = 400, with flat spaces between them, as that book does. */
    private fun arabicLine(vararg words: String, top: Float = 100f): Triple<String, FloatArray, Int> {
        val text = words.joinToString(" ")
        val boxes = FloatArray(text.length * 4)
        var pen = 400f
        text.forEachIndexed { i, c ->
            val o = i * 4
            if (c == ' ') {
                pen -= 4f
                // A flat box on the baseline: overlaps no letter's band.
                boxes[o] = pen
                boxes[o + 1] = top + 14f
                boxes[o + 2] = pen
                boxes[o + 3] = top + 14f
            } else {
                boxes[o] = pen - 6f
                boxes[o + 1] = top
                boxes[o + 2] = pen
                boxes[o + 3] = top + 17f
                pen -= 6f
            }
        }
        return Triple(text, boxes, text.length)
    }

    private fun page(vararg lines: Triple<String, FloatArray, Int>): List<String> {
        val text = lines.joinToString("") { it.first }
        val boxes = lines.map { it.second }.reduce { a, b -> a + b }
        val n = text.length
        val raw = RawPage(
            index = 0,
            width = 500f,
            height = 700f,
            text = text,
            boxes = boxes,
            fontSizes = FloatArray(n) { 1f },
            fontWeights = IntArray(n) { 400 },
            fontIds = IntArray(n) { 0 },
            colors = IntArray(n) { 0xFF000000.toInt() },
            generated = BooleanArray(n),
            fonts = listOf(FontInfo("TimesNewRoman", 0)),
            images = emptyList(),
            paths = emptyList()
        )
        return Extraction.page(raw).lines.map { it.text }
    }

    @Test
    fun `an Arabic line stays one line in reading order`() {
        assertEquals(listOf("نعتذر عن أدراج كل"), page(arabicLine("نعتذر", "عن", "أدراج", "كل")))
    }

    @Test
    fun `the next Arabic line still starts a new line`() {
        val lines = page(arabicLine("نعتذر", "عن"), arabicLine("الكتب", "السابقة", top = 120f))
        assertEquals(listOf("نعتذر عن", "الكتب السابقة"), lines)
    }
}
