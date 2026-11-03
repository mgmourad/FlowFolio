package com.flowfolio.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * An English definition looked up in the reader with Define (schema v17, M3T3). Kept when a book is removed.
 */
@Entity(
    tableName = "definitions",
    indices = [Index("word"), Index("book_id")]
)
data class DefinitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String? = null,
    val definition: String,
    @ColumnInfo(name = "book_id") val bookId: String? = null,
    val page: Int? = null,
    val locator: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L
)

@Dao
interface DefinitionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(definition: DefinitionEntity): Long

    @Query("SELECT * FROM definitions ORDER BY created_at DESC")
    fun observeAll(): Flow<List<DefinitionEntity>>

    @Query("SELECT * FROM definitions WHERE book_id = :bookId ORDER BY created_at DESC")
    fun observeForBook(bookId: String): Flow<List<DefinitionEntity>>

    @Query("SELECT COUNT(DISTINCT lower(word)) FROM definitions")
    fun observeWordCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM definitions WHERE book_id = :bookId")
    fun observeCount(bookId: String): Flow<Int>

    @Query("DELETE FROM definitions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM definitions WHERE book_id = :bookId")
    suspend fun deleteForBook(bookId: String)

    @Query("DELETE FROM definitions")
    suspend fun clear()
}
