package com.flowfolio.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A translation in the reader's history. Added in schema v8. Kept when a book is removed (it is the reader's own). */
@Entity(tableName = "translations")
data class TranslationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val translated: String,
    @ColumnInfo(name = "from_lang") val from: String,
    @ColumnInfo(name = "to_lang") val to: String,
    @ColumnInfo(name = "book_id") val bookId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Dao
interface TranslationDao {
    @Insert
    suspend fun insert(translation: TranslationEntity): Long

    @Query("SELECT * FROM translations ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TranslationEntity>>

    /** How many different words and phrases were translated (case aside): the dictionary's size. */
    @Query("SELECT COUNT(DISTINCT lower(source)) FROM translations")
    fun observeWordCount(): Flow<Int>

    /** How many of a book's translations there are (owner, M3T2). */
    @Query("SELECT COUNT(*) FROM translations WHERE book_id = :bookId")
    fun observeCount(bookId: String): Flow<Int>

    @Query("DELETE FROM translations")
    suspend fun clear()
}
