package com.flowfolio.feature.library

import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookDetails
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.BookmarkColor
import com.flowfolio.core.model.DictionaryWord
import com.flowfolio.core.model.HighlightColor
import com.flowfolio.core.model.Shelf
import com.flowfolio.core.model.Translation
import com.flowfolio.core.model.WordDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    @Test
    fun `fuzzy ranks starts, word starts, insides, typos and letters in order`() {
        val text = "University Physics"
        val start = Fuzzy.score("univ", text)
        val word = Fuzzy.score("phys", text)
        val inside = Fuzzy.score("ysics", text)
        val typo = Fuzzy.score("phisics", text)
        val letters = Fuzzy.score("unvrs", text)
        assertTrue(start > word && word > inside && inside > typo && typo > letters && letters > 0)
        assertEquals(0, Fuzzy.score("chemistry", text))
        assertEquals(0, Fuzzy.score("", text))
    }

    @Test
    fun `case and accents never matter`() {
        assertEquals(Fuzzy.STARTS, Fuzzy.score("ECLAIR", "Éclair au chocolat"))
    }

    @Test
    fun `letters scattered through a long text are not a match`() {
        assertEquals(0, Fuzzy.score("xyz", "x" + "a".repeat(40) + "y" + "b".repeat(40) + "z"))
    }

    @Test
    fun `one edit apart`() {
        assertTrue(Fuzzy.near("physics", "phisics"))
        assertTrue(Fuzzy.near("physics", "physic"))
        assertTrue(Fuzzy.near("physics", "physicss"))
        assertTrue(!Fuzzy.near("physics", "phasycs"))
    }

    @Test
    fun `everything is searched, favourites in a group of their own`() {
        val physics = book("p", "University Physics", favorite = true)
        val moby = book(
            "m",
            "Moby-Dick",
            author = "Herman Melville",
            details = BookDetails(series = "Sea Classics", subjects = listOf("Whaling", "Adventure"))
        )
        val sources = SearchViewModel.Sources(
            books = listOf(physics, moby),
            shelves = listOf(Shelf(1, "Physics shelf", listOf("p"))),
            annotations = listOf(note("m", "Call me Ishmael"), note("p", "Newton's laws of motion")),
            bookmarks = listOf(Bookmark(1, "p", 41, BookmarkColor.TITLE, 0)),
            words = listOf(Translation(1, "physique", "physics", "fr", "en", "p", 0))
        )
        val r = SearchViewModel.search("physics", sources)
        assertEquals(physics, (r.groups.getValue(SearchGroup.FAVORITES).single() as SearchHit.OfBook).book)
        assertTrue(SearchGroup.BOOKS !in r.groups)
        assertEquals(1, r.groups.getValue(SearchGroup.SHELVES).size)
        assertEquals(1, r.groups.getValue(SearchGroup.BOOKMARKS).size)
        assertEquals(1, r.groups.getValue(SearchGroup.DICTIONARY).size)
        assertTrue(SearchGroup.ANNOTATIONS !in r.groups)

        assertEquals(1, SearchViewModel.search("ishmael", sources).groups.getValue(SearchGroup.ANNOTATIONS).size)
        assertEquals(
            moby,
            (
                SearchViewModel.search(
                    "melvile",
                    sources
                ).groups.getValue(SearchGroup.BOOKS).single() as SearchHit.OfBook
                ).book
        )
        assertEquals(1, SearchViewModel.search("42", sources).groups.getValue(SearchGroup.BOOKMARKS).size)
        assertEquals(1, SearchViewModel.search("whaling", sources).groups.getValue(SearchGroup.BOOKS).size)
        assertEquals(1, SearchViewModel.search("sea classics", sources).groups.getValue(SearchGroup.BOOKS).size)
        assertTrue(SearchViewModel.search("  ", sources).empty)
    }

    @Test
    fun `definitions are searched and dictionary groups by book`() {
        val moby = book("m", "Moby-Dick")
        val def = WordDefinition(1, "ephemeral", "adj.", "Lasting for a very short time.", "m", 12, null, 100L)
        val sources = SearchViewModel.Sources(
            books = listOf(moby),
            shelves = emptyList(),
            annotations = emptyList(),
            bookmarks = emptyList(),
            words = emptyList(),
            definitions = listOf(def)
        )
        val hit = SearchViewModel.search("ephemeral", sources)
            .groups.getValue(SearchGroup.DICTIONARY).single() as SearchHit.OfDefinition
        assertEquals("ephemeral", hit.definition.word)
        assertEquals(moby, hit.book)

        val dictWords = listOf(
            DictionaryWord(
                key = "m:ephemeral",
                word = "ephemeral",
                partOfSpeech = "adj.",
                definition = "Lasting for a very short time.",
                translation = "éphémère",
                fromLang = "en",
                toLang = "fr",
                bookId = "m",
                page = 12,
                locator = null,
                updatedAtMillis = 100L
            )
        )
        val state = DictionaryViewModel.buildState(dictWords, listOf(moby), "short time")
        assertEquals(1, state.groups.size)
        assertEquals(moby, state.groups.single().book)
        assertEquals("ephemeral", state.groups.single().words.single().word)
    }

    private fun book(
        id: String,
        title: String,
        author: String? = null,
        favorite: Boolean = false,
        details: BookDetails = BookDetails()
    ) = Book(
        id = id,
        title = title,
        pageCount = 100,
        storage = BookStorage.Copied("/$id.pdf"),
        sizeBytes = 1,
        addedAtMillis = 0,
        lastOpenedAtMillis = null,
        coverPath = null,
        passwordProtected = false,
        author = author,
        details = details,
        favorite = favorite
    )

    private fun note(bookId: String, text: String) = Annotation(
        id = text.hashCode().toLong(),
        bookId = bookId,
        page = 3,
        label = null,
        start = 0,
        end = text.length,
        text = text,
        rects = emptyList(),
        color = HighlightColor.entries.first(),
        note = null,
        createdAtMillis = 0,
        updatedAtMillis = 0
    )
}
