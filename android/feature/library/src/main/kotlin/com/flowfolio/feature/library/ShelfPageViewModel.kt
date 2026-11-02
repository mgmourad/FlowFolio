package com.flowfolio.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.RelinkResult
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.data.ShelfRepository
import com.flowfolio.core.data.flow.DynamicFlow
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.FlowCarry
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.Shelf
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShelfPageUiState(
    val loading: Boolean = true,
    /** The imported folder shown (its path), when this page is a folder's rather than a shelf's. */
    val folder: String? = null,
    /** The folders inside [folder]. */
    val subfolders: List<FolderEntry> = emptyList(),
    /** The shelf shown, or null for the books on no shelf ("All books" when there are no shelves). */
    val shelf: Shelf? = null,
    val noShelves: Boolean = false,
    val items: List<LibraryItem> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
    /** Every book in the library, for "Add books" and for what a chosen folder holds. */
    val allBooks: List<Book> = emptyList(),
    /** [ShelfPageViewModel.FINISHED] or [ShelfPageViewModel.FAVORITES]: one of the library's own pages. */
    val special: Long? = null
) {
    /** The books select mode's [keys] stand for: books by id, and every book in a chosen folder. */
    fun booksChosen(keys: Set<String>): List<String> {
        val paths = keys.filter {
            it.startsWith(FolderEntry.FOLDER_PREFIX)
        }.map { it.removePrefix(FolderEntry.FOLDER_PREFIX) }
        val inFolders = allBooks.filter { b -> paths.any { b.isUnder(it) } }.map { it.id }
        return (keys.filterNot { it.startsWith(FolderEntry.FOLDER_PREFIX) } + inFolders).distinct()
    }
}

/** One shelf's books, as tiles or a list, with renaming, removing and locating books. */
@HiltViewModel
class ShelfPageViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val library: LibraryRepository,
    shelfRepo: ShelfRepository,
    private val settingsRepo: SettingsRepository,
    private val flow: DynamicFlow = DynamicFlow.None
) : ViewModel(),
    FlowSwitching {
    /** Whether switching a book's view asks first, or what was answered for good (ADR-0045). */
    override val flowCarry: StateFlow<FlowCarry> =
        settingsRepo.settings.map { it.flowCarry }.stateIn(viewModelScope, SharingStarted.Eagerly, FlowCarry.ASK)

    /** Turns a PDF's Dynamic Flow on (converting it in the background) or off, bringing the changes across or not. */
    override fun setFlow(book: Book, on: Boolean, carry: Boolean) {
        viewModelScope.launch {
            flow.setFlowMode(book.id, on, carry)
        }
    }

    /** Whether [book] has highlights, notes or bookmarks in its Dynamic Flow ([inFlow]) or its PDF. */
    override suspend fun hasNotes(book: Book, inFlow: Boolean): Boolean = flow.hasNotes(book.id, inFlow)

    /** Makes [book] a favourite, or not (owner, M3T2). */
    fun toggleFavorite(book: Book) {
        viewModelScope.launch { library.setFavorite(book.id, !book.favorite) }
    }

    /** Marks [book] finished, or not finished (owner, M3T2). */
    fun toggleFinished(book: Book) {
        viewModelScope.launch { library.setFinished(book.id, !book.finished) }
    }

    override fun setFlowCarry(carry: FlowCarry) {
        viewModelScope.launch { settingsRepo.setFlowCarry(carry) }
    }

    private val shelfId: Long = saved[ARG_SHELF_ID] ?: LOOSE
    private val folder: String? = saved.get<String>(ARG_FOLDER)?.let(android.net.Uri::decode)
    private val availability = MutableStateFlow<Map<String, Availability>>(emptyMap())

    val uiState: StateFlow<ShelfPageUiState> =
        combine(library.observeBooks(LibrarySort.RECENT), shelfRepo.observeShelves(), availability) {
                books,
                shelves,
                a
            ->
            val shelf = shelves.firstOrNull { it.id == shelfId }
            val byId = books.associateBy { it.id }
            val list = when {
                folder != null -> books.filter { it.folder == folder }.sortedBy { it.title.lowercase() }

                shelf != null -> shelf.bookIds.mapNotNull(byId::get)

                // The library's own pages (owner, M3T2): finished books, latest first, and favourites.
                shelfId == FINISHED -> books.filter { it.finished }.sortedByDescending { it.finishedAtMillis }

                shelfId == FAVORITES -> books.filter { it.favorite }.sortedBy { it.title.lowercase() }

                else -> {
                    val shelved = shelves.flatMapTo(HashSet()) { it.bookIds }
                    books.filterNot { it.id in shelved }
                }
            }
            ShelfPageUiState(
                loading = false,
                folder = folder,
                subfolders = folder?.let { subfolders(books, it) }.orEmpty(),
                shelf = shelf.takeIf { folder == null },
                special = shelfId.takeIf { folder == null && shelf == null && it in SPECIAL },
                noShelves = shelves.isEmpty(),
                items = list.map { LibraryItem(it, a[it.id] ?: Availability.AVAILABLE) },
                shelves = shelves,
                allBooks = books
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ShelfPageUiState())

    init {
        viewModelScope.launch {
            uiState.collect { state -> checkLinked(state.items.map { it.book }) }
        }
    }

    fun rename(bookId: String, title: String) = viewModelScope.launch {
        if (title.isNotBlank()) library.rename(bookId, title)
    }

    fun delete(bookId: String) = viewModelScope.launch { library.delete(bookId) }

    /**
     * Removes what select mode chose: books by id, and for a chosen folder every book in it and in the folders inside
     * it.
     */
    fun deleteChosen(keys: Set<String>) = viewModelScope.launch {
        val paths = keys.filter {
            it.startsWith(FolderEntry.FOLDER_PREFIX)
        }.map { it.removePrefix(FolderEntry.FOLDER_PREFIX) }
        val inFolders = library.observeBooks(LibrarySort.RECENT).first()
            .filter { b -> paths.any { b.isUnder(it) } }.map { it.id }
        (
            keys.filterNot {
                it.startsWith(FolderEntry.FOLDER_PREFIX)
            } + inFolders
            ).distinct().forEach { library.delete(it) }
    }

    /** Removes every book in the folder [path] and the folders inside it. */
    fun removeFolder(path: String) = deleteChosen(setOf(FolderEntry.FOLDER_PREFIX + path))

    fun relink(bookId: String, uri: String) = viewModelScope.launch {
        if (library.relink(bookId, uri) == RelinkResult.Relinked) {
            availability.update { it + (bookId to Availability.AVAILABLE) }
        }
    }

    /** Linked books can go missing outside the app. */
    private suspend fun checkLinked(books: List<Book>) {
        val linked = books.filter { it.storage is BookStorage.Linked && it.id !in availability.value }
        if (linked.isEmpty()) return
        val found = linked.associate { it.id to library.availability(it) }
        availability.update { it + found }
    }

    companion object {
        const val ARG_SHELF_ID = "shelfId"

        /** An imported folder's path, URL-encoded; set on a folder's page instead of a shelf. */
        const val ARG_FOLDER = "folder"

        /** The row of books on no shelf (all books when there are no shelves). */
        const val LOOSE = -1L

        /** The finished books' page (owner, M3T2). */
        const val FINISHED = -2L

        /** The favourites' page, the Favourites tab (owner, M3T2). */
        const val FAVORITES = -3L

        private val SPECIAL = setOf(FINISHED, FAVORITES)
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
