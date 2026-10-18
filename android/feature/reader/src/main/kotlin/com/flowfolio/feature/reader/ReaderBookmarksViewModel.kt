package com.flowfolio.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.BookmarkRepository
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.BookmarkColor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The open book's ribbon bookmarks: adding one in a chosen colour, recolouring and removing it. */
@HiltViewModel
class ReaderBookmarksViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val repository: BookmarkRepository
) : ViewModel() {
    private val bookId: String = checkNotNull(saved[ReaderViewModel.ARG_BOOK_ID]) { "reader needs a bookId" }

    val bookmarks: StateFlow<List<Bookmark>> = repository.observe(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Marks [page] in [color], or recolours its ribbon when it already has one. */
    fun mark(page: Int, color: BookmarkColor) = viewModelScope.launch {
        val existing = bookmarks.value.firstOrNull { it.page == page }
        if (existing == null) repository.add(bookId, page, color) else repository.setColor(existing.id, color)
    }

    /** Takes the ribbon off [page]. */
    fun unmark(page: Int) = viewModelScope.launch {
        bookmarks.value.filter { it.page == page }.forEach { repository.remove(it.id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
