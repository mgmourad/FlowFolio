package com.flowfolio.core.data

import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookDetails
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Google Books (googleapis.com/books, ADR-0036): the volume by ISBN, else the best match by title and author. No key
 * and no account; sends only the ISBN, or the title and author. A title search whose result is another book is
 * dropped rather than shown.
 */
class GoogleBooksLookup(
    private val http: HttpGet,
    private val io: CoroutineDispatcher,
    /** The phone's language as Google writes it ("en"). */
    private val language: String
) : BookLookup {
    @Inject
    constructor(http: HttpGet, @IoDispatcher io: CoroutineDispatcher) :
        this(http, io, Locale.getDefault().language.ifBlank { "en" })

    override suspend fun find(book: Book): Found? = withContext(io) {
        runCatching {
            val isbn = book.details.isbn?.filter { it.isDigit() || it == 'X' }?.takeIf { it.length in ISBN_LENGTHS }
            val volume = isbn?.let { first("isbn:$it") } ?: byTitle(book)
            volume?.let { GoogleBooksParse.found(it) }
        }.getOrNull()?.takeIf { !it.isEmpty }
    }

    private fun byTitle(book: Book): JSONObject? {
        val q = buildString {
            append("intitle:\"").append(book.title).append('"')
            book.author?.let { append(" inauthor:\"").append(it).append('"') }
        }
        val volume = first(q, "&langRestrict=$language") ?: first(q)
        return volume?.takeIf {
            GoogleBooksParse.sameTitle(book.title, it.optJSONObject("volumeInfo")?.optString("title"))
        }
    }

    private fun first(query: String, extra: String = ""): JSONObject? {
        val url = "$BASE?q=${URLEncoder.encode(query, "UTF-8")}&maxResults=1&printType=books$extra"
        val body = http.get(url) ?: return null
        return runCatching { JSONObject(body) }.getOrNull()?.optJSONArray("items")?.optJSONObject(0)
    }

    private companion object {
        const val BASE = "https://www.googleapis.com/books/v1/volumes"
        val ISBN_LENGTHS = setOf(10, 13)
    }
}

/** Reading Google Books' JSON, kept apart from the network so it can be tested. */
object GoogleBooksParse {
    fun found(volume: JSONObject): Found {
        val info = volume.optJSONObject("volumeInfo") ?: JSONObject()
        return Found(
            author = info.optJSONArray("authors")?.texts()?.joinToString(", ")?.ifEmpty { null },
            details = BookDetails(
                subtitle = info.text("subtitle"),
                publisher = info.text("publisher"),
                isbn = isbn(info.optJSONArray("industryIdentifiers")),
                description = info.text("description")?.let(::plain),
                language = info.text("language")?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH) }
                    ?.ifEmpty { null },
                released = info.text("publishedDate"),
                // "Science / Physics / General": the parts, most general dropped when there are others.
                subjects = info.optJSONArray("categories")?.texts()?.flatMap { c ->
                    c.split(" / ").map { it.trim() }.filter { it.isNotEmpty() && it != "General" }
                }?.distinct().orEmpty(),
                printPages =
                    info.optInt("printedPageCount").takeIf { it > 0 } ?: info.optInt("pageCount").takeIf { it > 0 },
                series = volume.optJSONObject("volumeInfo")?.optJSONObject("seriesInfo")?.text("shortSeriesBookTitle"),
                rating = info.optDouble("averageRating").takeIf { !it.isNaN() && it > 0 }?.toFloat(),
                ratingsCount = info.optInt("ratingsCount").takeIf { it > 0 }
            )
        )
    }

    /** Whether a result's title is the book's, ignoring case, punctuation and a subtitle on either side. */
    fun sameTitle(asked: String, found: String?): Boolean {
        val a = normal(asked)
        val b = normal(found ?: return false)
        return a.isNotEmpty() && b.isNotEmpty() && (a.startsWith(b) || b.startsWith(a))
    }

    private fun normal(s: String) = s.lowercase().replace(NON_WORD, " ").trim().replace(SPACES, " ")

    /** ISBN-13 first, else ISBN-10. */
    private fun isbn(ids: JSONArray?): String? {
        ids ?: return null
        val all = List(ids.length()) { ids.optJSONObject(it) }.filterNotNull()
        return (
            all.firstOrNull { it.optString("type") == "ISBN_13" }
                ?: all.firstOrNull { it.optString("type") == "ISBN_10" }
            )
            ?.text("identifier")
    }

    /** Descriptions come as simple HTML: paragraphs become blank lines, other tags and entities plain text. */
    fun plain(html: String): String = html
        .replace(BREAKS, "\n")
        .replace(PARAGRAPH_END, "\n\n")
        .replace(TAGS, "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace(EXTRA_LINES, "\n\n")
        .trim()

    private fun JSONObject.text(name: String): String? = optString(name).trim().takeIf { it.isNotEmpty() }

    private fun JSONArray.texts(): List<String> = List(length()) { optString(it).trim() }.filter { it.isNotEmpty() }

    private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
    private val SPACES = Regex("""\s+""")
    private val BREAKS = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val PARAGRAPH_END = Regex("""</p>""", RegexOption.IGNORE_CASE)
    private val TAGS = Regex("""<[^>]+>""")
    private val EXTRA_LINES = Regex("""\n{3,}""")
}

/**
 * Both sources at once (ADR-0036): Open Library first, Google Books filling what it leaves empty. Either failing
 * leaves the other's answer; subjects are pooled, and the rating with more ratings behind it wins.
 */
class CombinedLookup @Inject constructor(
    private val openLibrary: OpenLibraryLookup,
    private val googleBooks: GoogleBooksLookup
) : BookLookup {
    override suspend fun find(book: Book): Found? = coroutineScope {
        val open = async { runCatching { openLibrary.find(book) }.getOrNull() }
        val google = async { runCatching { googleBooks.find(book) }.getOrNull() }
        combine(open.await(), google.await())
    }

    companion object {
        @Suppress("CyclomaticComplexMethod") // one fallback per field
        fun combine(first: Found?, second: Found?): Found? {
            if (first == null || second == null) return first ?: second
            val a = first.details
            val b = second.details
            val rated = listOf(a, b).filter { it.rating != null }.maxByOrNull { it.ratingsCount ?: 0 }
            return Found(
                author = first.author ?: second.author,
                details = BookDetails(
                    subtitle = a.subtitle ?: b.subtitle,
                    edition = a.edition ?: b.edition,
                    publisher = a.publisher ?: b.publisher,
                    isbn = a.isbn ?: b.isbn,
                    // The fuller description: Open Library's are often a line, Google's a publisher's blurb.
                    description = listOfNotNull(a.description, b.description).maxByOrNull { it.length },
                    language = a.language ?: b.language,
                    released = a.released ?: b.released,
                    authorRole = a.authorRole ?: b.authorRole,
                    authorBio = a.authorBio ?: b.authorBio,
                    type = a.type ?: b.type,
                    subjects = (b.subjects + a.subjects).distinctBy { it.lowercase() }.take(MAX_SUBJECTS),
                    printPages = a.printPages ?: b.printPages,
                    series = a.series ?: b.series,
                    rating = rated?.rating,
                    ratingsCount = rated?.ratingsCount
                )
            )
        }

        private const val MAX_SUBJECTS = 8
    }
}
