package com.flowfolio.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.DictionaryRepository
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.ReaderRepository
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.DictionaryWord
import com.flowfolio.core.model.LibrarySort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One book's group on the Dictionary tab (or general words when [book] is null). */
data class DictionaryBookGroup(val book: Book?, val words: List<DictionaryWord>) {
    val key: String get() = book?.id ?: GENERAL_KEY

    companion object {
        const val GENERAL_KEY = "_general_"
    }
}

data class DictionaryUiState(
    val loading: Boolean = true,
    val query: String = "",
    val groups: List<DictionaryBookGroup> = emptyList(),
    val totalWords: Int = 0,
    val bookCount: Int = 0,
    val book: Book? = null
)

/**
 * Backing ViewModel for the Dictionary tab (owner, M3T3): merges saved translations (`translations`) and English
 * Wiktionary definitions (`definitions`), groups them by book, filters them with fuzzy search, and opens a word's
 * place in its book.
 */
@HiltViewModel
class DictionaryViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val dictionary: DictionaryRepository,
    library: LibraryRepository,
    private val reader: ReaderRepository
) : ViewModel() {
    private val onlyBook: String? = saved[ARG_BOOK_ID]
    private val query = MutableStateFlow("")

    val uiState: StateFlow<DictionaryUiState> = combine(
        dictionary.observeWords(),
        library.observeBooks(LibrarySort.RECENT),
        query
    ) { allWords, books, q ->
        buildState(allWords, books, q, onlyBook)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), DictionaryUiState())

    fun onQuery(text: String) {
        query.value = text
    }

    /** Jumps to [word]'s page in its book (when known) and invokes [then] to open the reader. */
    fun open(word: DictionaryWord, booksById: Map<String, Book>, then: (Book) -> Unit) {
        val book = word.bookId?.let(booksById::get) ?: return
        viewModelScope.launch {
            val page = word.page
            if (page != null && page >= 0) {
                reader.saveProgress(book.id, page)
            }
            then(book)
        }
    }

    companion object {
        const val ARG_BOOK_ID = "bookId"
        private const val STOP_MS = 5_000L

        internal fun buildState(
            allWords: List<DictionaryWord>,
            books: List<Book>,
            q: String,
            onlyBook: String? = null
        ): DictionaryUiState {
            val byId = books.associateBy { it.id }
            val scoped = if (onlyBook == null) allWords else allWords.filter { it.bookId == onlyBook }
            val filtered = if (q.isBlank()) {
                scoped
            } else {
                scoped.mapNotNull { w ->
                    val bookTitle = w.bookId?.let { byId[it]?.title }
                    val score = maxOf(
                        Fuzzy.score(q, w.word),
                        Fuzzy.score(q, w.translation),
                        Fuzzy.score(q, w.definition),
                        Fuzzy.score(q, bookTitle)
                    )
                    if (score > 0) w to score else null
                }.sortedByDescending { it.second }.map { it.first }
            }
            val groups = filtered
                .groupBy { it.bookId?.let(byId::get) }
                .entries
                .sortedWith(
                    compareByDescending<Map.Entry<Book?, List<DictionaryWord>>> { it.key != null }
                        .thenByDescending { entry -> entry.value.maxOfOrNull { it.updatedAtMillis } ?: 0L }
                )
                .map { (book, list) -> DictionaryBookGroup(book, list) }
            val distinctBooks = scoped.mapNotNull { it.bookId?.takeIf(byId::containsKey) }.distinct().size
            return DictionaryUiState(
                loading = false,
                query = q,
                groups = groups,
                totalWords = scoped.size,
                bookCount = distinctBooks,
                book = onlyBook?.let(byId::get)
            )
        }
    }
}
