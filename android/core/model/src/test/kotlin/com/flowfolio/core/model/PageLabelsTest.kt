package com.flowfolio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageLabelsTest {
    private val book = listOf(null, null, "i", "ii", "iii", "iv", "1", "2", "3", "A-1", "A-2", "Index")

    @Test
    fun compressedRunsReproduceTheLabels() {
        val runs = PageLabels.compress(book)
        assertEquals(book, PageLabels.expand(runs, book.size))
        assertEquals(5, runs.size) // none, roman, arabic, A-, literal
    }

    @Test
    fun encodedRunsSurviveStorage() {
        val runs = PageLabels.compress(book)
        assertEquals(runs, PageLabels.decode(PageLabels.encode(runs)))
        assertEquals(emptyList<LabelRun>(), PageLabels.decode(""))
        assertEquals(emptyList<LabelRun>(), PageLabels.decode(null))
    }

    @Test
    fun romanNumeralsAreStrict() {
        assertEquals(14, PageLabels.parseRoman("xiv"))
        assertEquals(1999, PageLabels.parseRoman("MCMXCIX"))
        assertNull(PageLabels.parseRoman("ic")) // 99 is written XCIX
        assertNull(PageLabels.parseRoman("dog"))
        assertNull(PageLabels.parseRoman("iiii"))
        assertEquals("XLII", PageLabels.roman(42))
    }

    @Test
    fun aBookWithoutNumbersCountsFromTheCover() {
        val plain = Book("b", "T", 10, BookStorage.Copied("/x"), 0, 0, null, null, false, pageLabels = "")
        assertEquals("1", plain.pageLabel(0))
        assertEquals("10", plain.printedPages())
        val printed = plain.copy(pageLabels = PageLabels.encode(PageLabels.compress(book.take(9) + listOf("4"))))
        assertEquals("ii", printed.pageLabel(3))
        assertEquals("4", printed.printedPages())
    }
}
