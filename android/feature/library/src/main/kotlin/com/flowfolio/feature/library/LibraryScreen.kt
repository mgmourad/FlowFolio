package com.flowfolio.feature.library

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.CoverSpot
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.LocalBookOpener
import com.flowfolio.core.designsystem.LocalChromePadding
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.ScrollToTopOnTab
import com.flowfolio.core.designsystem.Selection
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.LibrarySort

/** What the file picker offers: PDFs and EPUBs (Phase 2). */
private val PDF_TYPES = arrayOf("application/pdf", "application/epub+zip")

/** What the library screen can ask for. [onOpen] gets the row the book was tapped in, for the cover flight. */
data class LibraryActions(
    /** Picks files to import (one or several). */
    val onImport: () -> Unit = {},
    /** Picks a folder to import, with every folder inside it. */
    val onImportFolder: () -> Unit = {},
    /** Makes a shelf from a folder and from every folder inside it. */
    val onFolderToShelves: (FolderEntry) -> Unit = {},
    val onOpen: (LibraryItem, String) -> Unit = { _, _ -> },
    val onResume: (LibraryItem) -> Unit = {},
    val onDelete: (Book) -> Unit = {},
    val onLocate: (Book) -> Unit = {},
    val onAddToShelf: (Book) -> Unit = {},
    val onRename: ((Book) -> Unit)? = null,
    /** Turns a PDF's Dynamic Flow on or off (ADR-0045); null hides the menu entry. */
    val onFlow: ((Book) -> Unit)? = null,
    /** Makes a book a favourite, or not; marks it finished, or not (owner, M3T2). */
    val onFavorite: ((Book) -> Unit)? = null,
    val onFinished: ((Book) -> Unit)? = null,
    /** The book's ribbons; null hides the menu entry. */
    val onBookmarks: ((Book) -> Unit)? = null,
    /** The book's highlights and notes; null hides the Continue Reading shortcut and menu entry. */
    val onAnnotations: ((Book) -> Unit)? = null,
    /** The book's dictionary entries; null hides the menu entry. */
    val onDictionary: ((Book) -> Unit)? = null,
    /** Opens the Reading Goal dialog for a specific book; null hides the menu entry. */
    val onSetBookGoal: ((Book) -> Unit)? = null,
    /** Opens an imported folder's page, by its path. */
    val onOpenFolder: (String) -> Unit = {},
    /** Asks to remove a folder's books from the library. */
    val onRemoveFolder: (FolderEntry) -> Unit = {},
    /** Saves a shelf's new order after a drag and drop; null where books cannot be reordered (a filtered view). */
    val onReorder: ((Long, List<String>) -> Unit)? = null,
    /** Opens a whole shelf ([ShelfPageViewModel.LOOSE] for the books on no shelf). */
    val onOpenShelf: (Long) -> Unit = {},
    val onReadingGoal: () -> Unit = {},
    val onSort: (LibrarySort) -> Unit = {},
    val onShelfSort: (ShelfSort) -> Unit = {},
    val onFilter: (LibraryFilter) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    /** Where the "Continue reading" cover is, so Resume opens the book from it. */
    val heroSpot: CoverSpot = CoverSpot(),
    /** Select mode (owner, 2026-10-22); null where books cannot be selected. */
    val selection: Selection? = null,
    /** Asks to remove the chosen books. */
    val onDeleteChosen: () -> Unit = {}
)

