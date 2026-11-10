package com.flowfolio.core.data

import com.flowfolio.core.database.BookTextDao
import com.flowfolio.core.database.BookTextEntity
import com.flowfolio.core.model.LineSpacing
import com.flowfolio.core.model.PageMargins
import com.flowfolio.core.model.ReaderText
import com.flowfolio.core.model.ReadingFont
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * A book's own text settings (ADR-0052, schema v21). The reader's Aa panel changes them for the book open; a book
 * that has none follows Settings → Text, the defaults.
 */
interface BookTextRepository {
    /** The book's own text settings, or null while it follows the defaults. */
    fun observe(bookId: String): Flow<ReaderText?> = flowOf(null)

    suspend fun set(bookId: String, text: ReaderText) = Unit

    /** Back to the defaults. */
    suspend fun clear(bookId: String) = Unit

    /** A repository with no per-book settings (tests, previews). */
    object None : BookTextRepository
}

/** The text a book is shown in: its own, or the defaults from [settings]. */
fun BookTextRepository.effective(bookId: String, settings: Flow<com.flowfolio.core.model.UserSettings>) =
    combine(settings, observe(bookId)) { s, own -> s.copy(text = own ?: s.text) }

class DefaultBookTextRepository @Inject constructor(private val dao: BookTextDao) : BookTextRepository {
    override fun observe(bookId: String): Flow<ReaderText?> = dao.observe(bookId).map { it?.toModel() }

    override suspend fun set(bookId: String, text: ReaderText) = dao.upsert(text.toEntity(bookId))

    override suspend fun clear(bookId: String) = dao.delete(bookId)
}

private fun BookTextEntity.toModel() = ReaderText(
    font = ReadingFont.entries.firstOrNull { it.name == font } ?: ReaderText().font,
    sizeSp = sizeSp,
    lineSpacing = LineSpacing.entries.firstOrNull { it.name == lineSpacing } ?: ReaderText().lineSpacing,
    margins = PageMargins.entries.firstOrNull { it.name == margins } ?: ReaderText().margins,
    justify = justify,
    hyphenate = hyphenate
)

private fun ReaderText.toEntity(bookId: String) =
    BookTextEntity(bookId, font.name, sizeSp, lineSpacing.name, margins.name, justify, hyphenate)
