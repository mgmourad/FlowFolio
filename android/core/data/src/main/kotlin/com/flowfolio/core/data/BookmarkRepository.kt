package com.flowfolio.core.data

import com.flowfolio.core.database.BookmarkDao
import com.flowfolio.core.database.BookmarkEntity
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.BookmarkColor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Ribbon bookmarks. */
interface BookmarkRepository {
    /** A book's bookmarks, by page. */
    fun observe(bookId: String): Flow<List<Bookmark>>

    /** Every bookmark, newest first. */
    fun observeAll(): Flow<List<Bookmark>>

    suspend fun add(bookId: String, page: Int, color: BookmarkColor): Long

    suspend fun setColor(bookmarkId: Long, color: BookmarkColor)

    suspend fun remove(bookmarkId: Long)
}

class DefaultBookmarkRepository @Inject constructor(
    private val dao: BookmarkDao,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher
) : BookmarkRepository {
    override fun observe(bookId: String): Flow<List<Bookmark>> = dao.observe(bookId).map { it.map(::toModel) }

    override fun observeAll(): Flow<List<Bookmark>> = dao.observeAll().map { it.map(::toModel) }

    override suspend fun add(bookId: String, page: Int, color: BookmarkColor): Long = withContext(io) {
        dao.insert(BookmarkEntity(bookId = bookId, page = page, color = color.name, createdAt = clock.now()))
    }

    override suspend fun setColor(bookmarkId: Long, color: BookmarkColor) =
        withContext(io) { dao.setColor(bookmarkId, color.name) }

    override suspend fun remove(bookmarkId: Long) = withContext(io) { dao.delete(bookmarkId) }

    private fun toModel(e: BookmarkEntity) = Bookmark(
        id = e.id,
        bookId = e.bookId,
        page = e.page,
        color = BookmarkColor.entries.firstOrNull { it.name == e.color } ?: BookmarkColor.SUB_ITEM,
        createdAtMillis = e.createdAt
    )
}
