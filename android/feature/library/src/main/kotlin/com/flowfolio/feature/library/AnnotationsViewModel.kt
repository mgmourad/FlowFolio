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
import com.flowfolio.core.model.OutlineEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val onDelete: (Annotation) -> Unit = {},
    /** Press and hold → Select: select mode, with this one chosen. */
    val onSelect: ((Annotation) -> Unit)? = null
)

data class AnnotationsUiState(
    val loading: Boolean = true,
    /** The tab: books with annotations, most recently annotated first. */
    val books: List<AnnotatedBook> = emptyList(),
    /** A book's page: that book and its annotations in page order. */
    val book: Book? = null,
    val annotations: List<Annotation> = emptyList(),
    val total: Int = 0,
    /** The chapter (the deepest contents entry) each annotation is in, by annotation id; missing when unknown. */
    val chapters: Map<Long, String> = emptyMap()
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

    /** Each annotated book's contents with their pages, read once (PDF outlines). */
    private val outlines = MutableStateFlow<Map<String, List<OutlineEntry>>>(emptyMap())

    init {
        viewModelScope.launch {
            repository.observeAll().map { all -> all.map { it.bookId }.toSet() }.distinctUntilChanged().collect { ids ->
                ids.filterNot { it in outlines.value }.forEach { id -> outlines.update { it + (id to outlineOf(id)) } }
            }
        }
    }

    private suspend fun outlineOf(bookId: String): List<OutlineEntry> = withContext(Dispatchers.IO) {
        runCatching { reader.open(bookId).document.use { doc -> doc.outline().filter { it.pageIndex >= 0 } } }
            .getOrNull().orEmpty()
    }

    val uiState: StateFlow<AnnotationsUiState> = combine(
        repository.observeAll(),
        library.observeBooks(LibrarySort.RECENT),
        outlines
    ) { all, books, contents ->
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
            total = known.size,
            chapters = known.mapNotNull { a -> chapterOf(contents[a.bookId], a.page)?.let { a.id to it } }.toMap()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), AnnotationsUiState())

    fun setNote(a: Annotation, note: String?) = viewModelScope.launch { repository.setNote(a.id, note) }

    fun remove(a: Annotation) = viewModelScope.launch { repository.remove(a.id) }

    /** Select mode's Delete on a book's page, after its confirmation. */
    fun removeAll(ids: Collection<Long>) = viewModelScope.launch { ids.forEach { repository.remove(it) } }

    /** Select mode's Delete on the tab: every annotation of [bookIds], after its confirmation. */
    fun removeBooks(bookIds: Collection<String>) = viewModelScope.launch {
        repository.observeAll().first().filter { it.bookId in bookIds }.forEach { repository.remove(it.id) }
    }

    /** Makes the highlight's page the reading position, then calls [then] (to open the reader there). */
    fun open(a: Annotation, then: () -> Unit) = viewModelScope.launch {
        reader.saveProgress(a.bookId, a.page)
        then()
    }

    companion object {
        /**
         * The contents entry starting nearest before or on [page]; of several on that page, the last listed (the
         * deepest section). By page, not by place in the list: an outline can list a stray entry late that points
         * early in the book (OpenStax's "Blank Page"), which must not stand for every page after it. Entries without a
         * real title, or naming a blank page, are passed over.
         */
        internal fun chapterOf(entries: List<OutlineEntry>?, page: Int): String? = entries.orEmpty()
            .withIndex()
            .filter { (_, e) -> e.pageIndex in 0..page && e.title.isNotBlank() && !e.title.isBlankPageName() }
            .maxWithOrNull(compareBy({ it.value.pageIndex }, { it.index }))
            ?.value?.title

        private fun String.isBlankPageName() = trim().equals("blank page", ignoreCase = true)

        /** Optional: one book's annotations. */
        const val ARG_BOOK_ID = "bookId"
        private const val STOP_MS = 5_000L
    }
}
