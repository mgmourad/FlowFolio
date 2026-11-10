package com.flowfolio.feature.library

import com.flowfolio.core.model.Book
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.ReadingGoal

/**
 * What a shelf's books are ordered by. Shared by the shelf page and, since M4, the library's own shelf rows, so the
 * two offer the same choices and order them the same way.
 *
 * [ShelfSort.CUSTOM] is "leave them as they are": on the shelf page and in the library that is the shelf's own
 * drag-and-drop order (`shelf_books.position`, Room v13).
 */
enum class ShelfSort { CUSTOM, NAME, DATE_ADDED, PROGRESS, READ_TIME, READ_GOAL }

/**
 * What the orders beyond the book itself need: how long each book has been read, and each book's own reading goal,
 * both keyed by book id.
 */
data class ShelfSortInputs(
    val readMillis: Map<String, Long> = emptyMap(),
    val goals: Map<String, ReadingGoal> = emptyMap()
)

/**
 * [books] in [sort] order. Every order but [ShelfSort.CUSTOM] falls back to the title, so books that tie (two
 * unread books, two books never opened) still come out in a steady order rather than shuffling between reads.
 */
fun sortBooks(books: List<Book>, sort: ShelfSort, inputs: ShelfSortInputs): List<Book> = when (sort) {
    ShelfSort.CUSTOM -> books

    ShelfSort.NAME -> books.sortedBy { it.title.lowercase() }

    ShelfSort.DATE_ADDED -> books.sortedWith(
        compareByDescending<Book> { it.addedAtMillis }.thenBy { it.title.lowercase() }
    )

    ShelfSort.PROGRESS -> books.sortedWith(
        compareByDescending<Book> { it.progress }.thenBy { it.title.lowercase() }
    )

    ShelfSort.READ_TIME -> books.sortedWith(
        compareByDescending<Book> { inputs.readMillis[it.id] ?: 0L }.thenBy { it.title.lowercase() }
    )

    ShelfSort.READ_GOAL -> books.sortedWith(
        compareByDescending<Book> { inputs.goals[it.id]?.targetMinutes ?: 0 }.thenBy { it.title.lowercase() }
    )
}

/**
 * The library's books in [sort] order (ADR-0052), with [readMillis] for [LibrarySort.READ_TIME]. Ties fall back to the
 * title, so equal books keep a steady order. "Recently opened" puts books never opened last, newest added first.
 */
fun orderLibrary(books: List<Book>, sort: LibrarySort, readMillis: Map<String, Long> = emptyMap()): List<Book> {
    val byTitle = compareBy<Book> { it.title.lowercase() }
    val order: Comparator<Book> = when (sort) {
        LibrarySort.RECENT -> compareByDescending<Book> { it.lastOpenedAtMillis ?: Long.MIN_VALUE }
            .thenByDescending { it.addedAtMillis }

        LibrarySort.TITLE -> byTitle

        LibrarySort.AUTHOR -> compareBy<Book> { it.author.isNullOrBlank() }.thenBy { it.author?.lowercase() }

        LibrarySort.DATE_ADDED -> compareByDescending { it.addedAtMillis }

        LibrarySort.PROGRESS -> compareByDescending { if (it.finished) 1f else it.progress }

        LibrarySort.READ_TIME -> compareByDescending { readMillis[it.id] ?: 0L }
    }
    return books.sortedWith(order.then(byTitle))
}

/**
 * Where each book sits in [sort]'s order, by book id. The library keeps its books on their shelves and reorders
 * within each row by this rank (owner, M4), so one pass over the whole library orders every row.
 *
 * [ShelfSort.CUSTOM] gives no ranking: rows then keep the shelf's own order.
 */
fun sortRank(books: List<Book>, sort: ShelfSort, inputs: ShelfSortInputs): Map<String, Int> =
    if (sort == ShelfSort.CUSTOM) {
        emptyMap()
    } else {
        sortBooks(books, sort, inputs).withIndex().associate { (index, book) -> book.id to index }
    }
