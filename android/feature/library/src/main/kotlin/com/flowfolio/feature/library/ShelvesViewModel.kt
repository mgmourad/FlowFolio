package com.flowfolio.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.ShelfRepository
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.Shelf
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A shelf with its books resolved (most recently shelved first), for the Shelves screen. */
data class ShelfCard(val shelf: Shelf, val books: List<Book>)

data class ShelvesUiState(val loading: Boolean = true, val cards: List<ShelfCard> = emptyList())

/** Shelves: listing them, and making, renaming and removing them, and choosing a book's shelves. */
@HiltViewModel
class ShelvesViewModel @Inject constructor(private val shelves: ShelfRepository, library: LibraryRepository) :
    ViewModel() {
    val uiState: StateFlow<ShelvesUiState> =
        combine(shelves.observeShelves(), library.observeBooks(LibrarySort.RECENT)) { list, books ->
            val byId = books.associateBy { it.id }
            ShelvesUiState(false, list.map { s -> ShelfCard(s, s.bookIds.mapNotNull(byId::get)) })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ShelvesUiState())

    /** Makes a shelf; when [bookId] is given the book goes on it straight away (the "New shelf" in the picker). */
    fun create(name: String, bookId: String? = null) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val id = shelves.create(name)
        if (bookId != null) {
            val current = uiState.value.cards.filter { bookId in it.shelf.bookIds }.map { it.shelf.id }.toSet()
            shelves.setShelves(bookId, current + id)
        }
    }

    fun rename(shelfId: Long, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) shelves.rename(shelfId, name)
    }

    fun delete(shelfId: Long) = viewModelScope.launch { shelves.delete(shelfId) }

    fun setShelves(bookId: String, shelfIds: Set<Long>) = viewModelScope.launch { shelves.setShelves(bookId, shelfIds) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
