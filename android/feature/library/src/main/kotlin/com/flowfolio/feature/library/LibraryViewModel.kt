package com.flowfolio.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.BookEnricher
import com.flowfolio.core.data.BookLookup
import com.flowfolio.core.data.ImportResult
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.ReadingGoalRepository
import com.flowfolio.core.data.ReadingRepository
import com.flowfolio.core.data.RelinkResult
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.data.ShelfRepository
import com.flowfolio.core.data.flow.DynamicFlow
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.BookType
import com.flowfolio.core.model.FlowCarry
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.ReadingIntent
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

data class LibraryItem(
    val book: Book,
    val availability: Availability = Availability.AVAILABLE,
    val hasBookmarks: Boolean = false,
    val isFavorite: Boolean = false
)

/**
 * The library chips. [TEXTBOOKS] and [NOVELS] (the mock's chips) appear once a book has that type; [FOLDERS] shows the
 * imported folders instead of the shelves (owner, 2026-10-26).
 */
enum class LibraryFilter { ALL, IN_PROGRESS, NOT_STARTED, FINISHED, TEXTBOOKS, NOVELS, RECENT, FOLDERS }

/** What the reader picked to import: some files, or a whole folder with every folder inside it. */
sealed interface ImportRequest {
    data class Files(val uris: List<String>) : ImportRequest

    data class Folder(val treeUri: String) : ImportRequest
}

data class LibraryUiState(
    /** The books that pass [filter] and [query], in the chosen sort order. */
    val items: List<LibraryItem> = emptyList(),
    val loading: Boolean = true,
    val sort: LibrarySort = LibrarySort.RECENT,
    /** How the books inside each shelf row are ordered (owner, M4): the shelf page's own choices. */
    val shelfSort: ShelfSort = ShelfSort.CUSTOM,
    /** Where each book sits in [shelfSort]'s order; empty for [ShelfSort.CUSTOM], where shelves keep their order. */
    val sortRank: Map<String, Int> = emptyMap(),
    val importing: Boolean = false,
    /** Books done and books in all, while a batch (several files, a folder) imports. */
    val importProgress: Pair<Int, Int>? = null,
    val dialog: LibraryDialog? = null,
    val shelves: List<Shelf> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.ALL,
    val query: String = "",
    /** Books in the library and how many are started, for the header line. */
    val totalCount: Int = 0,
    val inProgressCount: Int = 0,
    /** Favourites and finished books, for the totals' second column (owner, M3T2). */
    val favoriteCount: Int = 0,
    val finishedCount: Int = 0,
    /** The most recently opened book with a saved position, for the "Continue reading" card. */
    val continueReading: LibraryItem? = null,
    /** The chips to show: the type chips only when some book has that type. */
    val filters: List<LibraryFilter> = LibraryFilter.entries.filter { it.type == null }
) {
    /** Whether the library itself is empty (as opposed to nothing matching the chips or search). */
    val libraryEmpty: Boolean get() = totalCount == 0
}

/**
 * A row of the library: a shelf's books, the books on no shelf ([shelf] null), or a row of the library's own
 * ([special]: [ShelfPageViewModel.FINISHED], the finished books at the bottom; owner, M3T2).
 */
data class ShelfRow(val shelf: Shelf?, val items: List<LibraryItem>, val special: Long? = null)

/**
 * The library's rows: each shelf with the [items] on it, then the books on no shelf. Shelves with no visible
 * books are left out, except that with no shelves at all every book is in one row.
 *
 * Each row is in the shelf's own order unless a [rank] is given, which reorders within every row while leaving
 * the books where they are (owner, M4). The finished row keeps its own order, latest first.
 */
fun shelfRows(
    items: List<LibraryItem>,
    shelves: List<Shelf>,
    /** Where each book sits in the chosen order; empty leaves every row in the shelf's own order. */
    rank: Map<String, Int> = emptyMap()
): List<ShelfRow> {
    val byId = items.associateBy { it.book.id }
    fun List<LibraryItem>.ordered(): List<LibraryItem> =
        if (rank.isEmpty()) this else sortedBy { rank[it.book.id] ?: Int.MAX_VALUE }
    val rows = shelves.mapNotNull { shelf ->
        shelf.bookIds.mapNotNull(byId::get).takeIf { it.isNotEmpty() }?.let { ShelfRow(shelf, it.ordered()) }
    }
    val shelved = shelves.flatMapTo(HashSet()) { it.bookIds }
    val loose = items.filterNot { it.book.id in shelved }.ordered()
    val finished = items.filter { it.book.finished }.sortedByDescending { it.book.finishedAtMillis }
    return rows + listOfNotNull(
        ShelfRow(null, loose).takeIf { loose.isNotEmpty() },
        ShelfRow(null, finished, ShelfPageViewModel.FINISHED).takeIf { finished.isNotEmpty() }
    )
}

