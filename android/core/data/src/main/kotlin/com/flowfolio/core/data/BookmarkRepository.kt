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

    /** A ribbon on [page]; in an EPUB, [locator] is the text at its top (ADR-0040). */
    suspend fun add(bookId: String, page: Int, color: BookmarkColor, locator: String? = null): Long

    suspend fun setColor(bookmarkId: Long, color: BookmarkColor)

    suspend fun remove(bookmarkId: Long)

    /** Where EPUB ribbons now are after a re-layout, by id. */
    suspend fun setPages(pages: Map<Long, Int>) = Unit
}

class DefaultBookmarkRepository @Inject constructor(
    private val dao: BookmarkDao,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher
) : BookmarkRepository {
    override fun observe(bookId: String): Flow<List<Bookmark>> = dao.observe(bookId).map { it.map(::toModel) }

    override fun observeAll(): Flow<List<Bookmark>> = dao.observeAll().map { it.map(::toModel) }

    override suspend fun add(bookId: String, page: Int, color: BookmarkColor, locator: String?): Long =
        withContext(io) {
            dao.insert(
                BookmarkEntity(
                    bookId = bookId,
                    page = page,
                    color = color.name,
                    createdAt = clock.now(),
                    locator = locator
                )
            )
        }

    override suspend fun setPages(pages: Map<Long, Int>) =
        withContext(io) { pages.forEach { (id, page) -> dao.setPage(id, page) } }

    override suspend fun setColor(bookmarkId: Long, color: BookmarkColor) =
        withContext(io) { dao.setColor(bookmarkId, color.name) }

    override suspend fun remove(bookmarkId: Long) = withContext(io) { dao.delete(bookmarkId) }

    private fun toModel(e: BookmarkEntity) = Bookmark(
        id = e.id,
        bookId = e.bookId,
        page = e.page,
        color = BookmarkColor.entries.firstOrNull { it.name == e.color.replace(OLD_GREY, BookmarkColor.GREEN.name) }
            ?: BookmarkColor.SUB_ITEM,
        createdAtMillis = e.createdAt,
        locator = e.locator
    )
}

/** The grey choice of the first reader build, replaced by green (owner, 2026-10-18). */
private const val OLD_GREY = "HINT"
