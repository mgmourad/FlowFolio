package com.flowfolio.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.AnnotationRepository
import com.flowfolio.core.data.BookmarkRepository
import com.flowfolio.core.data.DictionaryRepository
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.ShelfRepository
import com.flowfolio.core.data.TranslationRepository
import com.flowfolio.core.data.flow.ReadingViews
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.Shelf
import com.flowfolio.core.model.Translation
import com.flowfolio.core.model.WordDefinition
import com.flowfolio.core.model.pageLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** One thing found by the library's search, in its group, with the score it was found at. */
sealed interface SearchHit {
    val score: Int

    data class OfBook(val book: Book, override val score: Int) : SearchHit

    data class OfShelf(val shelf: Shelf, override val score: Int) : SearchHit

    data class OfAnnotation(val annotation: Annotation, val book: Book, override val score: Int) : SearchHit

    data class OfBookmark(val bookmark: Bookmark, val book: Book, override val score: Int) : SearchHit

    data class OfWord(val translation: Translation, val book: Book?, override val score: Int) : SearchHit

    data class OfDefinition(val definition: WordDefinition, val book: Book?, override val score: Int) : SearchHit
}

/** The search's groups, in the order they are shown. */
enum class SearchGroup { FAVORITES, BOOKS, SHELVES, ANNOTATIONS, BOOKMARKS, DICTIONARY }

/** What a query found, group by group, best first; empty groups are left out. */
data class SearchResults(val query: String = "", val groups: Map<SearchGroup, List<SearchHit>> = emptyMap()) {
    val empty get() = groups.isEmpty()
}

/**
 * Searches everything (owner, M3T2): books by title and author (favourites first, in a group of their own), shelves
 * by name, highlights and notes by their words, ribbons by their book and page, and the dictionary by either side of
 * a translation or definition (M3T3). It updates as the reader types, a moment after the last key so typing stays
 * smooth.
 */
@OptIn(FlowPreview::class)
@Suppress("LongParameterList")
@HiltViewModel
class SearchViewModel @Inject constructor(
    library: LibraryRepository,
    shelves: ShelfRepository,
    annotations: AnnotationRepository,
    bookmarks: BookmarkRepository,
    translations: TranslationRepository,
    dictionary: DictionaryRepository,
    views: ReadingViews
) : ViewModel() {
    private val query = MutableStateFlow("")

    private val dictSources = combine(
        translations.history(DICTIONARY_LIMIT),
        dictionary.observeDefinitions()
    ) { words, defs -> words to defs }

    private val sources = combine(
        library.observeBooks(LibrarySort.TITLE),
        shelves.observeShelves(),
        views.annotations(annotations.observeAll()),
        views.bookmarks(bookmarks.observeAll()),
        dictSources
    ) { books, s, notes, ribbons, (words, defs) -> Sources(books, s, notes, ribbons, words, defs) }

    val results: StateFlow<SearchResults> = combine(query.debounce(DEBOUNCE_MS), sources) { q, s -> search(q, s) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), SearchResults())

    fun onQuery(text: String) {
        query.value = text
    }

    /** The library's books, shelves, highlights, ribbons, translations and definitions: what a query matches. */
    internal data class Sources(
        val books: List<Book>,
        val shelves: List<Shelf>,
        val annotations: List<Annotation>,
        val bookmarks: List<Bookmark>,
        val words: List<Translation>,
        val definitions: List<WordDefinition> = emptyList()
    )

    internal companion object {
        const val DEBOUNCE_MS = 120L
        const val STOP_MS = 5_000L
        const val DICTIONARY_LIMIT = 2_000

        fun search(q: String, s: Sources): SearchResults {
            // Nothing until two letters: one matched too much to be useful (ADR-0052).
            if (q.trim().length < SEARCH_MINIMUM) return SearchResults(q)
            val byId = s.books.associateBy { it.id }
            val books = s.books.mapNotNull { b ->
                val subjectScore = b.details.subjects.maxOfOrNull { Fuzzy.score(q, it) } ?: 0
                maxOf(
                    Fuzzy.score(q, b.title),
                    Fuzzy.score(q, b.author),
                    Fuzzy.score(q, b.details.series),
                    subjectScore
                ).takeIf {
                    it > 0
                }?.let { SearchHit.OfBook(b, it) }
            }
            val wordHits = s.words.distinctBy { Fuzzy.fold(it.source) to it.to }.mapNotNull { w ->
                maxOf(Fuzzy.score(q, w.source), Fuzzy.score(q, w.translated)).takeIf { it > 0 }
                    ?.let { SearchHit.OfWord(w, w.bookId?.let(byId::get), it) }
            }
            val defHits = s.definitions.distinctBy { Fuzzy.fold(it.word) }.mapNotNull { d ->
                maxOf(Fuzzy.score(q, d.word), Fuzzy.score(q, d.definition)).takeIf { it > 0 }
                    ?.let { SearchHit.OfDefinition(d, d.bookId?.let(byId::get), it) }
            }
            val groups = mapOf(
                SearchGroup.FAVORITES to books.filter { it.book.favorite },
                SearchGroup.BOOKS to books.filterNot { it.book.favorite },
                SearchGroup.SHELVES to
                    s.shelves.hits { Fuzzy.score(q, it.name) }.map { (x, n) -> SearchHit.OfShelf(x, n) },
                SearchGroup.ANNOTATIONS to s.annotations.mapNotNull { a ->
                    val book = byId[a.bookId] ?: return@mapNotNull null
                    maxOf(Fuzzy.score(q, a.text), Fuzzy.score(q, a.note)).takeIf { it > 0 }
                        ?.let { SearchHit.OfAnnotation(a, book, it) }
                },
                SearchGroup.BOOKMARKS to s.bookmarks.mapNotNull { r ->
                    val book = byId[r.bookId] ?: return@mapNotNull null
                    // A ribbon has no words of its own: its book's title, or its page number typed exactly.
                    val page = if (book.pageLabel(r.page).equals(q.trim(), ignoreCase = true)) Fuzzy.STARTS else 0
                    maxOf(page, Fuzzy.score(q, book.title)).takeIf { it > 0 }?.let { SearchHit.OfBookmark(r, book, it) }
                },
                SearchGroup.DICTIONARY to (wordHits + defHits)
            )
            return SearchResults(
                q,
                groups.mapValues { (_, hits) -> hits.sortedByDescending { it.score } }.filterValues { it.isNotEmpty() }
            )
        }

        private fun <T> List<T>.hits(score: (T) -> Int): List<Pair<T, Int>> =
            mapNotNull { x -> score(x).takeIf { it > 0 }?.let { x to it } }
    }
}
