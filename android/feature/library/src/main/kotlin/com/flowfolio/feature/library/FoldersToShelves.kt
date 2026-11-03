package com.flowfolio.feature.library

import com.flowfolio.core.data.ShelfRepository
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.ShelfColor
import kotlin.random.Random

/**
 * Turning the folders a batch of books came in into shelves (owner, M5 round 4).
 *
 * A folder import already remembers where each book sat ([Book.folder], "Books/Physics"). This makes a shelf per
 * folder that actually holds books -- at every depth, so a folder inside a folder gets its own shelf -- and stands
 * the books of that folder on it. A folder that only contains other folders gets no shelf, because an empty one
 * would be a row of nothing.
 *
 * A shelf is named after the folder itself, not its path: "Physics", not "Books/Physics". Two folders of the same
 * name in different places therefore share a shelf, which is almost always what was meant by naming them the same.
 * Colours are picked at random, since nothing about a folder suggests one; the reader can change both afterwards.
 */
internal object FoldersToShelves {
    /** The shelves [books] would make, folder name to the books that would stand on it, in import order. */
    fun plan(books: List<Book>): Map<String, List<Book>> {
        val byShelf = LinkedHashMap<String, MutableList<Book>>()
        books.forEach { book ->
            val name = book.folder?.substringAfterLast('/')?.trim().orEmpty()
            if (name.isNotEmpty()) byShelf.getOrPut(name) { mutableListOf() } += book
        }
        return byShelf
    }

    /**
     * Makes the shelves of [plan] that are not there already and stands their books on them.
     *
     * An existing shelf of the same name is used rather than a second one made: importing the same folder twice
     * should leave one shelf, not a pair of them.
     */
    suspend fun apply(plan: Map<String, List<Book>>, shelves: ShelfRepository, existing: Map<String, Long>) {
        plan.forEach { (name, books) ->
            val id = existing[name] ?: shelves.create(name, ShelfColor.entries[Random.nextInt(COLOURS)])
            shelves.addBooks(id, books.map { it.id })
        }
    }

    private val COLOURS = ShelfColor.entries.size
}
