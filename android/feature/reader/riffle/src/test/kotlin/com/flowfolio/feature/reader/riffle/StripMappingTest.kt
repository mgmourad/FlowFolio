package com.flowfolio.feature.reader.riffle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StripMappingTest {
    /** A plain book of [pages] pages: spreads with the cover alone, or single pages. */
    private class Book(pages: Int, private val single: Boolean, override val rightToLeft: Boolean = false) :
        BookGeometry {
        override val pageCount = pages
        override val unitCount = if (single) pages else pages / 2 + 1
        override fun pagesOf(unit: Int) = when {
            single -> unit..unit
            unit == 0 -> 0..0
            else -> (2 * unit - 1)..minOf(2 * unit, pageCount - 1)
        }
        override fun unitOf(page: Int) = if (single) page else (page + 1) / 2
        override val sections = emptyList<Section>()
        override fun label(page: Int) = (page + 1).toString()
        override fun pageOfLabel(label: String) = label.toIntOrNull()?.minus(1)
    }

    @Test
    fun endsMapToTheFirstAndLastUnitForOddAndEvenPageCounts() {
        for (pages in listOf(1, 2, 3, 842, 843)) {
            for (single in listOf(false, true)) {
                val m = StripMapping(Book(pages, single))
                val last = m.unitCount - 1
                assertEquals("$pages/$single left end", 0, m.unitAtX(0f, 0f, 1000f))
                assertEquals("$pages/$single right end", last, m.unitAtX(1000f, 0f, 1000f))
                // Past the ends clamps to them.
                assertEquals(0, m.unitAtX(-50f, 0f, 1000f))
                assertEquals(last, m.unitAtX(5000f, 0f, 1000f))
            }
        }
    }

    @Test
    fun everyUnitMapsBackToItself() {
        for (pages in listOf(2, 3, 842, 843)) {
            for (single in listOf(false, true)) {
                val m = StripMapping(Book(pages, single))
                for (u in 0 until m.unitCount) {
                    assertEquals("$pages/$single unit $u", u, m.unitAtX(m.xOf(u, 26f, 2000f), 26f, 2000f))
                }
            }
        }
    }

    @Test
    fun spreadsOpenExactlyBetweenTheirSheets() {
        val m = StripMapping(SampleBook())
        assertEquals(421, m.sheetCount)
        // Pages 216-217 (0-based 215 and 216) open between sheets 107 and 108: 108 sheets lie to the left.
        val unit = SampleBook().unitOf(215)
        assertEquals(108f / 421f, m.fractionOf(unit), 1e-6f)
        // Positions only increase.
        for (u in 1 until m.unitCount) assertTrue(m.fractionOf(u) > m.fractionOf(u - 1))
    }

    @Test
    fun rightToLeftBooksAreMirrored() {
        val ltr = StripMapping(Book(842, single = false))
        val rtl = StripMapping(Book(842, single = false, rightToLeft = true))
        assertEquals(1f - ltr.fractionOf(100), rtl.fractionOf(100), 1e-6f)
        assertEquals(0, rtl.unitAtX(1000f, 0f, 1000f))
        assertEquals(rtl.unitCount - 1, rtl.unitAtX(0f, 0f, 1000f))
    }

    @Test
    fun leavesThinOutOnlyWhenTooDense() {
        val m = StripMapping(SampleBook())
        // 421 sheets in 2,100 px: 1.6 px apart fits 1,312 leaves, so one per sheet.
        assertEquals(1, m.sheetsPerLeaf(2100f))
        assertEquals(421, m.leafCount(2100f))
        // A 500 px cover-screen strip fits 312 leaves: two sheets per leaf.
        assertEquals(2, m.sheetsPerLeaf(500f))
        assertEquals(211, m.leafCount(500f))
        // The mapping is the same whatever the drawing.
        assertEquals(m.unitAtX(250f, 0f, 500f), m.unitAt(0.5f))
    }

    @Test
    fun theGapKeepsBothCoversStillAndOpensAroundThePin() {
        val gap = Gap(density = 1f).prepare(engagement = 1f, pin = 400f, x0 = 0f, x1 = 1000f)
        assertEquals(0f, gap.displacement(0f), 1e-4f)
        assertEquals(0f, gap.displacement(1000f), 1e-4f)
        // Leaves just right of the pin move right, just left move left, by up to G = 34 dp in all.
        assertTrue(gap.displacement(410f) > 0f)
        assertTrue(gap.displacement(390f) < 0f)
        val opened = gap.displacement(430f) - gap.displacement(370f)
        assertTrue("gap $opened", opened in 40f..68f)
        // At rest the gap is small (G = 4).
        val rest = Gap(1f).prepare(0f, 400f, 0f, 1000f)
        assertTrue(rest.displacement(430f) - rest.displacement(370f) < 8f)
    }
}
