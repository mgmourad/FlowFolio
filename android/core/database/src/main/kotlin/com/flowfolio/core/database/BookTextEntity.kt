package com.flowfolio.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A book's own text settings (schema v21, ADR-0052): the reader's Aa panel sets them for one book, and Settings → Text
 * holds the defaults every other book uses. No row means the book follows the defaults. Removed with its book.
 */
@Entity(
    tableName = "book_text",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BookTextEntity(
    @PrimaryKey @androidx.room.ColumnInfo(name = "book_id") val bookId: String,
    val font: String,
    @androidx.room.ColumnInfo(name = "size_sp") val sizeSp: Int,
    @androidx.room.ColumnInfo(name = "line_spacing") val lineSpacing: String,
    val margins: String,
    val justify: Boolean,
    val hyphenate: Boolean
)

@Dao
interface BookTextDao {
    @Query("SELECT * FROM book_text WHERE book_id = :bookId")
    fun observe(bookId: String): Flow<BookTextEntity?>

    @Upsert
    suspend fun upsert(text: BookTextEntity)

    @Query("DELETE FROM book_text WHERE book_id = :bookId")
    suspend fun delete(bookId: String)
}
