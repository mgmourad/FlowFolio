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

/** A stretch of reading: the reader open and in use. Removing the book removes its sessions. Added in schema v5. */
@Entity(
    tableName = "reading_sessions",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["book_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("book_id")]
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long
)

@Dao
interface ReadingSessionDao {
    @Insert
    suspend fun insert(session: ReadingSessionEntity): Long

    /** Total time read in [bookId], in milliseconds (0 when never read). */
    @Query("SELECT COALESCE(SUM(ended_at - started_at), 0) FROM reading_sessions WHERE book_id = :bookId")
    fun observeTotal(bookId: String): Flow<Long>

    /** Total time read in the books still in the library, in milliseconds. */
    @Query(
        "SELECT COALESCE(SUM(s.ended_at - s.started_at), 0) FROM reading_sessions s " +
            "JOIN books b ON b.id = s.book_id"
    )
    fun observeLibraryTotal(): Flow<Long>
}
