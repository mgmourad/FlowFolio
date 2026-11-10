package com.flowfolio.feature.library

import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.Shelf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Library of ADR-0052: its row keys, its six orders and its search minimum. */
class LibraryOverhaulTest {
    private fun item(id: String) = LibraryItem(book(id))

    /** The crash from the review: two rows without a shelf both took the key "row-loose". */
    @Test
    fun everyRowHasItsOwnKey() {
        val rows = listOf(
            ShelfRow(Shelf(1, "One", listOf("a")), listOf(item("a"))),
            ShelfRow(null, listOf(item("b"))),
            ShelfRow(null, listOf(item("c")), special = ShelfPageViewModel.FINISHED),
            ShelfRow(null, listOf(item("d")), special = ShelfPageViewModel.FAVORITES)
        )
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test
    fun shelfRowsGiveUniqueKeysWithLooseAndFinishedBooks() {
        val done = LibraryItem(book("f").copy(finishedAtMillis = 5L))
        val rows = shelfRows(listOf(item("a"), item("b"), done), listOf(Shelf(1, "One", listOf("a"))))
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
        assertEquals(null, rows.last().shelf)
    }

    @Test
    fun recentlyOpenedPutsUnopenedBooksLastNewestAddedFirst() {
        val old = book("old").copy(addedAtMillis = 1)
        val new = book("new").copy(addedAtMillis = 2)
        val read = book("read").copy(lastOpenedAtMillis = 10)
        val sorted = orderLibrary(listOf(old, new, read), LibrarySort.RECENT)
        assertEquals(listOf("read", "new", "old"), sorted.map { it.id })
    }

    @Test
    fun titleAuthorAddedProgressAndReadTimeOrders() {
        val a = book("a", "Beta").copy(author = "Zed", addedAtMillis = 3, lastPage = 9, pageCount = 10)
        val b = book("b", "alpha").copy(author = "Ann", addedAtMillis = 1, lastPage = 1, pageCount = 10)
        val c = book("c", "Gamma").copy(author = null, addedAtMillis = 2)
        val books = listOf(a, b, c)
        assertEquals(listOf("b", "a", "c"), orderLibrary(books, LibrarySort.TITLE).map { it.id })
        assertEquals(listOf("b", "a", "c"), orderLibrary(books, LibrarySort.AUTHOR).map { it.id })
        assertEquals(listOf("a", "c", "b"), orderLibrary(books, LibrarySort.DATE_ADDED).map { it.id })
        assertEquals("a", orderLibrary(books, LibrarySort.PROGRESS).first().id)
        val millis = mapOf("c" to 5_000L, "a" to 1_000L)
        assertEquals(listOf("c", "a", "b"), orderLibrary(books, LibrarySort.READ_TIME, millis).map { it.id })
    }

    @Test
    fun searchWaitsForTwoLetters() {
        val sources = SearchViewModel.Sources(
            listOf(book("p", "Python")),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList()
        )
        assertTrue(SearchViewModel.search("p", sources).empty)
        assertTrue(SearchViewModel.search(" p ", sources).empty)
        assertTrue(!SearchViewModel.search("py", sources).empty)
    }

    @Test
    fun shelvesOrderByNameBooksReadAndMarks() {
        val a = ShelfCard(Shelf(1, "Zoology", listOf()), listOf(book("x")), readTimeMillis = 10, annotationsCount = 1)
        val b = ShelfCard(Shelf(2, "algebra", listOf()), listOf(book("y"), book("z")), readTimeMillis = 5)
        val c = ShelfCard(Shelf(3, "Music", listOf()), emptyList(), readTimeMillis = 50, bookmarksCount = 4)
        val cards = listOf(a, b, c)
        assertEquals(
            listOf(1L, 2L, 3L),
            orderShelves(cards, com.flowfolio.core.model.ShelvesOrder.CUSTOM).map {
                it.shelf.id
            }
        )
        assertEquals(
            listOf(2L, 3L, 1L),
            orderShelves(cards, com.flowfolio.core.model.ShelvesOrder.NAME).map {
                it.shelf.id
            }
        )
        assertEquals(2L, orderShelves(cards, com.flowfolio.core.model.ShelvesOrder.MOST_BOOKS).first().shelf.id)
        assertEquals(3L, orderShelves(cards, com.flowfolio.core.model.ShelvesOrder.MOST_READ).first().shelf.id)
        assertEquals(3L, orderShelves(cards, com.flowfolio.core.model.ShelvesOrder.MOST_MARKS).first().shelf.id)
    }
}
