package com.flowfolio.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions") // one query per change the app makes to a book
interface BookDao {
    /** Marks [id] a favourite or not. */
    @Query("UPDATE books SET favorite = :on WHERE id = :id")
    suspend fun setFavorite(id: String, on: Boolean)

    /** Marks [id] finished at [at] (epoch millis), or not finished (null). */
    @Query("UPDATE books SET finished_at = :at WHERE id = :id")
    suspend fun setFinishedAt(id: String, at: Long?)

    @Query("UPDATE books SET intent = :intent WHERE id = :id")
    suspend fun setIntent(id: String, intent: String?)

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

    @Query("UPDATE books SET folder = :folder WHERE id = :id")
    suspend fun setFolder(id: String, folder: String?)

    @Query("UPDATE books SET last_opened_at = :at WHERE id = :id")
    suspend fun markOpened(id: String, at: Long)

    /** Everything the reader can edit about a book (Edit details). */
    @Query(
        """UPDATE books SET title = :title, author = :author, subtitle = :subtitle, edition = :edition,
        publisher = :publisher, isbn = :isbn, description = :description, language = :language, released = :released,
        author_role = :authorRole, author_bio = :authorBio, book_type = :bookType, subjects = :subjects,
        print_pages = :printPages, series = :series, rating = :rating, ratings_count = :ratingsCount WHERE id = :id"""
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
        bookType: String?,
        subjects: String? = null,
        printPages: Int? = null,
        series: String? = null,
        rating: Float? = null,
        ratingsCount: Int? = null
    )

    /**
     * A page position; an EPUB's locator is cleared, so it opens at this page (measurement scripts set it), and so is a
     * PDF's place in Dynamic Flow, which then opens at this PDF page too (ADR-0045).
     */
    @Query("UPDATE books SET last_page = :page, last_locator = NULL, flow_locator = NULL WHERE id = :id")
    suspend fun updateLastPage(id: String, page: Int)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String): Int
}
