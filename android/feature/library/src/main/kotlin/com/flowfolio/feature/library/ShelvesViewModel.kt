package com.flowfolio.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.ShelfRepository
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.Shelf
import com.flowfolio.core.model.ShelfColor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A shelf with its books resolved (most recently shelved first), for the Shelves screen. */
data class ShelfCard(val shelf: Shelf, val books: List<Book>)

data class ShelvesUiState(
    val loading: Boolean = true,
    val cards: List<ShelfCard> = emptyList(),
    /** Every book in the library, for a shelf's "Add books". */
    val books: List<Book> = emptyList()
)

/** Shelves: listing them, and making, renaming and removing them, and choosing a book's shelves. */
@HiltViewModel
class ShelvesViewModel @Inject constructor(private val shelves: ShelfRepository, library: LibraryRepository) :
    ViewModel() {
    val uiState: StateFlow<ShelvesUiState> =
        combine(shelves.observeShelves(), library.observeBooks(LibrarySort.RECENT)) { list, books ->
            val byId = books.associateBy { it.id }
            ShelvesUiState(false, list.map { s -> ShelfCard(s, s.bookIds.mapNotNull(byId::get)) }, books)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ShelvesUiState())

    /** Makes a shelf; when [bookId] is given the book goes on it straight away (the "New shelf" in the picker). */
    fun create(name: String, bookId: String? = null, color: ShelfColor? = null) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val id = shelves.create(name, color)
        if (bookId != null) {
            val current = uiState.value.cards.filter { bookId in it.shelf.bookIds }.map { it.shelf.id }.toSet()
            shelves.setShelves(bookId, current + id)
        }
    }

    fun rename(shelfId: Long, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) shelves.rename(shelfId, name)
    }

    fun setColor(shelfId: Long, color: ShelfColor?) = viewModelScope.launch { shelves.setColor(shelfId, color) }

    fun delete(shelfId: Long) = viewModelScope.launch { shelves.delete(shelfId) }

    fun setShelves(bookId: String, shelfIds: Set<Long>) = viewModelScope.launch { shelves.setShelves(bookId, shelfIds) }

    /** Puts [bookIds] on [shelfId] ("Add books"), in front of what is there. */
    fun addBooks(shelfId: Long, bookIds: List<String>) = viewModelScope.launch {
        if (bookIds.isNotEmpty()) shelves.addBooks(shelfId, bookIds)
    }

    /** Puts every one of [bookIds] on each of [shelfIds] (select mode's "Add to shelf"); takes none off a shelf. */
    fun addToShelves(bookIds: List<String>, shelfIds: Set<Long>) = viewModelScope.launch {
        shelfIds.forEach { shelves.addBooks(it, bookIds) }
    }

    /** Makes a shelf and puts [bookIds] on it. */
    fun createWith(name: String, color: ShelfColor?, bookIds: List<String>) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        shelves.addBooks(shelves.create(name, color), bookIds)
    }

    /** Stands [bookIds] on [shelfId] in this order (drag and drop). */
    fun setOrder(shelfId: Long, bookIds: List<String>) = viewModelScope.launch { shelves.setOrder(shelfId, bookIds) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
