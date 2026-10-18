package com.flowfolio.core.data

import com.flowfolio.core.data.FolioScanner.Folio
import com.flowfolio.core.model.NumberStyle
import com.flowfolio.core.model.PageLabels
import com.flowfolio.core.pdf.PageText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolioScannerTest {
    /** A page whose running head reads "[head] [folio]" in the top margin, then body text below. */
    private fun page(head: String): PageText {
        val text = "$head\nBody text"
        val boxes = FloatArray(text.length * PageText.FIELDS) { -1f }
        text.forEachIndexed { i, c ->
            if (c == '\n') return@forEachIndexed
            val top = if (i < head.length) 0.03f else 0.5f
            boxes[i * 4] = 0.1f + i * 0.01f
            boxes[i * 4 + 1] = top
            boxes[i * 4 + 2] = 0.1f + i * 0.01f + 0.008f
            boxes[i * 4 + 3] = top + 0.02f
        }
        return PageText(text, boxes)
    }

    @Test
    fun readsNumbersInTheMarginsOnly() {
        assertEquals(
            listOf(Folio(NumberStyle.ARABIC, 2), Folio(NumberStyle.ARABIC, 48)),
            FolioScanner.folios(page("2 Vectors 48"))
        )
        assertEquals(listOf(Folio(NumberStyle.ROMAN_LOWER, 14)), FolioScanner.folios(page("Preface xiv")))
        assertTrue(FolioScanner.folios(page("Scalars and Vectors")).isEmpty())
    }

    @Test
    fun theOffsetMostPagesAgreeOnWins() {
        // OpenStax style: chapter numbers in the head change, the folio runs 11 pages behind the file.
        val samples = (15 until 200 step 5).associateWith { p ->
            listOf(Folio(NumberStyle.ARABIC, 1 + p / 40), Folio(NumberStyle.ARABIC, p - 11))
        }
        val runs = FolioScanner.runs(samples + (5 to listOf(Folio(NumberStyle.ROMAN_LOWER, 3))), pageCount = 200)
        val labels = PageLabels.expand(runs, 200)
        assertEquals("48", labels[59])
        assertEquals("1", labels[12])
        assertEquals(null, labels[0])
    }

    @Test
    fun noConsistentNumbersMeansNoRuns() {
        val samples = (0 until 50 step 5).associateWith { listOf(Folio(NumberStyle.ARABIC, it * 7 % 13 + 1)) }
        assertTrue(FolioScanner.runs(samples, 50).isEmpty())
    }
}
