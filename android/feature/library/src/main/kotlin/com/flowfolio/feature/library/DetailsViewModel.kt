package com.flowfolio.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.ReaderRepository
import com.flowfolio.core.data.RelinkResult
import com.flowfolio.core.data.ShelfRepository
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
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
    val contents: DetailsContents = DetailsContents.Loading
) {
    val bookShelves: List<Shelf> get() = book?.let { b -> shelves.filter { b.id in it.bookIds } }.orEmpty()
}

/** A book's details page: the book itself, the shelves it is on and its contents. */
@HiltViewModel
class DetailsViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val library: LibraryRepository,
    private val reader: ReaderRepository,
    shelves: ShelfRepository
) : ViewModel() {
    val bookId: String = checkNotNull(saved[ARG_BOOK_ID]) { "details needs a book id" }

    private val contents = MutableStateFlow<DetailsContents>(DetailsContents.Loading)
    private val availability = MutableStateFlow(Availability.AVAILABLE)

    val uiState: StateFlow<DetailsUiState> =
        combine(library.observeBook(bookId), shelves.observeShelves(), contents, availability) { book, s, c, a ->
            DetailsUiState(loading = false, book = book, availability = a, shelves = s, contents = c)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DetailsUiState())

    init {
        viewModelScope.launch { load() }
    }

    /** Remembers [page] as the reading position, then calls [then] (to open the reader there). */
    fun startAt(page: Int, then: () -> Unit) = viewModelScope.launch {
        reader.saveProgress(bookId, page)
        then()
    }

    fun markOpened() = viewModelScope.launch { library.markOpened(bookId) }

    /** Points the book at a file the reader located again, then re-reads its availability and contents. */
    fun relink(uri: String) = viewModelScope.launch {
        if (library.relink(bookId, uri) == RelinkResult.Relinked) load()
    }

    private suspend fun load() {
        val book = library.observeBook(bookId).first() ?: return
        availability.value = library.availability(book)
        if (book.passwordProtected || availability.value == Availability.MISSING) {
            contents.value = DetailsContents.None
            return
        }
        contents.value = runCatching {
            reader.open(bookId).document.use { doc -> doc.outline().filter { it.depth == 0 && it.pageIndex >= 0 } }
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { DetailsContents.Ready(it) } ?: DetailsContents.None
    }

    companion object {
        const val ARG_BOOK_ID = "bookId"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
