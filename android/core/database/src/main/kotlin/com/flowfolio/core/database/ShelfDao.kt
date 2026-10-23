package com.flowfolio.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ShelfDao {
    /** Shelves in the order they were made. */
    @Query("SELECT * FROM shelves ORDER BY created_at ASC, id ASC")
    fun observeShelves(): Flow<List<ShelfEntity>>

    /** Every book-on-shelf row, newest first. */
    @Query("SELECT * FROM shelf_books ORDER BY added_at DESC")
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

    /** Puts [bookId] on exactly [shelfIds]: keeps rows already there (and their dates), adds and removes the rest. */
    @Transaction
    suspend fun setShelves(bookId: String, shelfIds: Set<Long>, now: Long) {
        val current = shelvesOf(bookId).toSet()
        val gone = current - shelfIds
        if (gone.isNotEmpty()) removeFrom(bookId, gone.toList())
        val added = shelfIds - current
        if (added.isNotEmpty()) addAll(added.map { ShelfBookEntity(it, bookId, now) })
    }
}
