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
import com.flowfolio.core.model.LibraryView
import com.flowfolio.core.model.ReadingIntent
import com.flowfolio.core.model.Shelf
import com.flowfolio.core.model.StatsBand
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
import kotlinx.coroutines.flow.asStateFlow
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
 * imported folders instead of the shelves (owner, 2026-10-26). [FAVORITES] replaced the Favorites tab (ADR-0052).
 */
enum class LibraryFilter { ALL, IN_PROGRESS, NOT_STARTED, FINISHED, FAVORITES, TEXTBOOKS, NOVELS, RECENT, FOLDERS }

/** What the reader picked to import: some files, or a whole folder with every folder inside it. */
sealed interface ImportRequest {
    data class Files(val uris: List<String>) : ImportRequest

    data class Folder(val treeUri: String) : ImportRequest
}

data class LibraryUiState(
    /** The books that pass [filter] and [query], in the chosen sort order. */
    val items: List<LibraryItem> = emptyList(),
    val loading: Boolean = true,
    /** The order of the books in every view and inside each shelf row (ADR-0052), remembered. */
    val sort: LibrarySort = LibrarySort.RECENT,
    /** Shelf, grid or list (ADR-0052), remembered; filtered views keep it. */
    val view: LibraryView = LibraryView.SHELF,
    /** The stats band: collapsed, expanded or hidden (ADR-0052), remembered. */
    val statsBand: StatsBand = StatsBand.COLLAPSED,
    /** Where each book sits in [sort]'s order, which reorders the books inside each shelf row. */
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
    /** The books opened most recently, for the global search before anything is typed (ADR-0052). */
    val recent: List<LibraryItem> = emptyList(),
    /** The most recently opened book with a saved position, for the "Continue reading" card. */
    val continueReading: LibraryItem? = null,
    /** The chips to show: the type chips only when some book has that type. */
    val filters: List<LibraryFilter> = LibraryFilter.entries.filter { it.type == null }
) {
    /** Whether the library itself is empty (as opposed to nothing matching the chips or search). */
    val libraryEmpty: Boolean get() = totalCount == 0
}

/**
 * A row of the library: a shelf's books, or the books on no shelf ([shelf] null, "Not on a shelf", always last).
 * [special] is kept for rows of the library's own; none is made since ADR-0052 (finished books have their chip).
 */
data class ShelfRow(val shelf: Shelf?, val items: List<LibraryItem>, val special: Long? = null) {
    /** A key no two rows share, for lazy lists (the old `row-loose` key crashed when two rows had no shelf). */
    val key: String get() = "row-" + (shelf?.id?.toString() ?: special?.let { "special-$it" } ?: "loose")
}

/**
 * The library's rows: each shelf with the [items] on it, then the books on no shelf. Shelves with no visible
 * books are left out, except that with no shelves at all every book is in one row.
 *
 * Each row is in the shelf's own order unless a [rank] is given, which reorders within every row while leaving
 * the books where they are (owner, M4).
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
    return rows + listOfNotNull(ShelfRow(null, loose).takeIf { loose.isNotEmpty() })
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

    // Starred books are known from the item, not the book ([accepts] with a [LibraryItem]).
    LibraryFilter.FAVORITES -> false
}

/** Whether [item] passes this chip: the book's own state, or for [LibraryFilter.FAVORITES] its star. */
internal fun LibraryFilter.accepts(item: LibraryItem, now: Long): Boolean =
    if (this == LibraryFilter.FAVORITES) item.isFavorite else accepts(item.book, now)

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

