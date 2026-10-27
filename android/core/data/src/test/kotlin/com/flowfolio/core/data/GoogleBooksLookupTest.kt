package com.flowfolio.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookDetails
import com.flowfolio.core.model.BookStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Google Books lookups against canned responses, and how they combine with Open Library's (ADR-0036). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class GoogleBooksLookupTest {
    private val book = Book("b", "Moby-Dick", 10, BookStorage.Copied("/x"), 0, 0, null, null, false)

    private val volume = """{"items":[{"volumeInfo":{"title":"Moby-Dick","subtitle":"or, The Whale",
        "authors":["Herman Melville"],"publisher":"Penguin","publishedDate":"2003-02-01",
        "description":"<p>A <b>whale</b> of a tale.</p><p>Second &amp; last.</p>",
        "industryIdentifiers":[{"type":"ISBN_10","identifier":"0142437247"},
            {"type":"ISBN_13","identifier":"9780142437247"}],
        "pageCount":720,"categories":["Fiction / Classics"],"averageRating":4.0,"ratingsCount":212,
        "language":"en"}}]}"""

    private val asked = mutableListOf<String>()

    private fun lookup(responses: (String) -> String?) = GoogleBooksLookup({ url ->
        asked += url
        responses(url)
    }, UnconfinedTestDispatcher(), language = "en")

    @Test
    fun aVolumeGivesEveryDetailGoogleKnows() = runTest {
        val found = lookup { volume }.find(book)!!
        assertEquals("Herman Melville", found.author)
        val d = found.details
        assertEquals("or, The Whale", d.subtitle)
        assertEquals("Penguin", d.publisher)
        assertEquals("9780142437247", d.isbn)
        assertEquals("A whale of a tale.\n\nSecond & last.", d.description)
        assertEquals("English", d.language)
        assertEquals("2003-02-01", d.released)
        assertEquals(listOf("Fiction", "Classics"), d.subjects)
        assertEquals(720, d.printPages)
        assertEquals(4.0f, d.rating)
        assertEquals(212, d.ratingsCount)
    }

    @Test
    fun byIsbnWhenKnownElseOnlyTheTitleAndAuthorAreSent() = runTest {
        lookup { volume }.find(book.copy(details = BookDetails(isbn = "978-0-14-243724-7")))
        assertTrue(asked.single().contains("isbn%3A9780142437247"))
        asked.clear()
        lookup { volume }.find(book.copy(author = "Melville"))
        assertTrue(asked.first().contains("intitle"))
        assertTrue(asked.all { it.startsWith("https://www.googleapis.com/books/v1/volumes?") && "/x" !in it })
    }

    @Test
    fun aTitleSearchThatFindsAnotherBookFindsNothing() = runTest {
        val other = volume.replace("\"title\":\"Moby-Dick\"", "\"title\":\"Whales of the World\"")
        assertNull(lookup { other }.find(book))
        assertTrue(GoogleBooksParse.sameTitle("Moby Dick", "Moby-Dick; or, The Whale"))
        assertFalse(GoogleBooksParse.sameTitle("Moby Dick", "Whales"))
    }

    @Test
    fun combinedFillsGapsPoolsSubjectsAndKeepsTheBetterRating() {
        val open = Found(
            "Herman Melville",
            BookDetails(description = "Short.", subjects = listOf("Whaling"), rating = 3.9f, ratingsCount = 50)
        )
        val google = Found(
            "H. Melville",
            BookDetails(
                publisher = "Penguin",
                description = "A much longer publisher description.",
                subjects = listOf("Classics", "whaling"),
                rating = 4.0f,
                ratingsCount = 212
            )
        )
        val both = CombinedLookup.combine(open, google)!!
        assertEquals("Herman Melville", both.author)
        assertEquals("Penguin", both.details.publisher)
        assertEquals("A much longer publisher description.", both.details.description)
        assertEquals(listOf("Classics", "whaling"), both.details.subjects)
        assertEquals(4.0f, both.details.rating)
        assertEquals(212, both.details.ratingsCount)
        assertEquals(open, CombinedLookup.combine(open, null))
        assertNull(CombinedLookup.combine(null, null))
    }
}
