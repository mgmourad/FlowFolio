package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.pdf.FontInfo
import com.flowfolio.core.conversion.pdf.RawPage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where a word space goes when the PDF wrote none (owner, 2026-11-03).
 *
 * A quarter em of empty space used to mean a word space on its own, which reads two common layouts as nothing but
 * spaces. Letter-spaced display text stands every glyph a quarter em from the next; monospaced code does the same
 * by accident, because a glyph's box is its ink while the font advances a whole, wider cell, so `.` and `i` leave
 * most of theirs empty. A Packt DevOps PDF came out of the converter as one word per character -- the copyright
 * page's URL as "w w w . p a c k t . c o m", and every code listing as a column of single letters.
 */
class WordSpacingTest {
    /**
     * Builds a one-line page. [chars] is the text; [advance] is how far the pen moves per character and [ink] how
     * much of that the glyph's box actually covers, so `advance - ink` is the gap PDFium reports between them.
     */
    // A test helper: each parameter is one property of the line being built.
    @Suppress("LongParameterList")
    private fun line(
        chars: String,
        size: Float,
        advance: Float,
        ink: Float,
        spaceAt: Set<Int> = emptySet(),
        boxHeight: Float = size
    ): String {
        val n = chars.length
        val boxes = FloatArray(n * 4)
        var pen = 0f
        for (i in 0 until n) {
            if (i in spaceAt) pen += advance
            boxes[i * 4] = pen
            boxes[i * 4 + 1] = 0f
            boxes[i * 4 + 2] = pen + ink
            boxes[i * 4 + 3] = boxHeight
            pen += advance
        }
        val raw = RawPage(
            index = 0,
            width = 500f,
            height = 700f,
            text = chars,
            boxes = boxes,
            fontSizes = FloatArray(n) { size },
            fontWeights = IntArray(n) { 400 },
            fontIds = IntArray(n) { 0 },
            colors = IntArray(n) { 0xFF000000.toInt() },
            generated = BooleanArray(n),
            fonts = listOf(FontInfo("Serif", 0)),
            images = emptyList(),
            paths = emptyList()
        )
        return Extraction.page(raw).lines.joinToString("\n") { it.text }
    }

    @Test
    fun `letter-spaced text is one word, not one word per letter`() {
        // Tracking of 5pt on a 10pt font: every gap clears a quarter em, but they all do, so none is a space.
        assertEquals("www.packt.com", line("www.packt.com", size = 10f, advance = 11f, ink = 6f))
    }

    @Test
    fun `monospaced code keeps its words`() {
        // A monospace cell the glyph only half fills -- what PDFium reports for `.` and `i` in a listing.
        assertEquals("TERRAFORM_VERSION", line("TERRAFORM_VERSION", size = 9f, advance = 6f, ink = 3f))
    }

    @Test
    fun `a real space still separates words in letter-spaced text`() {
        // The space character advances the pen a whole cell more, which is what makes it a space and not tracking.
        assertEquals("ab cd", line("abcd", size = 10f, advance = 11f, ink = 6f, spaceAt = setOf(2)))
    }

    @Test
    fun `ordinary text is unchanged`() {
        // Gaps within a word are nil here, so the plain quarter-em rule applies exactly as before.
        assertEquals("Hello world", line("Helloworld", size = 10f, advance = 6f, ink = 6f, spaceAt = setOf(5)))
    }

    @Test
    fun `a line of short words keeps every space`() {
        // Half of this line's gaps are word gaps, so a median would call a word gap typical and merge the lot.
        // The low quartile still lands among the within-word gaps. This is why it is a quartile.
        assertEquals(
            "I am a cat",
            line("Iamacat", size = 10f, advance = 6f, ink = 6f, spaceAt = setOf(1, 3, 4))
        )
    }

    @Test
    fun `a font reported at size 1 still keeps its kerning inside words`() {
        // Learning DevOps sets every font at size 1 and scales it through the text matrix (known issues 46 and 51):
        // the glyphs are 15 points tall, and 0.3 pt of kerning between "r" and "y" was read as a word space.
        assertEquals(
            "necessary to",
            line("necessaryto", size = 1f, advance = 6.3f, ink = 6f, spaceAt = setOf(9), boxHeight = 15f)
        )
    }
}
