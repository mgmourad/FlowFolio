package com.flowfolio.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookDetails
import com.flowfolio.core.model.BookStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Open Library lookups against canned responses (org.json needs Robolectric). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class OpenLibraryLookupTest {
    private val book = Book("b", "University Physics", 10, BookStorage.Copied("/x"), 0, 0, null, null, false)

    private val responses = mapOf(
        "https://openlibrary.org/isbn/9781938168277.json" to """
            {"title":"University Physics","subtitle":"Volume 1","publishers":["OpenStax"],"publish_date":"2016",
             "edition_name":"1st","languages":[{"key":"/languages/eng"}],
             "works":[{"key":"/works/OL1W"}],"authors":[{"key":"/authors/OL2A"}]}""",
        "https://openlibrary.org/works/OL1W.json" to
            """{"description":{"type":"/type/text","value":"Calculus-based physics."}}""",
        "https://openlibrary.org/authors/OL2A.json" to """{"name":"Samuel J. Ling","bio":"Physicist."}""",
        "https://openlibrary.org/search.json?limit=1&title=University+Physics&fields=$FIELDS&language=eng" to
            """{"docs":[{"key":"/works/OL1W","author_name":["Ling"],"first_publish_year":2016,"language":["eng"],
             "number_of_pages_median":584,"ratings_average":4.5,"ratings_count":12,
             "subject":["Physics","Accessible book","great_books_of_the_western_world","Mechanics"]}]}"""
    )
    private val asked = mutableListOf<String>()

    private companion object {
        const val FIELDS = "key,title,subtitle,author_name,author_key,publisher,isbn,language,first_publish_year," +
            "subject,number_of_pages_median,ratings_average,ratings_count"
    }
    private val lookup = OpenLibraryLookup({ url ->
        asked += url
        responses[url]
    }, UnconfinedTestDispatcher(), language = "eng")

    @Test
    fun byIsbnReadsEditionWorkAndAuthor() = runTest {
        val found = lookup.find(book.copy(details = BookDetails(isbn = "978-1-938168-27-7")))!!
        assertEquals("Samuel J. Ling", found.author)
        assertEquals("Volume 1", found.details.subtitle)
        assertEquals("OpenStax", found.details.publisher)
        assertEquals("English", found.details.language)
        assertEquals("Calculus-based physics.", found.details.description)
        assertEquals("Physicist.", found.details.authorBio)
    }

    @Test
    fun withoutIsbnSearchesByTitleAndSendsNothingElse() = runTest {
        val found = lookup.find(book)!!
        assertEquals("Ling", found.author)
        assertEquals("2016", found.details.released)
        assertEquals(584, found.details.printPages)
        assertEquals(4.5f, found.details.rating)
        assertEquals(12, found.details.ratingsCount)
        // Catalogue terms and tags are left out.
        assertEquals(listOf("Physics", "Mechanics"), found.details.subjects)
        assertTrue(asked.all { it.startsWith("https://openlibrary.org/") })
        assertTrue(asked.none { "/x" in it })
    }

    @Test
    fun aDescriptionInAnotherLanguageIsLeftOutOnAnEnglishPhone() {
        val spanish =
            "Física universitaria es una colección de tres volúmenes que cumple con los requisitos de alcance " +
                "y secuencia para los cursos de física de dos y tres semestres con base en el cálculo."
        val english =
            "University Physics is a three-volume collection that meets the scope and sequence requirements " +
                "for two- and three-semester calculus-based physics courses."
        assertTrue(!OpenLibraryParse.readable(spanish, "eng"))
        assertTrue(OpenLibraryParse.readable(english, "eng"))
        assertTrue(OpenLibraryParse.readable(spanish, "spa"))
    }

    @Test
    fun nothingFoundIsNull() = runTest {
        assertNull(lookup.find(book.copy(title = "Unknown")))
    }

    @Test
    fun foundDetailsOnlyFillEmptyFields() {
        val typed = book.copy(author = "Me", details = BookDetails(subtitle = "Mine"))
        val (author, details) = typed.mergedWith(Found("Them", BookDetails(subtitle = "Theirs", publisher = "P")))
        assertEquals("Me", author)
        assertEquals("Mine", details.subtitle)
        assertEquals("P", details.publisher)
    }
}
