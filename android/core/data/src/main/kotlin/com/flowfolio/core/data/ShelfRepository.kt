package com.flowfolio.core.data

import com.flowfolio.core.database.ShelfDao
import com.flowfolio.core.database.ShelfEntity
import com.flowfolio.core.model.Shelf
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/** The reader's shelves (collections of books). */
interface ShelfRepository {
    /** Shelves in the order they were made, each with its books (most recently added first). */
    fun observeShelves(): Flow<List<Shelf>>

    /** Makes a shelf and returns its id. [name] is trimmed and must not be blank. */
    suspend fun create(name: String): Long

    suspend fun rename(shelfId: Long, name: String)

    /** Removes the shelf; its books stay in the library. */
    suspend fun delete(shelfId: Long)

    /** Puts [bookId] on exactly [shelfIds], taking it off any others. */
    suspend fun setShelves(bookId: String, shelfIds: Set<Long>)
}

class DefaultShelfRepository @Inject constructor(
    private val dao: ShelfDao,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher
) : ShelfRepository {
    override fun observeShelves(): Flow<List<Shelf>> =
        combine(dao.observeShelves(), dao.observeMemberships()) { shelves, rows ->
            val byShelf = rows.groupBy({ it.shelfId }, { it.bookId })
            shelves.map { Shelf(it.id, it.name, byShelf[it.id].orEmpty()) }
        }

    override suspend fun create(name: String): Long = withContext(io) {
        require(name.isNotBlank()) { "shelf name is blank" }
        dao.insert(ShelfEntity(name = name.trim(), createdAt = clock.now()))
    }

    override suspend fun rename(shelfId: Long, name: String) = withContext(io) {
        require(name.isNotBlank()) { "shelf name is blank" }
        dao.rename(shelfId, name.trim())
    }

    override suspend fun delete(shelfId: Long) = withContext(io) { dao.delete(shelfId) }

    override suspend fun setShelves(bookId: String, shelfIds: Set<Long>) =
        withContext(io) { dao.setShelves(bookId, shelfIds, clock.now()) }
}