/** Library route: wires the ViewModels, file pickers, snackbar messages and shelf dialogs to [LibraryContent]. */
@Suppress("LongParameterList", "LongMethod")
@Composable
fun LibraryScreen(
    onOpenDetails: (Book, String) -> Unit,
    onRead: (Book) -> Unit,
    onOpenShelf: (Long) -> Unit = {},
    onOpenFolder: (String) -> Unit = {},
    onBookmarks: (Book) -> Unit = {},
    onAnnotations: (Book) -> Unit = {},
    onDictionary: (Book) -> Unit = {},
    scrollToShelf: Long? = null,
    onScrolledToShelf: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
    shelves: ShelvesViewModel = hiltViewModel(),
    statsViewModel: LibraryStatsViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.query) { searchViewModel.onQuery(state.query) }
    // The app introduces itself once, when the library first opens (owner, M5). The one-line welcome stays out of
    // the way while the full tour is still owed, so the two never stack on top of each other (owner, M5 round 4).
    val guideSettings by viewModel.guideSettings.collectAsStateWithLifecycle()
    guideSettings?.takeIf { TOUR_SEEN in it.seenGuides }
        ?.let { GuideSplash(Guide.Welcome, it, viewModel::markGuideSeen) { viewModel.disableTips(Guide.Welcome.id) } }
    val found by searchViewModel.results.collectAsStateWithLifecycle()
    var shelving by remember { mutableStateOf<Book?>(null) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.onFilesPicked(uris.map { it.toString() })
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onFolderPicked(it.toString()) }
    }
    val locate = rememberLocate(viewModel)
    val opener = LocalBookOpener.current
    val heroSpot = remember { CoverSpot() }
    val stats = rememberLibraryStats(statsViewModel, state.continueReading?.book?.id)
    val setBookGoal = rememberLibraryBookGoalDialog(statsViewModel)
    LibraryContent(
        state,
        rememberMessages(viewModel),
        stats,
        LibraryActions(
            onImport = { importPicker.launch(PDF_TYPES) },
            onImportFolder = { folderPicker.launch(null) },
            onFolderToShelves = viewModel::onFolderToShelves,
            onOpen = { item, origin ->
                if (item.availability == Availability.MISSING) locate(item.book) else onOpenDetails(item.book, origin)
            },
            onResume = { item ->
                if (item.availability == Availability.MISSING) {
                    locate(item.book)
                } else {
                    viewModel.onBookOpened(item.book.id)
                    opener?.open(heroSpot) { onRead(item.book) } ?: onRead(item.book)
                }
            },
            onDelete = viewModel::onRequestDelete,
            onLocate = locate,
            onAddToShelf = { shelving = it },
            onFlow = rememberDynamicFlowSwitch(viewModel),
            onFavorite = viewModel::toggleFavorite,
            onFinished = viewModel::toggleFinished,
            onOpenShelf = onOpenShelf,
            onReadingGoal = rememberLibraryGoalDialog(statsViewModel, stats, state, onOpenDetails),
            onOpenFolder = onOpenFolder,
            onRemoveFolder = rememberRemoveFolder(viewModel),
            onReorder = { id: Long, ids: List<String> -> shelves.setOrder(id, ids).let {} }
                .takeIf { state.filter == LibraryFilter.ALL && state.query.isBlank() },
            onBookmarks = onBookmarks,
            onAnnotations = onAnnotations,
            onDictionary = onDictionary,
            onSetBookGoal = setBookGoal,
            onSort = viewModel::onSortChange,
            onShelfSort = viewModel::onShelfSortChange,
            onFilter = viewModel::onFilter,
            onQuery = viewModel::onQuery,
            heroSpot = heroSpot
        ),
        found,
        scrollToShelf,
        onScrolledToShelf
    )
    LibraryDialogs(state.dialog, viewModel)
    shelving?.let { ShelveBookFlow(it, state.shelves, shelves) { shelving = null } }
}

/** The snackbar the library's messages show in. */
@Composable
private fun rememberMessages(viewModel: LibraryViewModel): SnackbarHostState {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it.text(context)) }
    }
    return snackbar
}

/** Remove from library on a folder: asks first, then removes every book in it. */
@Composable
private fun rememberRemoveFolder(viewModel: LibraryViewModel): (FolderEntry) -> Unit {
    var removing by remember { mutableStateOf<FolderEntry?>(null) }
    removing?.let { folder ->
        RemoveFolderDialog(folder, onConfirm = { viewModel.onRemoveFolder(folder.path) }) { removing = null }
    }
    return { removing = it }
}

/** Locate for a moved or missing book: the system picker, then the book is pointed at the file chosen. */
@Composable
private fun rememberLocate(viewModel: LibraryViewModel): (Book) -> Unit {
    var relinkBookId by rememberSaveable { mutableStateOf<String?>(null) }
    val relinkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = relinkBookId
        if (uri != null && id != null) viewModel.onRelinkPicked(id, uri.toString())
        relinkBookId = null
    }
    return { book: Book ->
        relinkBookId = book.id
        relinkPicker.launch(PDF_TYPES)
    }
}

