package com.flowfolio.ui.library

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Immutable state rendered by [LibraryScreen]. */
data class LibraryUiState(val books: List<BookSummary> = emptyList()) {
    val isEmpty: Boolean get() = books.isEmpty()
}

data class BookSummary(val id: String, val title: String)

/**
 * Phase 0 placeholder: exposes an empty library. In Phase 1 it depends on a library
 * repository (domain layer) and reacts to import events.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
}