internal fun Book.started(): Boolean = (lastPage ?: 0) > 0

internal fun LibraryFilter.accepts(book: Book, now: Long): Boolean = when (this) {
    LibraryFilter.ALL -> true
    LibraryFilter.IN_PROGRESS -> book.started() && book.progress < 1f && !book.finished
    LibraryFilter.NOT_STARTED -> !book.started() && !book.finished
    LibraryFilter.FINISHED -> book.finished
    LibraryFilter.RECENT -> now - book.addedAtMillis <= RECENT_WINDOW_MS
    LibraryFilter.TEXTBOOKS, LibraryFilter.NOVELS -> book.details.type == type
    LibraryFilter.FOLDERS -> book.folder != null
}

/** The book type a chip stands for, or null for the reading-state chips. */
internal val LibraryFilter.type: BookType?
    get() = when (this) {
        LibraryFilter.TEXTBOOKS -> BookType.TEXTBOOK
        LibraryFilter.NOVELS -> BookType.NOVEL
        else -> null
    }

internal fun Book.matches(query: String): Boolean =
    query.isBlank() || title.contains(query.trim(), ignoreCase = true) || author?.contains(query.trim(), true) == true

/** What the app remembers about having offered to turn folders into shelves (owner, M5 round 4). */
private const val FOLDER_SHELVES = "folders_to_shelves"

private const val RECENT_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

sealed interface LibraryDialog {
    /** First import ever, or "ask every time": choose copy or read-in-place, with both disclaimers. */
    data class ChooseStorage(
        val request: ImportRequest,
        val initial: StorageMode,
        val firstTime: Boolean,
        val sizeBytes: Long? = null
    ) : LibraryDialog

    /** Reading in place was refused for [request] (or the files in it that refused); it can be copied instead. */
    data class LinkNotPossible(val request: ImportRequest) : LibraryDialog

    data class ConfirmDelete(val book: Book) : LibraryDialog

    /** After the first import: may FlowFolio look book details up online (ADR-0017)? */
    data class AskLookup(val books: List<Book>) : LibraryDialog

    /** After a folder import: offers to turn its folders into shelves (owner, M5 round 4). */
    data class FoldersToShelvesAsk(val books: List<Book>, val shelves: Int) : LibraryDialog
}

sealed interface LibraryMessage {
    data class Imported(val title: String) : LibraryMessage

    data class Duplicate(val title: String) : LibraryMessage

    /** A batch is done: how many books came in, were in the library already, or could not be opened. */
    data class ImportedMany(val imported: Int, val already: Int, val failed: Int) : LibraryMessage

    /** The folder picked holds no PDFs or EPUBs. */
    data object FolderEmpty : LibraryMessage

    data object NotABook : LibraryMessage

    data object DrmProtected : LibraryMessage

    data object Unreadable : LibraryMessage

    data class NotEnoughSpace(val bytes: Long) : LibraryMessage

    data object Relinked : LibraryMessage

    data object RelinkDifferentFile : LibraryMessage

    data object RelinkFailed : LibraryMessage

    data class Deleted(val title: String) : LibraryMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions", "LongParameterList") // one handler per library action
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val settingsRepo: SettingsRepository,
    private val shelfRepo: ShelfRepository,
    private val lookup: BookLookup,
    readingRepo: ReadingRepository,
    goalRepo: ReadingGoalRepository,
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

    /** Sets the reader's own intention for [book], or clears it (owner, M5). */
    fun setIntent(book: Book, intent: ReadingIntent?) {
        viewModelScope.launch { library.setIntent(book.id, intent) }
    }

    /** Marks [book] finished, or not finished (owner, M3T2). */
    fun toggleFinished(book: Book) {
        viewModelScope.launch { library.setFinished(book.id, !book.finished) }
    }

    override fun setFlowCarry(carry: FlowCarry) {
        viewModelScope.launch { settingsRepo.setFlowCarry(carry) }
    }

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

    /** The app's first-run windows and tips (owner, M5). */
    val guideSettings: StateFlow<UserSettings?> = settings

    fun markGuideSeen(id: String) {
        viewModelScope.launch { settingsRepo.markGuideSeen(id) }
    }

    private val books: Flow<List<Book>> = settingsRepo.settings
        .map { it.librarySort }
        .distinctUntilChanged()
        .flatMapLatest { library.observeBooks(it) }
        .onEach { list -> checkAvailability(list) }

    private val bookIdsWithBookmarks: Flow<Set<String>> = library.observeBookIdsWithBookmarks()

