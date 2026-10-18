package com.flowfolio.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.ImportResult
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.RelinkResult
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.data.ShelfRepository
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.Shelf
import com.flowfolio.core.model.StorageMode
import com.flowfolio.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryItem(val book: Book, val availability: Availability = Availability.AVAILABLE)

/** The chips under the library header. Type chips (Textbooks, Novels) come with book types in a later slice. */
enum class LibraryFilter { ALL, IN_PROGRESS, NOT_STARTED, RECENT }

data class LibraryUiState(
    /** The books that pass [filter] and [query], in the chosen sort order. */
    val items: List<LibraryItem> = emptyList(),
    val loading: Boolean = true,
    val sort: LibrarySort = LibrarySort.RECENT,
    val importing: Boolean = false,
    val dialog: LibraryDialog? = null,
    val shelves: List<Shelf> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.ALL,
    val query: String = "",
    /** Books in the library and how many are started, for the header line. */
    val totalCount: Int = 0,
    val inProgressCount: Int = 0,
    /** The most recently opened book with a saved position, for the "Continue reading" card. */
    val continueReading: LibraryItem? = null
) {
    /** Whether the library itself is empty (as opposed to nothing matching the chips or search). */
    val libraryEmpty: Boolean get() = totalCount == 0
}

/** One shelf row on the library screen: a shelf with its visible books, or the books on no shelf ([shelf] null). */
data class ShelfRow(val shelf: Shelf?, val items: List<LibraryItem>)

/**
 * The library's rows: each shelf with the [items] on it (in the shelf's order), then the books on no shelf. Shelves
 * with no visible books are left out, except that with no shelves at all every book is in one row.
 */
fun shelfRows(items: List<LibraryItem>, shelves: List<Shelf>): List<ShelfRow> {
    val byId = items.associateBy { it.book.id }
    val rows = shelves.mapNotNull { shelf ->
        shelf.bookIds.mapNotNull(byId::get).takeIf { it.isNotEmpty() }?.let { ShelfRow(shelf, it) }
    }
    val shelved = shelves.flatMapTo(HashSet()) { it.bookIds }
    val loose = items.filterNot { it.book.id in shelved }
    return if (loose.isEmpty()) rows else rows + ShelfRow(null, loose)
}

internal fun Book.started(): Boolean = (lastPage ?: 0) > 0

internal fun LibraryFilter.accepts(book: Book, now: Long): Boolean = when (this) {
    LibraryFilter.ALL -> true
    LibraryFilter.IN_PROGRESS -> book.started() && book.progress < 1f
    LibraryFilter.NOT_STARTED -> !book.started()
    LibraryFilter.RECENT -> now - book.addedAtMillis <= RECENT_WINDOW_MS
}

internal fun Book.matches(query: String): Boolean =
    query.isBlank() || title.contains(query.trim(), ignoreCase = true) || author?.contains(query.trim(), true) == true

private const val RECENT_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

sealed interface LibraryDialog {
    /** First import ever, or "ask every time": choose copy or read-in-place, with both disclaimers. */
    data class ChooseStorage(
        val uri: String,
        val initial: StorageMode,
        val firstTime: Boolean,
        val sizeBytes: Long? = null
    ) : LibraryDialog

    data class LinkNotPossible(val uri: String) : LibraryDialog

    data class ConfirmDelete(val book: Book) : LibraryDialog
}

sealed interface LibraryMessage {
    data class Imported(val title: String) : LibraryMessage

    data class Duplicate(val title: String) : LibraryMessage

    data object NotAPdf : LibraryMessage

    data object Unreadable : LibraryMessage

    data class NotEnoughSpace(val bytes: Long) : LibraryMessage

    data object Relinked : LibraryMessage

    data object RelinkDifferentFile : LibraryMessage

    data object RelinkFailed : LibraryMessage

