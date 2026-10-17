package com.flowfolio.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    /** Most recently opened first; never-opened books by date added. */
    @Query("SELECT * FROM books ORDER BY COALESCE(last_opened_at, added_at) DESC")
    fun observeByRecent(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY title COLLATE NOCASE ASC")
    fun observeByTitle(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity)

    @Query("UPDATE books SET storage_mode = :storageMode, location = :location WHERE id = :id")
    suspend fun updateLocation(id: String, storageMode: String, location: String)

    @Query("UPDATE books SET last_opened_at = :at WHERE id = :id")
    suspend fun markOpened(id: String, at: Long)

    @Query("UPDATE books SET last_page = :page WHERE id = :id")
    suspend fun updateLastPage(id: String, page: Int)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String): Int
}
