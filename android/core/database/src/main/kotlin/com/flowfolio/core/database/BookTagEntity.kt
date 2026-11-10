package com.flowfolio.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A tag the reader gave a book (schema v22, owner 2026-11-10, ADR-0052's "+ Tag"): free words of their own, beside
 * the fixed reading intentions. One row per book and tag; removed with the book.
 */
@Entity(
    tableName = "book_tags",
    primaryKeys = ["book_id", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tag")]
)
data class BookTagEntity(
    @ColumnInfo(name = "book_id") val bookId: String,
    val tag: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long
)

@Dao
interface BookTagDao {
    @Query("SELECT tag FROM book_tags WHERE book_id = :bookId ORDER BY created_at")
    fun observe(bookId: String): Flow<List<String>>

    /** Every tag in the library, most used first, for suggestions. */
    @Query("SELECT tag FROM book_tags GROUP BY tag ORDER BY COUNT(*) DESC, tag")
    fun observeAll(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(tag: BookTagEntity)

    @Query("DELETE FROM book_tags WHERE book_id = :bookId AND tag = :tag")
    suspend fun remove(bookId: String, tag: String)
}