/** The library as in the mock: header, chips, "Continue reading" and a shelf row per shelf, on the app background. */
@Suppress("LongMethod")
@Composable
fun LibraryContent(
    state: LibraryUiState,
    snackbar: SnackbarHostState,
    stats: LibraryStats = LibraryStats(),
    actions: LibraryActions,
    search: SearchResults = SearchResults(),
    scrollToShelf: Long? = null,
    onScrolledToShelf: () -> Unit = {}
) {
    val chrome = LocalChromePadding.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    var searching by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
    val list = rememberLazyListState()
    // A tap on the Library tab shows the page from the top (owner, M4c).
    ScrollToTopOnTab("library", list)
    val expanded = remember { mutableStateOf(emptySet<SearchGroup>()) }
    // Shelves screen -> "show this shelf": scroll its row into view once the rows are there.
    LaunchedEffect(scrollToShelf, state.loading, state.shelves) {
        val target = scrollToShelf ?: return@LaunchedEffect
        if (state.loading) return@LaunchedEffect
        val index = rowIndex(state, target)
        if (index != null) list.animateScrollToItem(index)
        onScrolledToShelf()
    }
    Box(Modifier.fillMaxSize().chromeStart()) {
        LazyColumn(
            Modifier.fillMaxSize().testTag(LibraryTestTags.GRID),
            state = list,
            contentPadding = PaddingValues(
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding() + chrome.calculateBottomPadding() + LIST_END
            )
        ) {
            item(key = "header") {
                LibraryHeader(
                    state = state,
                    stats = stats,
                    searching = searching,
                    onSearch = {
                        searching = !searching
                        if (!searching) actions.onQuery("")
                    },
                    actions = actions,
                    modifier = Modifier.reveal(0)
                )
            }
            // Always emitted so that it can animate: it grows, slides and fades in rather than appearing whole,
            // and takes the cursor with it (owner, M4).
            item(key = "search") {
                AnimatedVisibility(
                    visible = searching,
                    enter = expandVertically(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) +
                        slideInVertically(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) { -it / SLIDE_FROM } +
                        fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)),
                    exit = shrinkVertically(tween(Motion.FAST_MS, easing = Motion.EaseInOut)) +
                        fadeOut(tween(Motion.FAST_MS, easing = Motion.EaseInOut))
                ) {
                    SearchField(state.query, actions.onQuery, autoFocus = true)
                }
            }
            if (state.importing) item(key = "importing") { ImportProgress(state.importProgress) }
            // While a query is typed, everything it finds takes the shelves' place (owner, M3T2).
            if (searching && state.query.isNotBlank()) {
                searchResults(search, actions, expanded)
            } else {
                libraryBody(state, actions, stats)
            }
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = insets.calculateBottomPadding() + chrome.calculateBottomPadding())
        )
    }
}

/** The list index of [shelfId]'s row, matching the items [LibraryContent] emits; null when it has no row. */
private fun rowIndex(state: LibraryUiState, shelfId: Long): Int? {
    val rows = shelfRows(state.items, state.shelves, state.sortRank)
    val row = rows.indexOfFirst { it.shelf?.id == shelfId }.takeIf { it >= 0 } ?: return null
    val hero = state.continueReading != null && state.filter == LibraryFilter.ALL && state.query.isBlank()
    // Header, search field, import progress, chips and the hero card come before the rows. Since M4 the search
    // row is always emitted (it animates open), so it always counts.
    val before = listOf(true, true, state.importing, true, hero).count { it }
    return before + row
}

