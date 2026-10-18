package com.flowfolio.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.AnnotationRepository
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.ReaderRepository
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.HighlightColor
import com.flowfolio.core.model.LibrarySort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A book on the Annotations tab: how many highlights and notes it has, in which colours, and when last changed. */
data class AnnotatedBook(
    val book: Book,
    val highlights: Int,
    val notes: Int,
    val colours: List<HighlightColor>,
    val latest: Annotation
)

/** What a book's annotations page can do with one annotation. */
data class AnnotationActions(
    val onOpen: (Annotation) -> Unit = {},
    val onShare: (Annotation) -> Unit = {},
    val onNote: (Annotation) -> Unit = {},
    val onDelete: (Annotation) -> Unit = {}
)

data class AnnotationsUiState(
    val loading: Boolean = true,
    /** The tab: books with annotations, most recently annotated first. */
    val books: List<AnnotatedBook> = emptyList(),
    /** A book's page: that book and its annotations in page order. */
    val book: Book? = null,
    val annotations: List<Annotation> = emptyList(),
    val total: Int = 0
)

/**
 * The Annotations tab (owner, 2026-10-18) and, with a book id in the route, one book's annotations. Highlights are
 * made in the reader; here they are listed, shared, edited, opened and removed.
 */
@HiltViewModel
class AnnotationsViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val repository: AnnotationRepository,
    library: LibraryRepository,
    private val reader: ReaderRepository
) : ViewModel() {
    private val onlyBook: String? = saved[ARG_BOOK_ID]

    val uiState: StateFlow<AnnotationsUiState> = combine(
        repository.observeAll(),
        library.observeBooks(LibrarySort.RECENT)
    ) { all, books ->
        val byId = books.associateBy { it.id }
        val known = all.filter { it.bookId in byId }
        val book = onlyBook?.let { byId[it] }
        AnnotationsUiState(
            loading = false,
            books = known.groupBy { it.bookId }.map { (id, list) ->
                AnnotatedBook(
                    book = byId.getValue(id),
                    highlights = list.size,
                    notes = list.count { it.note != null },
                    colours = HighlightColor.entries.filter { c -> list.any { it.color == c } },
                    latest = list.maxBy { it.updatedAtMillis }
                )
            }.sortedByDescending { it.latest.updatedAtMillis },
            book = book,
            annotations = if (onlyBook == null) {
                emptyList()
            } else {
                known.filter { it.bookId == onlyBook }
                    .sortedWith(compareBy({ it.page }, { it.start }))
            },
            total = known.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), AnnotationsUiState())

    fun setNote(a: Annotation, note: String?) = viewModelScope.launch { repository.setNote(a.id, note) }

    fun remove(a: Annotation) = viewModelScope.launch { repository.remove(a.id) }

    /** Makes the highlight's page the reading position, then calls [then] (to open the reader there). */
    fun open(a: Annotation, then: () -> Unit) = viewModelScope.launch {
        reader.saveProgress(a.bookId, a.page)
        then()
    }

    companion object {
        /** Optional: one book's annotations. */
        const val ARG_BOOK_ID = "bookId"
        private const val STOP_MS = 5_000L
    }
}
