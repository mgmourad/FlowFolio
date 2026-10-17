package com.flowfolio.feature.reader

import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.Spreads
import com.flowfolio.feature.reader.layout.chooseLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutTest {
    @Test
    fun `fold cover screen and phones are single, unfolded and tablets in landscape are spreads`() {
        assertEquals(ReaderLayout.SINGLE, chooseLayout(360f, 840f)) // cover screen / phone portrait
        assertEquals(ReaderLayout.SPREAD, chooseLayout(752f, 835f)) // Fold inner, portrait
        assertEquals(ReaderLayout.SPREAD, chooseLayout(835f, 752f)) // Fold inner, landscape
        assertEquals(ReaderLayout.SINGLE, chooseLayout(800f, 1280f)) // tablet portrait
        assertEquals(ReaderLayout.SPREAD, chooseLayout(1280f, 800f)) // tablet landscape
        assertEquals(ReaderLayout.SINGLE, chooseLayout(560f, 360f)) // small phone landscape: too narrow
    }

    @Test
    fun `cover stands alone then pages pair like print`() {
        val count = 959
        assertEquals(480, Spreads.unitCount(count))
        assertEquals(listOf(0), Spreads.pagesOf(0, count))
        assertEquals(listOf(1, 2), Spreads.pagesOf(1, count))
        assertEquals(listOf(957, 958), Spreads.pagesOf(479, count))
        assertEquals(-1, Spreads.leftOf(0, count))
        assertEquals(0, Spreads.rightOf(0, count))
    }

    @Test
    fun `even page count ends with a lone left page`() {
        assertEquals(6, Spreads.unitCount(10))
        assertEquals(listOf(9), Spreads.pagesOf(5, 10))
        assertEquals(-1, Spreads.rightOf(5, 10))
    }

    @Test
    fun `every page belongs to exactly the unit that shows it`() {
        for (count in listOf(1, 2, 3, 10, 959)) {
            for (page in 0 until count) {
                val unit = Spreads.unitOf(page)
                assertEquals("page $page of $count", true, page in Spreads.pagesOf(unit, count))
            }
        }
    }

    @Test
    fun `switching back to single page keeps the first visible page`() {
        assertEquals(0, Spreads.anchorOf(0, 959))
        assertEquals(121, Spreads.anchorOf(61, 959))
    }

    @Test
    fun `empty documents have no units`() {
        assertEquals(0, Spreads.unitCount(0))
    }
}
