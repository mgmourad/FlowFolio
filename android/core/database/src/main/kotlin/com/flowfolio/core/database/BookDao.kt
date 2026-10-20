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

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: String): Flow<BookEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity)

    @Query("UPDATE books SET storage_mode = :storageMode, location = :location WHERE id = :id")
    suspend fun updateLocation(id: String, storageMode: String, location: String)

    @Query("UPDATE books SET last_opened_at = :at WHERE id = :id")
    suspend fun markOpened(id: String, at: Long)

    /** Everything the reader can edit about a book (Edit details). */
    @Query(
        """UPDATE books SET title = :title, author = :author, subtitle = :subtitle, edition = :edition,
        publisher = :publisher, isbn = :isbn, description = :description, language = :language, released = :released,
        author_role = :authorRole, author_bio = :authorBio, book_type = :bookType WHERE id = :id"""
    )
    @Suppress("LongParameterList") // one column each, as the query reads
    suspend fun updateDetails(
        id: String,
        title: String,
        author: String?,
        subtitle: String?,
        edition: String?,
        publisher: String?,
        isbn: String?,
        description: String?,
        language: String?,
        released: String?,
        authorRole: String?,
        authorBio: String?,
        bookType: String?
    )

    /** A page position; an EPUB's locator is cleared, so it opens at this page (measurement scripts set it). */
    @Query("UPDATE books SET last_page = :page, last_locator = NULL WHERE id = :id")
    suspend fun updateLastPage(id: String, page: Int)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String): Int
}