private fun androidx.compose.foundation.lazy.LazyListScope.libraryBody(
    state: LibraryUiState,
    actions: LibraryActions,
    stats: LibraryStats
) {
    when {
        state.loading -> Unit

        state.libraryEmpty -> item(key = "empty") { EmptyLibrary(actions) }

        else -> {
            item(key = "chips") { FilterChips(state.filters, state.filter, actions.onFilter, Modifier.reveal(1)) }
            if (state.filter == LibraryFilter.FOLDERS) {
                folderRoots(state, actions)
                return
            }
            if (state.filter == LibraryFilter.FINISHED) {
                item(key = "finished") { FinishedTiles(state.items, actions, Modifier.reveal(2)) }
                return
            }
            val hero = state.continueReading
            if (hero != null && state.filter == LibraryFilter.ALL && state.query.isBlank()) {
                // The shelf its shortcut opens: the first it is on, or the books on no shelf.
                val shelf = state.shelves.firstOrNull { hero.book.id in it.bookIds }?.id ?: ShelfPageViewModel.LOOSE
                val goalProgress = stats.heroGoal?.progress(stats.heroReadMillis)
                    ?: stats.libraryGoal?.progress(stats.periodReadMillis)
                item(key = "hero") {
                    ContinueReadingCard(
                        hero,
                        actions,
                        shelf,
                        stats.heroReadMillis,
                        Modifier.reveal(2),
                        bookmarkCount = stats.heroBookmarks,
                        annotationCount = stats.heroAnnotations,
                        dictionaryCount = stats.heroDictionary,
                        readGoalProgress = goalProgress
                    )
                }
            }
            val rows = shelfRows(state.items, state.shelves, state.sortRank)
            if (rows.isEmpty()) item(key = "nomatch") { NoMatch() }
            itemsIndexed(rows, key = { _, r -> "row-" + (r.shelf?.id ?: "loose") }) { i, row ->
                ShelfRowView(
                    row,
                    noShelves = state.shelves.isEmpty(),
                    actions = actions,
                    modifier = Modifier.animateItem(),
                    order = ROWS_REVEAL_ORDER + i
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(actions: LibraryActions) {
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.library_empty),
            style = FlowFolioType.prose.merge(FlowFolio.hint),
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(LibraryTestTags.EMPTY)
        )
        FlowFolioPrimaryButton(
            text = stringResource(R.string.import_files),
            onClick = actions.onImport,
            modifier = Modifier.padding(top = 20.dp)
        )
        com.flowfolio.core.designsystem.FlowFolioGhostButton(
            text = stringResource(R.string.import_folder),
            onClick = actions.onImportFolder,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
internal fun NoMatch() {
    Text(
        stringResource(R.string.library_no_match),
        style = FlowFolioType.sub.merge(FlowFolio.hint),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp)
    )
}

/** The search box slides down from a third of its own height. */
private const val SLIDE_FROM = 3

private val LIST_END = 24.dp

/** Shelf rows reveal after the header, chips and hero card (orders 0 to 2). */
private const val ROWS_REVEAL_ORDER = 3

@Suppress("CyclomaticComplexMethod") // one branch per message
internal fun LibraryMessage.text(context: android.content.Context): String = when (this) {
    is LibraryMessage.Imported -> context.getString(R.string.msg_imported, title)

    is LibraryMessage.Duplicate -> context.getString(R.string.msg_duplicate, title)

    is LibraryMessage.ImportedMany -> listOfNotNull(
        context.resources.getQuantityString(R.plurals.msg_added_books, imported, imported),
        already.takeIf { it > 0 }?.let { context.getString(R.string.msg_already_count, it) },
        failed.takeIf { it > 0 }?.let { context.getString(R.string.msg_failed_count, it) }
    ).joinToString(" · ")

    LibraryMessage.FolderEmpty -> context.getString(R.string.msg_folder_empty)

    LibraryMessage.NotABook -> context.getString(R.string.msg_not_a_pdf)

    LibraryMessage.DrmProtected -> context.getString(R.string.msg_drm)

    LibraryMessage.Unreadable -> context.getString(R.string.msg_unreadable)

    is LibraryMessage.NotEnoughSpace -> context.getString(
        R.string.msg_no_space,
        Formatter.formatShortFileSize(context, bytes)
    )

    LibraryMessage.Relinked -> context.getString(R.string.msg_relinked)

    LibraryMessage.RelinkDifferentFile -> context.getString(R.string.msg_relink_different)

    LibraryMessage.RelinkFailed -> context.getString(R.string.msg_relink_failed)

    is LibraryMessage.Deleted -> context.getString(R.string.msg_deleted, title)

    is LibraryMessage.ShelvesMade ->
        context.resources.getQuantityString(R.plurals.msg_shelves_made, shelves, shelves)
}
