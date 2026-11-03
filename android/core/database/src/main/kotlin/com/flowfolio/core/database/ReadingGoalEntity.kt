package com.flowfolio.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A reading goal (schema v17, M3T3): [scope] is `""` for the whole library, or a book's `id` for that book.
 */
@Entity(tableName = "reading_goals")
data class ReadingGoalEntity(
    @PrimaryKey val scope: String,
    @ColumnInfo(name = "target_minutes") val targetMinutes: Int,
    @ColumnInfo(defaultValue = "DAY") val period: String = "DAY",
    @ColumnInfo(name = "remind_enabled", defaultValue = "0") val remindEnabled: Boolean = false,
    @ColumnInfo(name = "remind_hour", defaultValue = "20") val remindHour: Int = 20,
    @ColumnInfo(name = "remind_minute", defaultValue = "0") val remindMinute: Int = 0,
    @ColumnInfo(name = "remind_days", defaultValue = "127") val remindDaysMask: Int = 127,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0L
)

@Dao
interface ReadingGoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: ReadingGoalEntity)

    @Query("SELECT * FROM reading_goals WHERE scope = :scope")
    fun observe(scope: String): Flow<ReadingGoalEntity?>

    @Query("SELECT * FROM reading_goals")
    fun observeAll(): Flow<List<ReadingGoalEntity>>

    @Query("SELECT * FROM reading_goals")
    suspend fun getAll(): List<ReadingGoalEntity>

    @Query("DELETE FROM reading_goals WHERE scope = :scope")
    suspend fun delete(scope: String)
}
