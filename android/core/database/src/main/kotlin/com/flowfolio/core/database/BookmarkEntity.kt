package com.flowfolio.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A ribbon bookmark. Removing the book removes its bookmarks. Added in schema v4. */
@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["book_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("book_id")]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: String,
    val page: Int,
    /** A BookmarkColor name; unknown names read as the default colour. */
    val color: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** An EPUB ribbon's `chapter:offset`, the text at the top of its page (ADR-0040). Added in v14. */
    val locator: String? = null
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE book_id = :bookId ORDER BY page ASC, created_at ASC")
    fun observe(bookId: String): Flow<List<BookmarkEntity>>

    /** All bookmarks, newest first (the Bookmarks tab). */
    @Query("SELECT * FROM bookmarks ORDER BY created_at DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("UPDATE bookmarks SET color = :color WHERE id = :id")
    suspend fun setColor(id: Long, color: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    /** Where an EPUB ribbon's text now is, after a re-layout. */
    @Query("UPDATE bookmarks SET page = :page WHERE id = :id")
    suspend fun setPage(id: Long, page: Int)
}
