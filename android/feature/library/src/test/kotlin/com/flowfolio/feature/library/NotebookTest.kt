package com.flowfolio.feature.library

import com.flowfolio.core.data.AllMarks
import com.flowfolio.core.data.MarkKind
import com.flowfolio.core.data.countByBook
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.BookmarkColor
import com.flowfolio.core.model.DictionaryWord
import com.flowfolio.core.model.HighlightColor
import com.flowfolio.core.model.VoiceNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Notebook of ADR-0052: one count for every screen, the landing's filters and order, and the exports. */
class NotebookTest {
    @Suppress("LongParameterList")
    private fun note(
        id: Long,
        book: String,
        page: Int,
        text: String = "t$id",
        note: String? = null,
        voice: Boolean = false,
        at: Long = id
    ) = Annotation(
        id = id,
        bookId = book,
        page = page,
        label = null,
        start = 0,
        end = 1,
        text = text,
        rects = emptyList(),
        color = HighlightColor.YELLOW,
        note = note,
        createdAtMillis = at,
        updatedAtMillis = at,
        voice = if (voice) VoiceNote("/v$id.m4a", 3_000) else null
    )

    private fun ribbon(id: Long, book: String, page: Int) = Bookmark(id, book, page, BookmarkColor.GREEN, id)

    private fun word(key: String, book: String?) = DictionaryWord(key, key, book, page = 1, definition = "d")

    private val marks = AllMarks(
        annotations = listOf(note(1, "a", 3), note(2, "a", 1, note = "mine"), note(3, "b", 2, voice = true)),
        bookmarks = listOf(ribbon(10, "a", 5), ribbon(11, "a", 6)),
        words = listOf(word("tuple", "a"), word("loose", null))
    )

    @Test
    fun marksAreCountedByKindAndBook() {
        val counts = countByBook(marks.annotations, marks.bookmarks, marks.words)
        val a = counts.getValue("a")
        assertEquals(1, a.highlights)
        assertEquals(1, a.notes)
        assertEquals(2, a.bookmarks)
        assertEquals(1, a.words)
        assertEquals(0, a.voiceMarks)
        assertEquals(1, counts.getValue("b").voiceMarks)
        // A word saved outside any book belongs to no book's count.
        assertEquals(setOf("a", "b"), counts.keys)
    }

    @Test
    fun theLandingFiltersByKindAndSorts() {
        val books = listOf(book("a", "Algebra"), book("b", "Biology"), book("c", "Chemistry"))
        val all = NotebookViewModel.build(marks, books, null, NotebookSort.TITLE, "")
        assertEquals(listOf("a", "b"), all.books.map { it.book.id })
        assertEquals(6, all.totals.total)
        val voices = NotebookViewModel.build(marks, books, MarkKind.VOICE, NotebookSort.TITLE, "")
        assertEquals(listOf("b"), voices.books.map { it.book.id })
        val most = NotebookViewModel.build(marks, books, null, NotebookSort.MOST, "")
        assertEquals("a", most.books.first().book.id)
        val searched = NotebookViewModel.build(marks, books, null, NotebookSort.TITLE, "mine")
        assertEquals(listOf("a"), searched.books.map { it.book.id })
    }

    @Test
    fun aBooksEntriesSortByPageNewestOrKind() {
        val entries = NotebookViewModel.entries("a", marks)
        assertEquals(5, entries.size)
        val byPage = entries.sortedBy { it.page }
        assertEquals(1, byPage.first().page)
        assertTrue(entries.any { it.kind == MarkKind.NOTE })
    }

    @Test
    fun csvQuotesItsCells() {
        val m = AllMarks(annotations = listOf(note(1, "a", 0, text = "He said \"hi\"")))
        val csv = notebookCsv(listOf(book("a", "Algebra")), m)
        assertTrue(csv.lines()[1].contains("\"He said \"\"hi\"\"\""))
        assertTrue(notebookMarkdown(listOf(book("a", "Algebra")), m).contains("## Algebra"))
    }
}
