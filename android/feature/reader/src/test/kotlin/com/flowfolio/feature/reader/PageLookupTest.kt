package com.flowfolio.feature.reader

import com.flowfolio.core.model.OutlineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageLookupTest {
    private val labels = listOf("Cover", "i", "ii", "1", "2", "3", null)

    @Test
    fun printedLabelWinsOverPosition() {
        assertEquals(3, PageLookup.resolve("1", labels, labels.size)) // printed page 1 is the 4th page
        assertEquals(2, PageLookup.resolve(" II ", labels, labels.size))
    }

    @Test
    fun plainNumberFallsBackToPosition() {
        assertEquals(6, PageLookup.resolve("7", labels, labels.size))
        assertEquals(4, PageLookup.resolve("5", emptyList(), 10))
    }

    @Test
    fun rejectsEmptyUnknownAndOutOfRange() {
        assertNull(PageLookup.resolve("", labels, labels.size))
        assertNull(PageLookup.resolve("xiv", labels, labels.size))
        assertNull(PageLookup.resolve("0", emptyList(), 10))
        assertNull(PageLookup.resolve("11", emptyList(), 10))
    }

    @Test
    fun chapterIsTheLastEntryStartingAtOrBeforeThePage() {
        val outline = listOf(
            OutlineEntry(0, 0, "Preface"),
            OutlineEntry(0, 10, "Chapter 1"),
            OutlineEntry(1, 12, "1.1"),
            OutlineEntry(0, -1, "Broken link"),
            OutlineEntry(0, 30, "Chapter 2")
        )
        assertEquals(0, PageLookup.chapterAt(outline, 5))
        assertEquals(2, PageLookup.chapterAt(outline, 29))
        assertEquals(4, PageLookup.chapterAt(outline, 30))
        assertEquals(-1, PageLookup.chapterAt(emptyList(), 3))
    }

    @Test
    fun chapterIgnoresUnsortedTrailingEntries() {
        val outline = listOf(
            OutlineEntry(0, 5, "Preface"),
            OutlineEntry(0, 12, "Chapter 1"),
            OutlineEntry(0, 900, "Answer key"),
            OutlineEntry(0, 0, "Book.pdf")
        )
        assertEquals(1, PageLookup.chapterAt(outline, 20))
        assertEquals(0, PageLookup.chapterAt(outline, 5))
        assertEquals(3, PageLookup.chapterAt(outline, 2))
    }

    @Test
    fun labelFallsBackToNumber() {
        assertEquals("ii", PageLookup.labelOf(2, labels))
        assertEquals("7", PageLookup.labelOf(6, labels))
        assertEquals("43", PageLookup.labelOf(42, emptyList()))
    }
}
