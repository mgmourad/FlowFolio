package com.flowfolio.core.data

import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookDetails
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** What an online lookup found about a book; null fields were not found. */
data class Found(val author: String? = null, val details: BookDetails = BookDetails()) {
    val isEmpty: Boolean get() = author == null && details == BookDetails()
}

/** Book details from the internet (ADR-0017). */
interface BookLookup {
    /** Looks [book] up by its ISBN, else its title and author. Null when nothing was found or the network failed. */
    suspend fun find(book: Book): Found?
}

/** Fetches a URL's body as text; replaceable in tests. */
fun interface HttpGet {
    @Throws(IOException::class)
    fun get(url: String): String?
}

/** Plain HTTPS GET with timeouts, no cookies; null for anything but 200. */
class UrlConnectionGet @Inject constructor() : HttpGet {
    override fun get(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText().take(MAX_BYTES) }
            } else {
                null
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val MAX_BYTES = 2_000_000
        const val USER_AGENT = "FlowFolio (Android; https://github.com/mgmourad/FlowFolio)"
    }
}

/**
 * Open Library (openlibrary.org, ADR-0017): the edition by ISBN, or the best search match by title and author, then
 * its work (description) and first author (name, bio). Sends only the ISBN, or the title and author.
 */
class OpenLibraryLookup(
    private val http: HttpGet,
    private val io: CoroutineDispatcher,
    /** The phone's language as Open Library writes it ("eng"). */
    private val language: String
) : BookLookup {
    @Inject
    constructor(http: HttpGet, @IoDispatcher io: CoroutineDispatcher) :
        this(http, io, runCatching { Locale.getDefault().isO3Language }.getOrDefault("").ifBlank { "eng" })

    override suspend fun find(book: Book): Found? = withContext(io) {
        runCatching {
            val isbn = book.details.isbn?.filter { it.isDigit() || it == 'X' }?.takeIf { it.length in ISBN_LENGTHS }
            val edition = isbn?.let { json("$BASE/isbn/$it.json") }
            if (edition != null) fromEdition(edition, isbn) else fromSearch(book)
        }.getOrNull()?.takeIf { !it.isEmpty }
    }

    private fun fromEdition(edition: JSONObject, isbn: String): Found {
        val work = edition.optJSONArray("works")?.optJSONObject(0)?.optString("key")?.let { json("$BASE$it.json") }
        val authorKey = edition.optJSONArray("authors")?.optJSONObject(0)?.optString("key")
            ?: work?.optJSONArray("authors")?.optJSONObject(0)?.optJSONObject("author")?.optString("key")
        val author = authorKey?.let { json("$BASE$it.json") }
        return OpenLibraryParse.found(edition, work, author, isbn, language)
    }

    /**
     * The best match by title (and author), preferring an edition in the phone's language: a book found by title alone
     * can otherwise come back as a translation.
     */
    private fun fromSearch(book: Book): Found? {
        val query = buildString {
            append("$BASE/search.json?limit=1&title=").append(encode(book.title))
            book.author?.let { append("&author=").append(encode(it)) }
        }
        val doc = (json("$query&language=$language")?.firstDoc() ?: json(query)?.firstDoc()) ?: return null
        val work = doc.optString("key").takeIf { it.startsWith("/works/") }?.let { json("$BASE$it.json") }
        val author = doc.optJSONArray("author_key")?.optString(0)?.takeIf { it.isNotEmpty() }
            ?.let { json("$BASE/authors/$it.json") }
        return OpenLibraryParse.fromSearch(doc, work, author, language)
    }

    private fun JSONObject.firstDoc(): JSONObject? = optJSONArray("docs")?.optJSONObject(0)

    private fun json(url: String): JSONObject? = http.get(url)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        const val BASE = "https://openlibrary.org"

        /** ISBN-10 and ISBN-13. */
        val ISBN_LENGTHS = setOf(10, 13)
    }
}

/** Reading Open Library's JSON, kept apart from the network so it can be tested. */
object OpenLibraryParse {
    fun found(edition: JSONObject, work: JSONObject?, author: JSONObject?, isbn: String?, phone: String = "eng") =
        Found(
            author = author?.text("name"),
            details = BookDetails(
                subtitle = edition.text("subtitle"),
                edition = edition.text("edition_name"),
                publisher = edition.optJSONArray("publishers")?.firstText(),
                isbn = isbn,
                description = (work?.longText("description") ?: edition.longText("description"))?.takeIf {
                    readable(it, phone)
                },
                language = edition.optJSONArray("languages")?.optJSONObject(0)?.optString("key")?.let(::language),
                released = edition.text("publish_date"),
                authorBio = author?.longText("bio")
            )
        )

