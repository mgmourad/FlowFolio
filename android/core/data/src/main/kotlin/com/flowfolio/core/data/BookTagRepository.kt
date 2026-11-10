package com.flowfolio.core.data

import com.flowfolio.core.database.BookTagDao
import com.flowfolio.core.database.BookTagEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** A book's own tags (Room v22): free words beside the reading intentions. */
interface BookTagRepository {
    fun tagsOf(bookId: String): Flow<List<String>>

    /** Every tag used in the library, most used first. */
    fun allTags(): Flow<List<String>>

    /** Adds [tag] (trimmed; blank and duplicate tags are ignored). */
    suspend fun add(bookId: String, tag: String)

    suspend fun remove(bookId: String, tag: String)
}

class DefaultBookTagRepository @Inject constructor(private val dao: BookTagDao) : BookTagRepository {
    override fun tagsOf(bookId: String): Flow<List<String>> = dao.observe(bookId)

    override fun allTags(): Flow<List<String>> = dao.observeAll()

    override suspend fun add(bookId: String, tag: String) {
        val clean = normalize(tag) ?: return
        dao.add(BookTagEntity(bookId, clean, System.currentTimeMillis()))
    }

    override suspend fun remove(bookId: String, tag: String) = dao.remove(bookId, tag)

    companion object {
        /** One line, single spaces, at most [MAX_LENGTH] characters; null when nothing is left. */
        fun normalize(tag: String): String? =
            tag.trim().replace(Regex("\\s+"), " ").take(MAX_LENGTH).trim().takeIf { it.isNotEmpty() }

        const val MAX_LENGTH = 32
    }
}
