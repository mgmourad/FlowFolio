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
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** An EPUB highlight's `chapter:offset` (ADR-0040; char_start and char_end are chapter offsets). Added in v14. */
    val locator: String? = null,
    /**
     * A voice note recorded against this highlight (owner, M5). Added in v20.
     *
     * [audioPath] is the file inside the app's own `voice/` directory, [audioMillis] how long it runs, and
     * [audioWave] the levels drawn as the playback bar's waveform -- sampled while recording, because reading them
     * back out of the file afterwards would mean decoding the whole thing to draw one small picture.
     */
    @ColumnInfo(name = "audio_path") val audioPath: String? = null,
    @ColumnInfo(name = "audio_millis") val audioMillis: Long? = null,
    @ColumnInfo(name = "audio_wave") val audioWave: String? = null
)

@Dao
@Suppress("TooManyFunctions") // one query per operation; grouping them would only hide them
interface AnnotationDao {
    @Query("UPDATE annotations SET audio_path = :path, audio_millis = :millis, audio_wave = :wave WHERE id = :id")
    suspend fun setVoice(id: Long, path: String?, millis: Long?, wave: String?)

    @Query("SELECT * FROM annotations WHERE id = :id")
    suspend fun byId(id: Long): AnnotationEntity?

    /** How many of [bookId]'s highlights belong to its Dynamic Flow ([flow], those with a locator) or to its PDF. */
    @Query("SELECT COUNT(*) FROM annotations WHERE book_id = :bookId AND (locator IS NOT NULL) = :flow")
    suspend fun countInView(bookId: String, flow: Boolean): Int

    /** How many highlights [bookId] has (owner, M3T2). */
    @Query("SELECT COUNT(*) FROM annotations WHERE book_id = :bookId")
    fun count(bookId: String): Flow<Int>

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

    /** Where an EPUB highlight now starts, after a re-layout (not a change by the reader: updated_at stays). */
    @Query("UPDATE annotations SET page = :page WHERE id = :id")
    suspend fun setPage(id: Long, page: Int)

    /** An EPUB highlight found again in a new edition of its text (a re-converted Dynamic Flow, ADR-0045). */
    @Query("UPDATE annotations SET locator = :locator, char_start = :start, char_end = :end WHERE id = :id")
    suspend fun relocate(id: Long, locator: String, start: Int, end: Int)
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

    /** An EPUB's position set from outside the reader (a highlight or ribbon opened from the library). */
    @Query("UPDATE books SET last_locator = :locator, last_page = :page WHERE id = :id")
    suspend fun updateEpubPosition(id: String, locator: String, page: Int)

    /** A PDF's view: true Dynamic Flow, false the PDF (ADR-0045). */
    @Query("UPDATE books SET flow_mode = :on WHERE id = :id")
    suspend fun setFlowMode(id: String, on: Boolean)

    /** Where the reader is in a PDF's Dynamic Flow, and the PDF page that is ([page], 0-based). */
    @Query("UPDATE books SET flow_locator = :locator, last_page = :page WHERE id = :id")
    suspend fun updateFlowProgress(id: String, locator: String, page: Int)

    /** Where the reader is in a PDF's Dynamic Flow, without touching the PDF's page (a switch about to happen). */
    @Query("UPDATE books SET flow_locator = :locator WHERE id = :id")
    suspend fun setFlowLocator(id: String, locator: String)
}
