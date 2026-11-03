package com.flowfolio.core.data

import com.flowfolio.core.database.ReadingSessionDao
import com.flowfolio.core.database.ReadingSessionEntity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Time spent reading each book. */
interface ReadingRepository {
    /** Records that [bookId] was read from [startedAt] to [endedAt] (epoch milliseconds). */
    suspend fun record(bookId: String, startedAt: Long, endedAt: Long)

    /** Total time read in [bookId], in milliseconds. */
    fun observeReadMillis(bookId: String): Flow<Long>

    /** Total time read in the whole library, in milliseconds. */
    fun observeLibraryReadMillis(): Flow<Long>

    /** Total time read per book in the library, in milliseconds. */
    fun observeReadMillisByBook(): Flow<Map<String, Long>> = flowOf(emptyMap())
}

class DefaultReadingRepository @Inject constructor(
    private val dao: ReadingSessionDao,
    @IoDispatcher private val io: CoroutineDispatcher
) : ReadingRepository {
    override suspend fun record(bookId: String, startedAt: Long, endedAt: Long) {
        if (endedAt <= startedAt) return
        withContext(io) {
            // A book no longer in the library (deleted while open, or an id that was never in it) keeps no time.
            try {
                dao.insert(ReadingSessionEntity(bookId = bookId, startedAt = startedAt, endedAt = endedAt))
            } catch (_: android.database.sqlite.SQLiteConstraintException) {
                Unit
            }
        }
    }

    override fun observeReadMillis(bookId: String): Flow<Long> = dao.observeTotal(bookId)

    override fun observeLibraryReadMillis(): Flow<Long> = dao.observeLibraryTotal()

    override fun observeReadMillisByBook(): Flow<Map<String, Long>> =
        dao.observeTotalsByBook().map { list -> list.associate { it.bookId to it.total } }
}
