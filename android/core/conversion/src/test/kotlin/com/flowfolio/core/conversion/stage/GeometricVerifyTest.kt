package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.ir.Line
import com.flowfolio.core.conversion.ir.Span
import com.flowfolio.core.conversion.ir.StyleKey
import com.flowfolio.core.conversion.pdf.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 4 M1 spike: geometric verification catches the Packt "size=1.0" class of bug. */
class GeometricVerifyTest {
    private val normal = StyleKey("CourierStd", 10f, bold = false, italic = false, mono = true)
    private val packt = StyleKey("CourierStd", 1f, bold = false, italic = false, mono = true)

    /** A line whose glyphs are [height] tall, as the Packt book's rendered glyphs are ~10pt against a size of 1. */
    private fun line(style: StyleKey, height: Float): Line {
        val box = Box(0f, 0f, 120f, height)
        return Line(listOf(Span("sudo apt-get update", box, style)), box)
    }

    @Test
    fun `a style that matches its glyphs is consistent`() {
        assertTrue(GeometricVerify.sizeIsConsistent(line(normal, 10f)))
    }

    @Test
    fun `the Packt book's size-1 style over 10pt glyphs is flagged`() {
        assertFalse(GeometricVerify.sizeIsConsistent(line(packt, 10f)))
    }

    @Test
    fun `the ratio is the distance from the truth, in multiples of the size`() {
        assertEquals(10f, GeometricVerify.sizeRatio(line(packt, 10f)), 0.01f)
        assertEquals(1f, GeometricVerify.sizeRatio(line(normal, 10f)), 0.01f)
    }

    @Test
    fun `coverage is the share of text in consistent lines`() {
        assertEquals(1f, GeometricVerify.coverage(listOf(line(normal, 10f))), 0.01f)
        assertEquals(0f, GeometricVerify.coverage(listOf(line(packt, 10f))), 0.01f)
        assertEquals(0.5f, GeometricVerify.coverage(listOf(line(normal, 10f), line(packt, 10f))), 0.01f)
    }
}
