package com.flowfolio.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions") // one query per change the app makes to shelves
interface ShelfDao {
    /** Shelves in the order they were made. */
    @Query("SELECT * FROM shelves ORDER BY created_at ASC, id ASC")
    fun observeShelves(): Flow<List<ShelfEntity>>

    /** Every book-on-shelf row in shelf order: by position, then newest first. */
    @Query("SELECT * FROM shelf_books ORDER BY position ASC, added_at DESC")
    fun observeMemberships(): Flow<List<ShelfBookEntity>>

    @Insert
    suspend fun insert(shelf: ShelfEntity): Long

    @Query("UPDATE shelves SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE shelves SET color = :color WHERE id = :id")
    suspend fun setColor(id: Long, color: String?)

    @Query("DELETE FROM shelves WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT shelf_id FROM shelf_books WHERE book_id = :bookId")
    suspend fun shelvesOf(bookId: String): List<Long>

    @Insert
    suspend fun addAll(rows: List<ShelfBookEntity>)

    @Query("DELETE FROM shelf_books WHERE book_id = :bookId AND shelf_id IN (:shelfIds)")
    suspend fun removeFrom(bookId: String, shelfIds: List<Long>)

    @Query("SELECT COALESCE(MIN(position), 0) FROM shelf_books WHERE shelf_id = :shelfId")
    suspend fun firstPosition(shelfId: Long): Int

    @Query("SELECT book_id FROM shelf_books WHERE shelf_id = :shelfId")
    suspend fun booksOn(shelfId: Long): List<String>

    @Query("UPDATE shelf_books SET position = :position WHERE shelf_id = :shelfId AND book_id = :bookId")
    suspend fun setPosition(shelfId: Long, bookId: String, position: Int)

    /** Puts [bookId] on exactly [shelfIds]: keeps rows already there (and their places), adds and removes the rest. */
    @Transaction
    suspend fun setShelves(bookId: String, shelfIds: Set<Long>, now: Long) {
        val current = shelvesOf(bookId).toSet()
        val gone = current - shelfIds
        if (gone.isNotEmpty()) removeFrom(bookId, gone.toList())
        // A book put on a shelf stands first, as the newest did before shelves had an order.
        (shelfIds - current).forEach { addAll(listOf(ShelfBookEntity(it, bookId, now, firstPosition(it) - 1))) }
    }

    /** Puts [bookIds] on [shelfId] in front of the books already there, in the given order; ones already on it stay. */
    @Transaction
    suspend fun addBooks(shelfId: Long, bookIds: List<String>, now: Long) {
        val on = booksOn(shelfId).toSet()
        val fresh = bookIds.distinct().filterNot { it in on }
        val first = firstPosition(shelfId) - fresh.size
        addAll(fresh.mapIndexed { i, id -> ShelfBookEntity(shelfId, id, now, first + i) })
    }

    /** Stands [bookIds] on [shelfId] in this order (drag and drop); books not listed keep their places after them. */
    @Transaction
    suspend fun setOrder(shelfId: Long, bookIds: List<String>) {
        bookIds.forEachIndexed { i, id -> setPosition(shelfId, id, i) }
    }
}
