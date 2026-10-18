package com.flowfolio.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.BookmarkRepository
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.ReaderRepository
import com.flowfolio.core.designsystem.CoverSpot
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.BookmarkColor
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.core.model.pageLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A ribbon in the Bookmarks tab: the bookmark, its book, and where it is ([chapter], printed page [label]). */
data class BookmarkItem(val bookmark: Bookmark, val book: Book, val chapter: String?, val label: String)

data class BookmarksUiState(
    val loading: Boolean = true,
    /** The ribbons shown (newest first), after the colour filter. */
    val items: List<BookmarkItem> = emptyList(),
    /** How many ribbons there are in all, and in how many books. */
    val total: Int = 0,
    val bookCount: Int = 0,
    /** The colours in use, for the filter chips; null [filter] shows them all. */
    val colours: List<BookmarkColor> = emptyList(),
    val filter: BookmarkColor? = null,
    /** The book this page is for (a book's Bookmarks page), or null for the Bookmarks tab. */
    val book: Book? = null
)

/** What the Bookmarks tab can ask for. */
data class BookmarkActions(
    val onOpen: (BookmarkItem, CoverSpot) -> Unit = { _, _ -> },
    val onRecolour: (BookmarkItem, BookmarkColor) -> Unit = { _, _ -> },
    val onRemove: (BookmarkItem) -> Unit = {},
    val onFilter: (BookmarkColor?) -> Unit = {},
    /** Back, on a book's own Bookmarks page (none on the tab). */
    val onBack: (() -> Unit)? = null
)

/**
 * Where a book's ribbons are: its outline (for the chapter) and the printed labels of the marked pages. Read once per
 * book from the PDF; a book that cannot be opened has neither.
 */
private class BookIndex(val outline: List<OutlineEntry>, val labels: Map<Int, String>)

/** The Bookmarks tab: every ribbon in the library, newest first, filterable by colour. */
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val bookmarks: BookmarkRepository,
    library: LibraryRepository,
    private val reader: ReaderRepository
) : ViewModel() {
    private val onlyBook: String? = saved[ARG_BOOK_ID]
    private val filter = MutableStateFlow<BookmarkColor?>(null)
    private val indexes = MutableStateFlow<Map<String, BookIndex>>(emptyMap())

    /** The marked pages each book was indexed for; a new page re-reads its label. */
    private val indexedPages = mutableMapOf<String, Set<Int>>()

    val uiState: StateFlow<BookmarksUiState> = combine(
        bookmarks.observeAll(),
        library.observeBooks(LibrarySort.RECENT),
        indexes,
        filter
    ) { marks, books, index, chosen ->
        val byId = books.associateBy { it.id }
        val all = marks.filter { onlyBook == null || it.bookId == onlyBook }
            .mapNotNull { m -> byId[m.bookId]?.let { item(m, it, index[m.bookId]) } }
        all.groupBy { it.book.id }.forEach { (id, items) -> indexLater(id, items.map { it.bookmark.page }.toSet()) }
        val colours = BookmarkColor.entries.filter { c -> all.any { it.bookmark.color == c } }
        val shown = chosen?.takeIf { it in colours }
        BookmarksUiState(
            loading = false,
            items = if (shown == null) all else all.filter { it.bookmark.color == shown },
            total = all.size,
            bookCount = all.map { it.book.id }.distinct().size,
            colours = colours,
            filter = shown,
            book = onlyBook?.let { byId[it] }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BookmarksUiState())

    fun setFilter(colour: BookmarkColor?) {
        filter.value = colour
    }

    fun recolour(item: BookmarkItem, colour: BookmarkColor) =
        viewModelScope.launch { bookmarks.setColor(item.bookmark.id, colour) }

    fun remove(item: BookmarkItem) = viewModelScope.launch { bookmarks.remove(item.bookmark.id) }

    /** Makes the ribbon's page the reading position, then calls [then] (to open the reader there). */
    fun open(item: BookmarkItem, then: () -> Unit) = viewModelScope.launch {
        reader.saveProgress(item.book.id, item.bookmark.page)
        then()
    }

    private fun item(mark: Bookmark, book: Book, index: BookIndex?): BookmarkItem {
        val chapter = index?.outline?.let { outline ->
            outline.filter { it.pageIndex in 0..mark.page }.maxByOrNull { it.pageIndex }?.title
        }
        return BookmarkItem(mark, book, chapter, index?.labels?.get(mark.page) ?: book.pageLabel(mark.page))
    }

    /** Reads a book's outline and the labels of its marked [pages], off the main thread; the cards fill in after. */
    private fun indexLater(bookId: String, pages: Set<Int>) {
        if (indexedPages[bookId]?.containsAll(pages) == true) return
        indexedPages[bookId] = pages + indexedPages[bookId].orEmpty()
        viewModelScope.launch {
            val index = runCatching {
                reader.open(bookId).document.use { doc ->
                    BookIndex(doc.outline(), pages.mapNotNull { p -> doc.pageLabel(p)?.let { p to it } }.toMap())
                }
            }.getOrDefault(BookIndex(emptyList(), emptyMap()))
            indexes.update { all ->
                val before = all[bookId]
                all + (bookId to BookIndex(index.outline, before?.labels.orEmpty() + index.labels))
            }
        }
    }

    companion object {
        /** Optional: show only this book's ribbons. */
        const val ARG_BOOK_ID = "bookId"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
