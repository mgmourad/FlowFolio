package com.flowfolio.core.data

import com.flowfolio.core.database.AnnotationDao
import com.flowfolio.core.database.AnnotationEntity
import com.flowfolio.core.database.BookReadingDao
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.HighlightColor
import com.flowfolio.core.model.PageRect
import com.flowfolio.core.model.Passage
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Highlights and notes. */
interface AnnotationRepository {
    /** A book's annotations, in page order. */
    fun observe(bookId: String): Flow<List<Annotation>>

    /** Every annotation, most recently changed first. */
    fun observeAll(): Flow<List<Annotation>>

    /** How many highlights and notes [bookId] has (owner, M3T2). */
    fun observeAnnotationCount(bookId: String): Flow<Int> = flowOf(0)

    /** Saves a new highlight over [passage]. Returns its id. */
    suspend fun add(bookId: String, passage: Passage, color: HighlightColor, note: String? = null): Long

    suspend fun setColor(id: Long, color: HighlightColor)

    /** Sets or (blank) clears the note. */
    suspend fun setNote(id: Long, note: String?)

    suspend fun remove(id: Long)

    /** Where EPUB highlights now start after a re-layout, by id (not a change by the reader). */
    suspend fun setPages(pages: Map<Long, Int>) = Unit

    /** A highlight found again in a new edition of its book's text: its new [locator] and chapter offsets. */
    suspend fun relocate(id: Long, locator: String, start: Int, end: Int) = Unit

    /** Whether the book's pages run right to left. */
    suspend fun setRightToLeft(bookId: String, on: Boolean)
}

class DefaultAnnotationRepository @Inject constructor(
    private val dao: AnnotationDao,
    private val books: BookReadingDao,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher
) : AnnotationRepository {
    override fun observe(bookId: String): Flow<List<Annotation>> = dao.observe(bookId).map { it.map(::toModel) }

    override fun observeAll(): Flow<List<Annotation>> = dao.observeAll().map { it.map(::toModel) }

    override fun observeAnnotationCount(bookId: String): Flow<Int> = dao.count(bookId)

    override suspend fun add(bookId: String, passage: Passage, color: HighlightColor, note: String?): Long =
        withContext(io) {
            val now = clock.now()
            dao.insert(
                AnnotationEntity(
                    bookId = bookId,
                    page = passage.page,
                    pageLabel = passage.label,
                    charStart = passage.range.first,
                    charEnd = passage.range.last,
                    text = passage.text,
                    rects = encode(passage.rects),
                    color = color.name,
                    note = note?.trim()?.takeIf { it.isNotEmpty() },
                    createdAt = now,
                    updatedAt = now,
                    locator = passage.locator
                )
            )
        }

    override suspend fun setColor(id: Long, color: HighlightColor) =
        withContext(io) { dao.setColor(id, color.name, clock.now()) }

    override suspend fun setNote(id: Long, note: String?) =
        withContext(io) { dao.setNote(id, note?.trim()?.takeIf { it.isNotEmpty() }, clock.now()) }

    override suspend fun remove(id: Long) = withContext(io) { dao.delete(id) }

    override suspend fun relocate(id: Long, locator: String, start: Int, end: Int) =
        withContext(io) { dao.relocate(id, locator, start, end) }

    override suspend fun setPages(pages: Map<Long, Int>) =
        withContext(io) { pages.forEach { (id, page) -> dao.setPage(id, page) } }

    override suspend fun setRightToLeft(bookId: String, on: Boolean) =
        withContext(io) { books.setRightToLeft(bookId, on) }

    private fun toModel(e: AnnotationEntity) = Annotation(
        id = e.id,
        bookId = e.bookId,
        page = e.page,
        label = e.pageLabel,
        start = e.charStart,
        end = e.charEnd,
        text = e.text,
        rects = decode(e.rects),
        color = HighlightColor.entries.firstOrNull { it.name == e.color } ?: HighlightColor.YELLOW,
        note = e.note,
        createdAtMillis = e.createdAt,
        updatedAtMillis = e.updatedAt,
        locator = e.locator
    )
}

/** "l,t,r,b;l,t,r,b" with four decimals: about a tenth of a pixel on a phone, and compact. */
internal fun encode(rects: List<PageRect>): String = rects.joinToString(";") { r ->
    listOf(r.left, r.top, r.right, r.bottom).joinToString(",") { String.format(Locale.ROOT, "%.4f", it) }
}

internal fun decode(text: String): List<PageRect> = text.split(';').mapNotNull { part ->
    val v = part.split(',').mapNotNull { it.toFloatOrNull() }
    if (v.size == RECT_VALUES) PageRect(left = v[0], top = v[1], right = v[2], bottom = v.last()) else null
}

private const val RECT_VALUES = 4