    fun fromSearch(doc: JSONObject, work: JSONObject?, author: JSONObject?, phone: String = "eng") = Found(
        author = author?.text("name") ?: doc.optJSONArray("author_name")?.firstText(),
        details = BookDetails(
            subtitle = doc.text("subtitle"),
            publisher = doc.optJSONArray("publisher")?.firstText(),
            isbn = doc.optJSONArray("isbn")?.firstText(),
            description = work?.longText("description")?.takeIf { readable(it, phone) },
            language = doc.optJSONArray("language")?.let { list ->
                // A work in several languages: the phone's, when it is one of them.
                val codes = List(list.length()) { list.optString(it) }
                (codes.firstOrNull { it == phone } ?: codes.firstOrNull())?.let { language("/languages/$it") }
            },
            released = doc.optInt("first_publish_year").takeIf { it > 0 }?.toString(),
            authorBio = author?.longText("bio")
        )
    )

    /**
     * Whether a description is in the phone's language. Open Library keeps one description per work, sometimes in a
     * translation's language; for English phones a text with almost no common English words is left out rather than
     * shown in another language. Other languages are taken as they come.
     */
    fun readable(text: String, phone: String): Boolean {
        val words = text.lowercase().split(NON_LETTERS).filter { it.isNotEmpty() }
        return phone != "eng" || words.size < MIN_WORDS || words.count { it in ENGLISH } >= words.size * ENGLISH_SHARE
    }

    private val NON_LETTERS = Regex("""[^\p{L}]+""")
    private val ENGLISH = setOf("the", "and", "of", "to", "a", "in", "is", "for", "that", "with", "this", "it", "are")
    private const val MIN_WORDS = 12
    private const val ENGLISH_SHARE = 0.08

    /** "/languages/eng" -> "English"; unknown codes as they are. */
    fun language(key: String): String {
        val code = key.substringAfterLast('/')
        return LANGUAGES[code] ?: code
    }

    private fun JSONObject.text(name: String): String? = optString(name).trim().takeIf { it.isNotEmpty() }

    /** Descriptions and bios are either a string or {"type": "/type/text", "value": "..."}. */
    private fun JSONObject.longText(name: String): String? =
        (optJSONObject(name)?.optString("value") ?: optString(name)).trim().takeIf { it.isNotEmpty() }

    private fun JSONArray.firstText(): String? = optString(0).trim().takeIf { it.isNotEmpty() }

    private val LANGUAGES = mapOf(
        "eng" to "English", "ara" to "Arabic", "fre" to "French", "ger" to "German", "spa" to "Spanish",
        "ita" to "Italian", "por" to "Portuguese", "rus" to "Russian", "chi" to "Chinese", "jpn" to "Japanese",
        "heb" to "Hebrew", "per" to "Persian", "tur" to "Turkish", "urd" to "Urdu", "hin" to "Hindi"
    )
}

/** [found] fills only what the reader has not filled in (never overwrites what they typed). */
fun Book.mergedWith(found: Found): Pair<String?, BookDetails> {
    val d = details
    val f = found.details
    return (author ?: found.author) to BookDetails(
        subtitle = d.subtitle ?: f.subtitle,
        edition = d.edition ?: f.edition,
        publisher = d.publisher ?: f.publisher,
        isbn = d.isbn ?: f.isbn,
        description = d.description ?: f.description,
        language = d.language ?: f.language,
        released = d.released ?: f.released,
        authorRole = d.authorRole ?: f.authorRole,
        authorBio = d.authorBio ?: f.authorBio,
        type = d.type ?: f.type
    )
}

/** Looks a book up and fills its empty details; true when anything was added. */
class BookEnricher @Inject constructor(private val lookup: BookLookup, private val library: LibraryRepository) {
    suspend fun enrich(book: Book): Boolean {
        val merged = lookup.find(book)?.let(book::mergedWith)
        val changed = merged != null && merged != (book.author to book.details)
        if (changed && merged != null) library.updateDetails(book.id, book.title, merged.first, merged.second)
        return changed
    }

    /**
     * After an import: looks the book up when the reader allowed it, and says whether to ask first (the lookup is off
     * until the reader agrees, ADR-0017).
     */
    suspend fun afterImport(book: Book, settings: SettingsRepository): Boolean {
        val s = settings.settings.first()
        if (s.onlineLookup) enrich(book)
        return !s.onlineLookup && !s.onlineLookupAsked
    }
}