/** How many recent books the global search shows before anything is typed. */
private const val RECENT_IN_SEARCH = 4

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

    /** Shelves made from a folder the reader picked, rather than from a whole import. */
    data class ShelvesMade(val shelves: Int) : LibraryMessage

    /** A refresh checked the imported folders and added [added] books (TM2). */
    data class FoldersSynced(val added: Int) : LibraryMessage

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
    private val flow: DynamicFlow = DynamicFlow.None,
    private val folders: com.flowfolio.core.data.FolderSync = com.flowfolio.core.data.FolderSync.None
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

    /** "Never show tips again": turns first-use tips off and marks this one seen. */
    fun disableTips(id: String) {
        viewModelScope.launch {
            settingsRepo.setTipsEnabled(false)
            settingsRepo.markGuideSeen(id)
        }
    }

    private val books: Flow<List<Book>> = settingsRepo.settings
        .map { it.librarySort }
        .distinctUntilChanged()
        .flatMapLatest { library.observeBooks(it) }
        .onEach { list -> checkAvailability(list) }

    private val bookIdsWithBookmarks: Flow<Set<String>> = library.observeBookIdsWithBookmarks()

    /** Time read per book, for the Read time order. */
    private val readMillis = readingRepo.observeReadMillisByBook()

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
        readMillis
    ) {
            (unordered, _, shelves),
            s,
            t,
            v,
            millis
        ->
        val now = System.currentTimeMillis()
        val rank = orderLibrary(unordered.map { it.book }, s.librarySort, millis)
            .withIndex().associate { (i, b) -> b.id to i }
        val all = unordered.sortedBy { rank[it.book.id] ?: Int.MAX_VALUE }
        LibraryUiState(
            items = all.filter { v.filter.accepts(it, now) && it.book.matches(v.query) },
            loading = false,
            sort = s.librarySort,
            view = s.libraryView,
            statsBand = s.statsBand,
            sortRank = rank,
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
            recent = all.filter { it.book.lastOpenedAtMillis != null }
                .sortedByDescending { it.book.lastOpenedAtMillis }
                .take(RECENT_IN_SEARCH),
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

    /** Whether the folder being imported was picked to become shelves ("Make shelves from a folder", ADR-0052). */
    private var shelvesFromNextFolder = false

    /** Imports a folder and makes its folders into shelves, with no question asked: the reader chose this. */
    fun onFolderPickedForShelves(treeUri: String) {
        shelvesFromNextFolder = true
        onFolderPicked(treeUri)
    }

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
        onDone = { outcome -> afterBatch(outcome) },
        folders = folders
    )

    /** A pull-to-refresh is checking the imported folders (TM2). */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val folderRefresh = FolderRefresh(library, folders, shelfRepo)

    init {
        // Folders read in place show what was added to them on the phone, without asking (TM2).
        viewModelScope.launch { folderRefresh.run(inPlaceOnly = true) }
    }

    /** Pull-to-refresh: checks every imported folder, copied or read in place, for books added since (TM2). */
    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                messageChannel.send(LibraryMessage.FoldersSynced(folderRefresh.run(inPlaceOnly = false)))
            } finally {
                _refreshing.value = false
            }
        }
    }

    private suspend fun afterBatch(outcome: BatchImport.Outcome) {
        when {
            outcome.unreadable -> messageChannel.send(LibraryMessage.Unreadable)

            outcome.empty -> messageChannel.send(LibraryMessage.FolderEmpty)

            else -> messageChannel.send(
                LibraryMessage.ImportedMany(outcome.imported.size, outcome.already, outcome.failed)
            )
        }
        outcome.linkRefused?.let { showDialog(LibraryDialog.LinkNotPossible(it)) }
        // Both of these want the screen. The shelves offer waits its turn rather than being painted over by the
        // lookup question, which is what happened when they were simply shown one after the other.
        if (shelvesFromNextFolder) {
            shelvesFromNextFolder = false
            val plan = FoldersToShelves.plan(outcome.imported)
            if (plan.isNotEmpty()) {
                val existing = shelfRepo.observeShelves().first().associate { it.name to it.id }
                FoldersToShelves.apply(plan, shelfRepo, existing)
                messageChannel.send(LibraryMessage.ShelvesMade(plan.size))
            }
        } else {
            pendingShelves = shelvesOffer(outcome.imported)
        }
        val asked = outcome.imported.isNotEmpty() && lookUp(outcome.imported)
        if (!asked) showPendingShelves()
    }

    /**
     * Offers to turn the folders a batch came in into shelves (owner, M5 round 4).
     *
     * Only worth asking when the books actually sat in folders, and only until the reader has said not to ask
     * again -- which is remembered like any other thing the app shows once, so "Introduce features again" brings
     * the offer back.
     */
    private suspend fun shelvesOffer(books: List<Book>): LibraryDialog.FoldersToShelvesAsk? {
        if (FOLDER_SHELVES in settingsRepo.settings.first().seenGuides) return null
        val plan = FoldersToShelves.plan(books)
        return if (plan.isEmpty()) null else LibraryDialog.FoldersToShelvesAsk(books, plan.size)
    }

    /** Shows the shelves offer if one is waiting and nothing else is on the screen. */
    private fun showPendingShelves() {
        pendingShelves?.let {
            pendingShelves = null
            showDialog(it)
        }
    }

    /** Makes the shelves the reader agreed to, reusing any that are already there by that name. */
    fun onFoldersToShelves(dialog: LibraryDialog.FoldersToShelvesAsk, never: Boolean) = viewModelScope.launch {
        dismissDialog()
        if (never) settingsRepo.markGuideSeen(FOLDER_SHELVES)
        val existing = shelfRepo.observeShelves().first().associate { it.name to it.id }
        FoldersToShelves.apply(FoldersToShelves.plan(dialog.books), shelfRepo, existing)
    }

    /**
     * Makes shelves from one folder, asked for from its own menu rather than offered after an import.
     *
     * Every folder *inside* it gets its own shelf too, because [FoldersToShelves.plan] groups each book by the
     * folder it actually sits in. No dialog and no "don't ask again": the reader went looking for this.
     */
    fun onFolderToShelves(folder: FolderEntry) = viewModelScope.launch {
        val books = library.observeBooks(LibrarySort.RECENT).first().filter { it.isUnder(folder.path) }
        val plan = FoldersToShelves.plan(books)
        if (plan.isEmpty()) return@launch
        val existing = shelfRepo.observeShelves().first().associate { it.name to it.id }
        FoldersToShelves.apply(plan, shelfRepo, existing)
        messageChannel.send(LibraryMessage.ShelvesMade(plan.size))
    }

    /** "No thanks", with or without "and don't ask again". */
    fun onFoldersToShelvesDeclined(never: Boolean) = viewModelScope.launch {
        dismissDialog()
        if (never) settingsRepo.markGuideSeen(FOLDER_SHELVES)
    }

    /**
     * The online lookup for new books: runs when allowed, or asks once (off until agreed, ADR-0017).
     *
     * Answers whether it put a question on the screen, so whatever else wanted the screen knows to wait.
     */
    private suspend fun lookUp(books: List<Book>): Boolean {
        val enricher = BookEnricher(lookup, library)
        val ask = books.map { enricher.afterImport(it, settingsRepo) }.firstOrNull() ?: false
        if (ask) showDialog(LibraryDialog.AskLookup(books))
        return ask
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

    /** Shelf, grid or list (ADR-0052). */
    fun onViewChange(view: LibraryView) = viewModelScope.launch { settingsRepo.setLibraryView(view) }

    /** Opens or closes the stats band, or (from Settings) hides it (ADR-0052). */
    fun onStatsBand(band: StatsBand) = viewModelScope.launch { settingsRepo.setStatsBand(band) }

    fun onBookOpened(bookId: String) = viewModelScope.launch { library.markOpened(bookId) }

    /** A shelves offer held back while another question has the screen (owner, M5 round 4). */
    private var pendingShelves: LibraryDialog.FoldersToShelvesAsk? = null

    fun dismissDialog() = transient.update { it.copy(dialog = null) }

    /** The answer to [LibraryDialog.AskLookup]: remembered, and on yes the new book is looked up straight away. */
    fun onLookupAnswer(dialog: LibraryDialog.AskLookup, allow: Boolean) = viewModelScope.launch {
        dismissDialog()
        // Whatever was waiting behind this question gets its turn now (owner, M5 round 4: the shelves offer).
        showPendingShelves()
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