    /** The order inside the shelf rows. Like the shelf page's, it lasts for the session rather than being stored. */
    private val shelfSortFlow = MutableStateFlow(ShelfSort.CUSTOM)

    private val shelfSorting = combine(
        shelfSortFlow,
        readingRepo.observeReadMillisByBook(),
        goalRepo.observeAllGoals()
    ) { sort, readMillis, goalList ->
        sort to ShelfSortInputs(readMillis, goalList.associateBy { it.scope })
    }

    private val listed =
        combine(books, availability, shelfRepo.observeShelves(), bookIdsWithBookmarks) {
                list,
                avail,
                shelves,
                bookmarkBookIds
            ->
            Triple(
                list.map {
                    LibraryItem(
                        book = it,
                        availability = avail[it.id] ?: Availability.AVAILABLE,
                        hasBookmarks = bookmarkBookIds.contains(it.id),
                        isFavorite = it.favorite
                    )
                },
                avail,
                shelves
            )
        }

    val uiState: StateFlow<LibraryUiState> = combine(
        listed,
        settingsRepo.settings,
        transient,
        view,
        shelfSorting
    ) {
            (all, _, shelves),
            s,
            t,
            v,
            (shelfSort, sortInputs)
        ->
        val now = System.currentTimeMillis()
        LibraryUiState(
            items = all.filter { v.filter.accepts(it.book, now) && it.book.matches(v.query) },
            loading = false,
            sort = s.librarySort,
            shelfSort = shelfSort,
            sortRank = sortRank(all.map { it.book }, shelfSort, sortInputs),
            importing = t.importing,
            importProgress = t.progress,
            dialog = t.dialog,
            shelves = shelves,
            filter = v.filter,
            query = v.query,
            totalCount = all.size,
            inProgressCount = all.count { it.book.started() && !it.book.finished },
            favoriteCount = all.count { it.book.favorite },
            finishedCount = all.count { it.book.finished },
            continueReading = all.filter {
                it.book.started() && !it.book.finished && it.book.lastOpenedAtMillis != null
            }
                .maxByOrNull { it.book.lastOpenedAtMillis ?: 0L },
            filters = LibraryFilter.entries.filter { f -> f.type == null || all.any { it.book.details.type == f.type } }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    fun onFilter(filter: LibraryFilter) = view.update { it.copy(filter = filter) }

    fun onQuery(query: String) = view.update { it.copy(query = query) }

    /** A file chosen with the in-app picker. */
    fun onFilePicked(uri: String) = onFilesPicked(listOf(uri))

    /** Files chosen with the in-app picker (one or several). */
    fun onFilesPicked(uris: List<String>) {
        if (uris.isNotEmpty()) request(ImportRequest.Files(uris.distinct()))
    }

    /** A folder chosen with the system's folder picker: its books and those in every folder inside it. */
    fun onFolderPicked(treeUri: String) = request(ImportRequest.Folder(treeUri))

    private fun request(request: ImportRequest) = viewModelScope.launch {
        val s = settings.value ?: settingsRepo.settings.first()
        val size = (request as? ImportRequest.Files)?.uris?.map { library.sizeOf(it) }
            ?.takeIf { sizes -> sizes.all { it != null } }?.sumOf { it ?: 0L }
        when {
            !s.storageNoticeSeen -> showDialog(LibraryDialog.ChooseStorage(request, s.storageMode, true, size))
            s.askStorageEachImport -> showDialog(LibraryDialog.ChooseStorage(request, s.storageMode, false, size))
            else -> run(request, s.storageMode)
        }
    }

    private suspend fun run(request: ImportRequest, mode: StorageMode) {
        val single = (request as? ImportRequest.Files)?.uris?.singleOrNull()
        if (single != null) import(single, mode) else batch.run(request, mode)
    }

    private val batch = BatchImport(
        library,
        onProgress = { p -> transient.update { it.copy(importing = p != null, progress = p) } },
        onDone = { outcome -> afterBatch(outcome) }
    )

    private suspend fun afterBatch(outcome: BatchImport.Outcome) {
        when {
            outcome.unreadable -> messageChannel.send(LibraryMessage.Unreadable)

            outcome.empty -> messageChannel.send(LibraryMessage.FolderEmpty)

            else -> messageChannel.send(
                LibraryMessage.ImportedMany(outcome.imported.size, outcome.already, outcome.failed)
            )
        }
        outcome.linkRefused?.let { showDialog(LibraryDialog.LinkNotPossible(it)) }
        if (outcome.imported.isNotEmpty()) lookUp(outcome.imported)
        offerShelves(outcome.imported)
    }

    /**
     * Offers to turn the folders a batch came in into shelves (owner, M5 round 4).
     *
     * Only worth asking when the books actually sat in folders, and only until the reader has said not to ask
     * again -- which is remembered like any other thing the app shows once, so "Introduce features again" brings
     * the offer back.
     */
    private suspend fun offerShelves(books: List<Book>) {
        if (FOLDER_SHELVES in settingsRepo.settings.first().seenGuides) return
        val plan = FoldersToShelves.plan(books)
        if (plan.isEmpty()) return
        showDialog(LibraryDialog.FoldersToShelvesAsk(books, plan.size))
    }

    /** Makes the shelves the reader agreed to, reusing any that are already there by that name. */
    fun onFoldersToShelves(dialog: LibraryDialog.FoldersToShelvesAsk, never: Boolean) = viewModelScope.launch {
        dismissDialog()
        if (never) settingsRepo.markGuideSeen(FOLDER_SHELVES)
        val existing = shelfRepo.observeShelves().first().associate { it.name to it.id }
        FoldersToShelves.apply(FoldersToShelves.plan(dialog.books), shelfRepo, existing)
    }

    /** "No thanks", with or without "and don't ask again". */
    fun onFoldersToShelvesDeclined(never: Boolean) = viewModelScope.launch {
        dismissDialog()
        if (never) settingsRepo.markGuideSeen(FOLDER_SHELVES)
    }

    /** The online lookup for new books: runs when allowed, or asks once (off until agreed, ADR-0017). */
    private fun lookUp(books: List<Book>) = viewModelScope.launch {
        val enricher = BookEnricher(lookup, library)
        val ask = books.map { enricher.afterImport(it, settingsRepo) }.firstOrNull() ?: false
        if (ask) showDialog(LibraryDialog.AskLookup(books))
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
        run(dialog.request, mode)
    }

    fun onCopyInstead(request: ImportRequest) = viewModelScope.launch {
        dismissDialog()
        run(request, StorageMode.COPY)
    }

    fun onRequestDelete(book: Book) = showDialog(LibraryDialog.ConfirmDelete(book))

    /** Removes every book in the folder [path] and the folders inside it (asked first). */
    fun onRemoveFolder(path: String) = viewModelScope.launch {
        uiState.value.items.map { it.book }.filter { it.isUnder(path) }.forEach { library.delete(it.id) }
    }

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

    /**
     * Orders the books inside every shelf row (owner, M4). The order the books are read from the database in is set
     * with them, so picking Name also fetches them by title and the rows need no reordering at all.
     */
    fun onShelfSortChange(sort: ShelfSort) {
        shelfSortFlow.value = sort
        onSortChange(if (sort == ShelfSort.NAME) LibrarySort.TITLE else LibrarySort.RECENT)
    }

    fun onBookOpened(bookId: String) = viewModelScope.launch { library.markOpened(bookId) }

    fun dismissDialog() = transient.update { it.copy(dialog = null) }

    /** The answer to [LibraryDialog.AskLookup]: remembered, and on yes the new book is looked up straight away. */
    fun onLookupAnswer(dialog: LibraryDialog.AskLookup, allow: Boolean) = viewModelScope.launch {
        dismissDialog()
        settingsRepo.setOnlineLookup(allow)
        if (allow) dialog.books.forEach { BookEnricher(lookup, library).enrich(it) }
    }

    private fun showDialog(dialog: LibraryDialog) = transient.update { it.copy(dialog = dialog) }

    private suspend fun import(uri: String, mode: StorageMode, fromShare: Boolean = false) {
        transient.update { it.copy(importing = true) }
        try {
            when (val r = library.import(uri, mode, fromShare)) {
                is ImportResult.Imported -> {
                    messageChannel.send(LibraryMessage.Imported(r.book.title))
                    lookUp(listOf(r.book))
                }

                is ImportResult.Duplicate -> messageChannel.send(LibraryMessage.Duplicate(r.existing.title))

                ImportResult.NotABook -> messageChannel.send(LibraryMessage.NotABook)

                ImportResult.DrmProtected -> messageChannel.send(LibraryMessage.DrmProtected)

                ImportResult.Unreadable -> messageChannel.send(LibraryMessage.Unreadable)

                is ImportResult.NotEnoughSpace -> messageChannel.send(LibraryMessage.NotEnoughSpace(r.neededBytes))

                ImportResult.LinkNotPossible -> showDialog(
                    LibraryDialog.LinkNotPossible(ImportRequest.Files(listOf(uri)))
                )
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

    private data class Transient(
        val importing: Boolean = false,
        val progress: Pair<Int, Int>? = null,
        val dialog: LibraryDialog? = null
    )

    private data class View(val filter: LibraryFilter = LibraryFilter.ALL, val query: String = "")

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
