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

/** A highlight and its note. Removing the book removes its annotations. Added in schema v6. */
@Entity(
    tableName = "annotations",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["book_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("book_id")]
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: String,
    val page: Int,
    /** The page's printed label when highlighted, if the PDF has one. */
    @ColumnInfo(name = "page_label") val pageLabel: String?,
    @ColumnInfo(name = "char_start") val charStart: Int,
    @ColumnInfo(name = "char_end") val charEnd: Int,
    val text: String,
    /** "left,top,right,bottom" fractions of the page, one rectangle per line, separated by ";". */
    val rects: String,
    /** A HighlightColor name; unknown names read as the default colour. */
    val color: String,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE book_id = :bookId ORDER BY page ASC, char_start ASC")
    fun observe(bookId: String): Flow<List<AnnotationEntity>>

    /** Every annotation, newest first (the Annotations tab). */
    @Query("SELECT * FROM annotations ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<AnnotationEntity>>

    @Insert
    suspend fun insert(annotation: AnnotationEntity): Long

    @Query("UPDATE annotations SET color = :color, updated_at = :at WHERE id = :id")
    suspend fun setColor(id: Long, color: String, at: Long)

    @Query("UPDATE annotations SET note = :note, updated_at = :at WHERE id = :id")
    suspend fun setNote(id: Long, note: String?, at: Long)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Per-book reading choices that are not part of a book's details. */
@Dao
interface BookReadingDao {
    /** Pages run right to left (Arabic, Hebrew, Persian, Urdu books, manga). Added in schema v6. */
    @Query("UPDATE books SET right_to_left = :on WHERE id = :id")
    suspend fun setRightToLeft(id: String, on: Boolean)

    /** The book's printed page numbers (PageLabels runs). Added in schema v7. */
    @Query("UPDATE books SET page_labels = :labels WHERE id = :id")
    suspend fun setPageLabels(id: String, labels: String)

    /** An EPUB's position: the locator, and the page and page count at the size it was read (for progress). */
    @Query("UPDATE books SET last_locator = :locator, last_page = :page, page_count = :pageCount WHERE id = :id")
    suspend fun updateEpubProgress(id: String, locator: String, page: Int, pageCount: Int)
}
