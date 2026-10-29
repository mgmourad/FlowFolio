package com.flowfolio.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.BookmarkRepository
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.ReaderRepository
import com.flowfolio.core.data.ReadingRepository
import com.flowfolio.core.data.RelinkResult
import com.flowfolio.core.data.ShelfRepository
import com.flowfolio.core.data.flow.ReadingViews
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookDetails
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.core.model.Shelf
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Contents tab: top-level outline entries, or why there are none. */
sealed interface DetailsContents {
    data object Loading : DetailsContents

    data class Ready(val entries: List<OutlineEntry>) : DetailsContents

    /** The PDF has no outline, is locked, or could not be opened. */
    data object None : DetailsContents
}

data class DetailsUiState(
    val loading: Boolean = true,
    /** Null once loaded means the book was removed. */
    val book: Book? = null,
    val availability: Availability = Availability.AVAILABLE,
    val shelves: List<Shelf> = emptyList(),
    val contents: DetailsContents = DetailsContents.Loading,
    /** Time spent reading this book (milliseconds), its ribbons, and the other books by the same author. */
    val readMillis: Long = 0,
    val ribbons: Int = 0,
    val sameAuthor: List<Book> = emptyList()
) {
    val bookShelves: List<Shelf> get() = book?.let { b -> shelves.filter { b.id in it.bookIds } }.orEmpty()
}

/** A book's details page: the book itself, the shelves it is on and its contents. */
@Suppress("LongParameterList") // one injected source per part of the page
@HiltViewModel
class DetailsViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val library: LibraryRepository,
    private val reader: ReaderRepository,
    shelves: ShelfRepository,
    reading: ReadingRepository,
    bookmarks: BookmarkRepository,
    private val views: ReadingViews
) : ViewModel() {
    val bookId: String = checkNotNull(saved[ARG_BOOK_ID]) { "details needs a book id" }

    private val contents = MutableStateFlow<DetailsContents>(DetailsContents.Loading)
    private val availability = MutableStateFlow(Availability.AVAILABLE)

    val uiState: StateFlow<DetailsUiState> =
        combine(
            library.observeBook(bookId),
            shelves.observeShelves(),
            contents,
            availability,
            extras(reading, bookmarks)
        ) { book, s, c, a, x ->
            DetailsUiState(
                loading = false,
                book = book,
                availability = a,
                shelves = s,
                contents = c,
                readMillis = x.readMillis,
                ribbons = x.ribbons,
                sameAuthor = book?.author?.let { author -> x.books.filter { it.id != bookId && it.author == author } }
                    .orEmpty()
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DetailsUiState())

    init {
        viewModelScope.launch { load() }
    }

    /** Remembers [page] as the reading position, then calls [then] (to open the reader there). */
    fun startAt(page: Int, then: () -> Unit) = viewModelScope.launch {
        if (page >= 0) reader.saveProgress(bookId, page)
        then()
    }

    fun markOpened() = viewModelScope.launch { library.markOpened(bookId) }

    /** Points the book at a file the reader located again, then re-reads its availability and contents. */
    fun relink(uri: String) = viewModelScope.launch {
        if (library.relink(bookId, uri) == RelinkResult.Relinked) load()
    }

    private class Extras(val readMillis: Long, val ribbons: Int, val books: List<Book>)

    private fun extras(reading: ReadingRepository, bookmarks: BookmarkRepository) = combine(
        reading.observeReadMillis(bookId),
        views.bookmarks(bookmarks.observe(bookId)),
        library.observeBooks(LibrarySort.RECENT)
    ) { ms, marks, books -> Extras(ms, marks.size, books) }

    /** Saves Edit details. */
    fun saveDetails(title: String, author: String?, details: BookDetails, then: () -> Unit) = viewModelScope.launch {
        if (title.isNotBlank()) library.updateDetails(bookId, title, author, details)
        then()
    }

    private suspend fun load() {
        val book = library.observeBook(bookId).first() ?: return
        availability.value = library.availability(book)
        if (book.passwordProtected || availability.value == Availability.MISSING) {
            contents.value = DetailsContents.None
            return
        }
        val entries = if (book.format == BookFormat.EPUB) {
            // EPUB pages depend on the text size: chapters are listed without page numbers (pageIndex -1).
            reader.epubContents(bookId).map { OutlineEntry(depth = 0, pageIndex = -1, title = it) }
        } else {
            runCatching {
                reader.open(bookId).document.use { doc -> doc.outline().filter { it.depth == 0 && it.pageIndex >= 0 } }
            }.getOrNull().orEmpty()
        }
        contents.value = entries.takeIf { it.isNotEmpty() }?.let { DetailsContents.Ready(it) } ?: DetailsContents.None
    }

    companion object {
        const val ARG_BOOK_ID = "bookId"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
