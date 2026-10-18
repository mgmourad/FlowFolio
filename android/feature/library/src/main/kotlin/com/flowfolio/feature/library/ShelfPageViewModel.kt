package com.flowfolio.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.RelinkResult
import com.flowfolio.core.data.ShelfRepository
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.Shelf
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShelfPageUiState(
    val loading: Boolean = true,
    /** The shelf shown, or null for the books on no shelf ("All books" when there are no shelves). */
    val shelf: Shelf? = null,
    val noShelves: Boolean = false,
    val items: List<LibraryItem> = emptyList(),
    val shelves: List<Shelf> = emptyList()
)

/** One shelf's books, as tiles or a list, with renaming, removing and locating books. */
@HiltViewModel
class ShelfPageViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val library: LibraryRepository,
    shelfRepo: ShelfRepository
) : ViewModel() {
    private val shelfId: Long = saved[ARG_SHELF_ID] ?: LOOSE
    private val availability = MutableStateFlow<Map<String, Availability>>(emptyMap())

    val uiState: StateFlow<ShelfPageUiState> =
        combine(library.observeBooks(LibrarySort.RECENT), shelfRepo.observeShelves(), availability) {
                books,
                shelves,
                a
            ->
            val shelf = shelves.firstOrNull { it.id == shelfId }
            val byId = books.associateBy { it.id }
            val list = when {
                shelf != null -> shelf.bookIds.mapNotNull(byId::get)

                else -> {
                    val shelved = shelves.flatMapTo(HashSet()) { it.bookIds }
                    books.filterNot { it.id in shelved }
                }
            }
            ShelfPageUiState(
                loading = false,
                shelf = shelf,
                noShelves = shelves.isEmpty(),
                items = list.map { LibraryItem(it, a[it.id] ?: Availability.AVAILABLE) },
                shelves = shelves
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ShelfPageUiState())

    init {
        viewModelScope.launch {
            uiState.collect { state -> checkLinked(state.items.map { it.book }) }
        }
    }

    fun rename(bookId: String, title: String) = viewModelScope.launch {
        if (title.isNotBlank()) library.rename(bookId, title)
    }

    fun delete(bookId: String) = viewModelScope.launch { library.delete(bookId) }

    fun relink(bookId: String, uri: String) = viewModelScope.launch {
        if (library.relink(bookId, uri) == RelinkResult.Relinked) {
            availability.update { it + (bookId to Availability.AVAILABLE) }
        }
    }

    /** Linked books can go missing outside the app. */
    private suspend fun checkLinked(books: List<Book>) {
        val linked = books.filter { it.storage is BookStorage.Linked && it.id !in availability.value }
        if (linked.isEmpty()) return
        val found = linked.associate { it.id to library.availability(it) }
        availability.update { it + found }
    }

    companion object {
        const val ARG_SHELF_ID = "shelfId"

        /** The row of books on no shelf (all books when there are no shelves). */
        const val LOOSE = -1L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
