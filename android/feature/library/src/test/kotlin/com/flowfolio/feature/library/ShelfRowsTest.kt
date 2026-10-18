package com.flowfolio.feature.library

import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.core.model.Shelf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfRowsTest {
    private val a = LibraryItem(book("a"))
    private val b = LibraryItem(book("b"))
    private val c = LibraryItem(book("c"))

    @Test
    fun withoutShelvesEveryBookIsInOneRow() {
        val rows = shelfRows(listOf(a, b), emptyList())
        assertEquals(listOf(ShelfRow(null, listOf(a, b))), rows)
    }

    @Test
    fun shelvesKeepTheirOrderAndLooseBooksComeLast() {
        val shelves =
            listOf(Shelf(1, "One", listOf("b", "a")), Shelf(2, "Two", listOf("a")), Shelf(3, "Empty", listOf()))
        val rows = shelfRows(listOf(a, b, c), shelves)
        assertEquals(listOf(listOf(b, a), listOf(a), listOf(c)), rows.map { it.items })
        assertEquals(listOf(1L, 2L, null), rows.map { it.shelf?.id })
    }

    @Test
    fun filteredOutBooksDropTheirShelves() {
        val rows = shelfRows(listOf(c), listOf(Shelf(1, "One", listOf("a"))))
        assertEquals(listOf(ShelfRow(null, listOf(c))), rows)
    }

    @Test
    fun filtersAndSearch() {
        val started = book("s").copy(lastPage = 3, title = "Optics", author = "Hecht")
        val fresh = book("f").copy(addedAtMillis = 1_000)
        assertTrue(LibraryFilter.IN_PROGRESS.accepts(started, now = 0))
        assertFalse(LibraryFilter.NOT_STARTED.accepts(started, now = 0))
        assertTrue(LibraryFilter.NOT_STARTED.accepts(fresh, now = 0))
        assertTrue(LibraryFilter.RECENT.accepts(fresh, now = 2_000))
        assertFalse(LibraryFilter.RECENT.accepts(fresh, now = 1_000L + 31L * 24 * 60 * 60 * 1000))
        assertTrue(started.matches("hech"))
        assertTrue(started.matches(" opt "))
        assertFalse(started.matches("calculus"))
    }

    @Test
    fun currentChapterIsTheLastOneStartedBeforeThePosition() {
        val toc = listOf(OutlineEntry(0, 0, "A"), OutlineEntry(0, 10, "B"), OutlineEntry(0, 20, "C"))
        assertEquals(-1, currentChapter(toc, null))
        assertEquals(0, currentChapter(toc, 9))
        assertEquals(1, currentChapter(toc, 10))
        assertEquals(2, currentChapter(toc, 99))
    }
}
