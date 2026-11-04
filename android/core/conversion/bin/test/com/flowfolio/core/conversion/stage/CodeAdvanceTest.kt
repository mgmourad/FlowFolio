package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.ir.Line
import com.flowfolio.core.conversion.ir.Role
import com.flowfolio.core.conversion.ir.Span
import com.flowfolio.core.conversion.ir.Style
import com.flowfolio.core.conversion.ir.StyleKey
import com.flowfolio.core.conversion.pdf.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The character cell a code listing is laid out on (owner, 2026-11-03).
 *
 * A Packt DevOps PDF sets its fonts at size 1 and scales them through the text matrix, so every style reached the
 * converter as `size=1.0`. The cell then came from the style as `size * TYPICAL_MONO_EMS` -- 0.6pt against a real
 * cell of 6pt -- and ten times too narrow puts every character in a column of its own, so whole listings came out
 * as vertical columns of single letters. The listing's own spacing is measured from the glyphs themselves, which
 * makes it the better witness whatever the font size claims.
 */
class CodeAdvanceTest {
    private val mono = StyleKey("CourierStd", 1f, bold = false, italic = false, mono = true)

    /** The converter's view of that book: a code style whose width was derived from a font size of 1. */
    private val blocks = Blocks(listOf(Style(mono, Role.CODE, 0, 5_000, advance = 0.6f)))

    /** A line whose characters sit [advance] apart, as PDFium reports them. */
    private fun line(text: String, advance: Float): Line {
        val lefts = FloatArray(text.length) { it * advance }
        val box = Box(0f, 0f, text.length * advance, 10f)
        return Line(listOf(Span(text, box, mono, lefts)), box)
    }

    @Test
    fun `the listing's own spacing wins over a width from a broken font size`() {
        assertEquals(6f, blocks.advanceFor(listOf(line("sudo apt-get update", 6f))), 0.01f)
    }

    @Test
    fun `a listing with no pair to measure still gets a cell`() {
        // One character, so nothing to measure: fall back rather than lay out on a width of zero.
        assertTrue(blocks.advanceFor(listOf(line("x", 6f))) > 0f)
    }

    @Test
    fun `the cell is the median, so one wide gap does not set it`() {
        // Characters 6pt apart but for a 40pt jump, which is a gap in the listing rather than the cell.
        val lefts = floatArrayOf(0f, 6f, 12f, 52f, 58f, 64f, 70f)
        val box = Box(0f, 0f, 76f, 10f)
        val line = Line(listOf(Span("abcdefg", box, mono, lefts)), box)
        assertEquals(6f, blocks.advanceFor(listOf(line)), 0.01f)
    }
}
