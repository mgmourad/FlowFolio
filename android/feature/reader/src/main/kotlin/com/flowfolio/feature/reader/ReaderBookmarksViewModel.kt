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

    /**
     * Marks [page] in [color] ([locator]: an EPUB's text at its top, ADR-0040), or recolours the [existing] ribbon on
     * the pages in view.
     */
    fun mark(page: Int, color: BookmarkColor, locator: String? = null, existing: Bookmark? = null) =
        viewModelScope.launch {
            if (existing ==
                null
            ) {
                repository.add(bookId, page, color, locator)
            } else {
                repository.setColor(existing.id, color)
            }
        }

    /** Takes these ribbons off. */
    fun remove(ids: Collection<Long>) = viewModelScope.launch { ids.forEach { repository.remove(it) } }

    /** EPUB ribbons placed on other pages by a new layout: saved with them (ADR-0040). */
    fun syncPages(stored: List<Bookmark>, placed: List<Bookmark>) = viewModelScope.launch {
        val before = stored.associate { it.id to it.page }
        val moved = placed.filter { it.locator != null && before[it.id] != it.page }.associate { it.id to it.page }
        if (moved.isNotEmpty()) repository.setPages(moved)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