    data class Deleted(val title: String) : LibraryMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val settingsRepo: SettingsRepository,
    shelfRepo: ShelfRepository
) : ViewModel() {
    private val view = MutableStateFlow(View())
    private val transient = MutableStateFlow(Transient())
    private val availability = MutableStateFlow<Map<String, Availability>>(emptyMap())
    private val messageChannel = Channel<LibraryMessage>(Channel.BUFFERED)
    val messages: Flow<LibraryMessage> = messageChannel.receiveAsFlow()

    private val settings: StateFlow<UserSettings?> = settingsRepo.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )

    private val books: Flow<List<Book>> = settingsRepo.settings
        .map { it.librarySort }
        .distinctUntilChanged()
        .flatMapLatest { library.observeBooks(it) }
        .onEach { list -> checkAvailability(list) }

    private val listed = combine(books, availability, shelfRepo.observeShelves()) { list, avail, shelves ->
        Triple(list.map { LibraryItem(it, avail[it.id] ?: Availability.AVAILABLE) }, avail, shelves)
    }

    val uiState: StateFlow<LibraryUiState> = combine(listed, settingsRepo.settings, transient, view) {
            (all, _, shelves),
            s,
            t,
            v
        ->
        val now = System.currentTimeMillis()
        LibraryUiState(
            items = all.filter { v.filter.accepts(it.book, now) && it.book.matches(v.query) },
            loading = false,
            sort = s.librarySort,
            importing = t.importing,
            dialog = t.dialog,
            shelves = shelves,
            filter = v.filter,
            query = v.query,
            totalCount = all.size,
            inProgressCount = all.count { it.book.started() },
            continueReading = all.filter { it.book.started() && it.book.lastOpenedAtMillis != null }
                .maxByOrNull { it.book.lastOpenedAtMillis ?: 0L }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    fun onFilter(filter: LibraryFilter) = view.update { it.copy(filter = filter) }

    fun onQuery(query: String) = view.update { it.copy(query = query) }

    /** A file chosen with the in-app picker. */
    fun onFilePicked(uri: String) = viewModelScope.launch {
        val s = settings.value ?: settingsRepo.settings.first()
        when {
            !s.storageNoticeSeen -> showDialog(
                LibraryDialog.ChooseStorage(uri, s.storageMode, true, library.sizeOf(uri))
            )

            s.askStorageEachImport -> showDialog(
                LibraryDialog.ChooseStorage(uri, s.storageMode, false, library.sizeOf(uri))
            )

            else -> import(uri, s.storageMode)
        }
    }

    /** A file handed over by another app ("Open with", share sheet): always copied. */
    fun onFileShared(uri: String) {
        viewModelScope.launch { import(uri, StorageMode.COPY, fromShare = true) }
    }

    fun onStorageChosen(dialog: LibraryDialog.ChooseStorage, mode: StorageMode) = viewModelScope.launch {
        dismissDialog()
        if (dialog.firstTime) {
            settingsRepo.setStorageMode(mode)
            settingsRepo.setStorageNoticeSeen()
        }
        import(dialog.uri, mode)
    }

    fun onCopyInstead(uri: String) = viewModelScope.launch {
        dismissDialog()
        import(uri, StorageMode.COPY)
    }

    fun onRequestDelete(book: Book) = showDialog(LibraryDialog.ConfirmDelete(book))

    fun onConfirmDelete(book: Book) = viewModelScope.launch {
        dismissDialog()
        library.delete(book.id)
        messageChannel.send(LibraryMessage.Deleted(book.title))
    }

    fun onRelinkPicked(bookId: String, uri: String) = viewModelScope.launch {
        val message = when (library.relink(bookId, uri)) {
            RelinkResult.Relinked -> LibraryMessage.Relinked
            RelinkResult.DifferentFile -> LibraryMessage.RelinkDifferentFile
            RelinkResult.Unreadable, RelinkResult.LinkNotPossible -> LibraryMessage.RelinkFailed
        }
        if (message == LibraryMessage.Relinked) availability.update { it + (bookId to Availability.AVAILABLE) }
        messageChannel.send(message)
    }

    fun onSortChange(sort: LibrarySort) = viewModelScope.launch { settingsRepo.setLibrarySort(sort) }

    fun onBookOpened(bookId: String) = viewModelScope.launch { library.markOpened(bookId) }

    fun dismissDialog() = transient.update { it.copy(dialog = null) }

    private fun showDialog(dialog: LibraryDialog) = transient.update { it.copy(dialog = dialog) }

    private suspend fun import(uri: String, mode: StorageMode, fromShare: Boolean = false) {
        transient.update { it.copy(importing = true) }
        try {
            when (val r = library.import(uri, mode, fromShare)) {
                is ImportResult.Imported -> messageChannel.send(LibraryMessage.Imported(r.book.title))
                is ImportResult.Duplicate -> messageChannel.send(LibraryMessage.Duplicate(r.existing.title))
                ImportResult.NotAPdf -> messageChannel.send(LibraryMessage.NotAPdf)
                ImportResult.Unreadable -> messageChannel.send(LibraryMessage.Unreadable)
                is ImportResult.NotEnoughSpace -> messageChannel.send(LibraryMessage.NotEnoughSpace(r.neededBytes))
                ImportResult.LinkNotPossible -> showDialog(LibraryDialog.LinkNotPossible(uri))
            }
        } finally {
            transient.update { it.copy(importing = false) }
        }
    }

    /** Linked books can go missing outside the app; check each one whenever the list changes. */
    private fun checkAvailability(list: List<Book>) {
        viewModelScope.launch {
            val linked = list.filter { it.storage is BookStorage.Linked }
            val results = linked.associate { it.id to library.availability(it) }
            availability.update { results }
        }
    }

    private data class Transient(val importing: Boolean = false, val dialog: LibraryDialog? = null)

    private data class View(val filter: LibraryFilter = LibraryFilter.ALL, val query: String = "")

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
