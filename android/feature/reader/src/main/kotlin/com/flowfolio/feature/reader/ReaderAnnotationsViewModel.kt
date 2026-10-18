package com.flowfolio.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.AnnotationRepository
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.HighlightColor
import com.flowfolio.core.model.Passage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The open book's highlights and notes, and the book itself (for sharing a quote). */
@HiltViewModel
class ReaderAnnotationsViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val annotations: AnnotationRepository,
    library: LibraryRepository
) : ViewModel() {
    private val bookId: String = checkNotNull(saved[ReaderViewModel.ARG_BOOK_ID]) { "reader needs a bookId" }

    val all: StateFlow<List<Annotation>> =
        annotations.observe(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptyList())

    val book: StateFlow<Book?> =
        library.observeBook(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    fun highlight(passage: Passage, color: HighlightColor, note: String? = null) = viewModelScope.launch {
        annotations.add(bookId, passage, color, note)
    }

    fun recolour(id: Long, color: HighlightColor) = viewModelScope.launch { annotations.setColor(id, color) }

    fun setNote(id: Long, note: String?) = viewModelScope.launch { annotations.setNote(id, note) }

    fun remove(id: Long) = viewModelScope.launch { annotations.remove(id) }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
