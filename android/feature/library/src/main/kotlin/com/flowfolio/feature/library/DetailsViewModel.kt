package com.flowfolio.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.BookMarkCounts
import com.flowfolio.core.data.BookTagRepository
import com.flowfolio.core.data.BookmarkRepository
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.MarkCounts
import com.flowfolio.core.data.ReaderRepository
import com.flowfolio.core.data.ReadingGoalRepository
import com.flowfolio.core.data.ReadingRepository
import com.flowfolio.core.data.RelinkResult
import com.flowfolio.core.data.ShelfRepository
import com.flowfolio.core.data.flow.ReadingViews
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookDetails
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.core.model.ReadingGoal
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
    val contents: DetailsContents = DetailsContents.Loading,
    /** Time spent reading this book (milliseconds), its ribbons, and the other books by the same author. */
    val readMillis: Long = 0,
    val ribbons: Int = 0,
    val sameAuthor: List<Book> = emptyList(),
    /** This book's reading goal (M3T3), if set. */
    val bookGoal: ReadingGoal? = null,
    /** This book's marks by kind, from the count every screen shares (ADR-0052). */
    val counts: BookMarkCounts = BookMarkCounts(),
    /** The book's own tags, and every tag in the library (owner, 2026-11-10); filled in by the screen. */
    val tags: List<String> = emptyList(),
    val allTags: List<String> = emptyList()
) {
    val bookShelves: List<Shelf> get() = book?.let { b -> shelves.filter { b.id in it.bookIds } }.orEmpty()
}

/** A book's details page: the book itself, the shelves it is on and its contents. */
@Suppress("LongParameterList") // one injected source per part of the page
@HiltViewModel
class DetailsViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val library: LibraryRepository,
    private val reader: ReaderRepository,
    shelves: ShelfRepository,
    reading: ReadingRepository,
    bookmarks: BookmarkRepository,
    @Suppress("unused") views: ReadingViews,
    private val goals: ReadingGoalRepository,
    private val marks: MarkCounts,
    private val tagRepo: BookTagRepository
) : ViewModel() {
    val bookId: String = checkNotNull(saved[ARG_BOOK_ID]) { "details needs a book id" }

    /** The book's own tags (Room v22), and every tag in the library for suggestions (owner, 2026-11-10). */
    val tags: StateFlow<List<String>> =
        tagRepo.tagsOf(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(TAG_STOP_MS), emptyList())
    val allTags: StateFlow<List<String>> =
        tagRepo.allTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(TAG_STOP_MS), emptyList())

    fun addTag(tag: String) = viewModelScope.launch { tagRepo.add(bookId, tag) }

    fun removeTag(tag: String) = viewModelScope.launch { tagRepo.remove(bookId, tag) }

    private val contents = MutableStateFlow<DetailsContents>(DetailsContents.Loading)
    private val availability = MutableStateFlow(Availability.AVAILABLE)

    val uiState: StateFlow<DetailsUiState> =
        combine(
            library.observeBook(bookId),
            shelves.observeShelves(),
            contents,
            availability,
            extras(reading, bookmarks, goals)
        ) { book, s, c, a, x ->
            DetailsUiState(
                loading = false,
                book = book,
                availability = a,
                shelves = s,
                contents = c,
                readMillis = x.readMillis,
                ribbons = x.counts.bookmarks,
                counts = x.counts,
                sameAuthor = book?.author?.let { author -> x.books.filter { it.id != bookId && it.author == author } }
                    .orEmpty(),
                bookGoal = x.goal
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DetailsUiState())

    init {
        viewModelScope.launch { load() }
    }

    /** Remembers [page] as the reading position, then calls [then] (to open the reader there). */
    fun startAt(page: Int, then: () -> Unit) = viewModelScope.launch {
        if (page >= 0) reader.saveProgress(bookId, page)
        then()
    }

    fun markOpened() = viewModelScope.launch { library.markOpened(bookId) }

    /** Points the book at a file the reader located again, then re-reads its availability and contents. */
    fun relink(uri: String) = viewModelScope.launch {
        if (library.relink(bookId, uri) == RelinkResult.Relinked) load()
    }

    private class Extras(
        val readMillis: Long,
        val counts: BookMarkCounts,
        val books: List<Book>,
        val goal: ReadingGoal?
    )

    @Suppress("UnusedParameter") // ribbons are counted through MarkCounts, like every other screen (ADR-0052)
    private fun extras(reading: ReadingRepository, bookmarks: BookmarkRepository, goals: ReadingGoalRepository) =
        combine(
            reading.observeReadMillis(bookId),
            marks.all,
            library.observeBooks(LibrarySort.RECENT),
            goals.observeBookGoal(bookId)
        ) { ms, all, books, goal -> Extras(ms, all.byBook[bookId] ?: BookMarkCounts(), books, goal) }

    /** Starts the book again from its first page (ADR-0052: Reset progress, after a confirmation). */
    fun resetProgress() = viewModelScope.launch { reader.saveProgress(bookId, 0) }

    /** This book's marks as Markdown, for Export notes. */
    suspend fun notesMarkdown(): String {
        val book = library.observeBook(bookId).first() ?: return ""
        return notebookMarkdown(listOf(book), marks.all.first())
    }

    fun saveGoal(goal: ReadingGoal) = viewModelScope.launch { goals.saveGoal(goal) }

    fun resetGoal() = viewModelScope.launch { goals.resetGoal(bookId) }

    /** Saves Edit details. */
    fun saveDetails(title: String, author: String?, details: BookDetails, then: () -> Unit) = viewModelScope.launch {
        if (title.isNotBlank()) library.updateDetails(bookId, title, author, details)
        then()
    }

    private suspend fun load() {
        val book = library.observeBook(bookId).first() ?: return
        availability.value = library.availability(book)
        if (book.passwordProtected || availability.value == Availability.MISSING) {
            contents.value = DetailsContents.None
            return
        }
        val entries = if (book.format == BookFormat.EPUB) {
            // EPUB pages depend on the text size: chapters are listed without page numbers (pageIndex -1).
            reader.epubContents(bookId).map { OutlineEntry(depth = 0, pageIndex = -1, title = it) }
        } else {
            runCatching {
                reader.open(bookId).document.use { doc -> doc.outline().filter { it.depth == 0 && it.pageIndex >= 0 } }
            }.getOrNull().orEmpty()
        }
        contents.value = entries.takeIf { it.isNotEmpty() }?.let { DetailsContents.Ready(it) } ?: DetailsContents.None
    }

    companion object {
        const val ARG_BOOK_ID = "bookId"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

private const val TAG_STOP_MS = 5_000L
