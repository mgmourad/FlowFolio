package com.flowfolio.feature.library

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlatCover
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioAlertDialog
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioMenu
import com.flowfolio.core.designsystem.FlowFolioProgressBar
import com.flowfolio.core.designsystem.FlowFolioSegmented
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Selection
import com.flowfolio.core.designsystem.rememberSelection
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.pageLabel
import com.flowfolio.core.model.printedPages

/** What is being asked about a book on the shelf page. */
private sealed interface BookPrompt {
    val book: Book

    data class Rename(override val book: Book) : BookPrompt

    data class Delete(override val book: Book) : BookPrompt

    data class Shelves(override val book: Book) : BookPrompt
}

/** Everything a book on the shelf page can do. */
internal data class BookRowActions(
    val onOpen: (LibraryItem) -> Unit,
    val onRename: (Book) -> Unit,
    val onDelete: (Book) -> Unit,
    val onShelves: (Book) -> Unit,
    val onLocate: (Book) -> Unit,
    /** The book's ribbons (its Bookmarks page). */
    val onBookmarks: (Book) -> Unit = {},
    /** Turns a PDF's Dynamic Flow on or off (ADR-0045); null hides it. */
    val onFlow: ((Book) -> Unit)? = null,
    /** Makes a book a favourite, or not; marks it finished, or not (owner, M3T2). */
    val onFavorite: ((Book) -> Unit)? = null,
    val onFinished: ((Book) -> Unit)? = null,
    /** Select mode (owner, 2026-10-22); null where books cannot be selected. */
    val selection: Selection? = null,
    val onDeleteChosen: () -> Unit = {},
    /** On a folder's page: opening a folder inside it, and asking to remove one. */
    val folders: FolderActions = FolderActions(onOpen = {}, onRemove = {}),
    /** A shelf's "+": add books to it; null on pages that are not a shelf. */
    val onAddBooks: (() -> Unit)? = null,
    /** Select mode's "Add to shelf" for the chosen books. */
    val onShelveChosen: (() -> Unit)? = null,
    /** Saves the shelf's new order after a drag and drop; null where books keep their order. */
    val onReorder: ((List<String>) -> Unit)? = null,
    /** The drag and drop under way in the current view (set by [ShelfPageContent]). */
    val reorder: ReorderState? = null,
    /** Changes the sort order on a shelf or the 'Not on a shelf' page. */
    val onSort: (ShelfSort) -> Unit = {}
)

/**
 * A whole shelf (opened from its book count in the library): its books as 3D tiles, or as a list for fast scrolling
 * whose rows swipe to show more actions.
 */
@Suppress("LongMethod")
@Composable
fun ShelfPageScreen(
    onBack: (() -> Unit)?,
    onOpenDetails: (Book, String) -> Unit,
    onBookmarks: (Book) -> Unit = {},
    onOpenFolder: (String) -> Unit = {},
    viewModel: ShelfPageViewModel = hiltViewModel(),
    shelvesVm: ShelvesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var prompt by remember { mutableStateOf<BookPrompt?>(null) }
    val selection = rememberSelection()
    var confirmBulk by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<FolderEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var shelving by remember { mutableStateOf<List<String>?>(null) }
    val relink = rememberRelink(viewModel)
    val actions = BookRowActions(
        onOpen = { item ->
            if (item.availability ==
                Availability.MISSING
            ) {
                relink(item.book.id)
            } else {
                onOpenDetails(item.book, SHELF_PAGE_ORIGIN)
            }
        },
        onRename = { prompt = BookPrompt.Rename(it) },
        onDelete = { prompt = BookPrompt.Delete(it) },
        onShelves = { prompt = BookPrompt.Shelves(it) },
        onLocate = { relink(it.id) },
        onBookmarks = onBookmarks,
        onFlow = rememberDynamicFlowSwitch(viewModel),
        onFavorite = viewModel::toggleFavorite,
        onFinished = viewModel::toggleFinished,
        selection = selection,
        onDeleteChosen = { confirmBulk = true },
        folders = FolderActions(
            onOpen = onOpenFolder,
            onRemove = { removing = it },
            onMakeShelves = { folder ->
                shelvesVm.foldersToShelves(state.items.map { it.book }.filter { it.isUnder(folder.path) })
            },
            selection = selection
        ),
        onAddBooks = state.shelf?.let { { adding = true } },
        onReorder = when {
            state.shelf != null -> { ids: List<String> ->
                shelvesVm.setOrder(state.shelf!!.id, ids)
                Unit
            }

            state.canSort -> viewModel::setLooseOrder

            else -> null
        },
        onShelveChosen = { shelving = state.booksChosen(selection.chosen) },
        onSort = viewModel::setSort
    )
    ShelfPageContent(state, onBack, actions)
    val shelf = state.shelf
    if (adding && shelf != null) {
        AddBooksDialog(shelf, state.allBooks, onConfirm = {
            shelvesVm.addBooks(shelf.id, it)
            adding = false
        }, onDismiss = { adding = false })
    }
    shelving?.let { ids ->
        ShelveBooksFlow(ids, state.shelves, shelvesVm) {
            shelving = null
            selection.done()
        }
    }
    if (confirmBulk) ConfirmShelfBooksDelete(selection, state, viewModel) { confirmBulk = false }
    removing?.let { folder -> RemoveFolderDialog(folder, { viewModel.removeFolder(folder.path) }) { removing = null } }
    BackHandler(selection.active) { selection.done() }
    BookPromptDialogs(prompt, state, viewModel, shelvesVm) { prompt = null }
}

/** Finding a missing book again: picks its file, then points the book at it. */
@Composable
private fun rememberRelink(viewModel: ShelfPageViewModel): (String) -> Unit {
    var relinkId by rememberSaveable { mutableStateOf<String?>(null) }
    val relink = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = relinkId
        if (uri != null && id != null) viewModel.relink(id, uri.toString())
        relinkId = null
    }
    return { id ->
        relinkId = id
        relink.launch(arrayOf("application/pdf", "application/epub+zip"))
    }
}

/** Asks before select mode removes the chosen books from the library. */
@Composable
private fun ConfirmShelfBooksDelete(
    selection: Selection,
    state: ShelfPageUiState,
    viewModel: ShelfPageViewModel,
    onClose: () -> Unit
) {
    // A chosen folder counts every book in it.
    val n = selection.chosen.sumOf { key -> state.subfolders.firstOrNull { it.selectionId == key }?.bookCount ?: 1 }
    BulkDeleteDialog(
        pluralStringResource(R.plurals.delete_books_title, n, n),
        stringResource(R.string.delete_books_body),
        onConfirm = {
            viewModel.deleteChosen(selection.chosen.toSet())
            selection.done()
            onClose()
        },
        onDismiss = onClose
    )
}

/** The dialog for what is being asked about a book: rename it, remove it, or choose its shelves. */
@Composable
private fun BookPromptDialogs(
    prompt: BookPrompt?,
    state: ShelfPageUiState,
    viewModel: ShelfPageViewModel,
    shelvesVm: ShelvesViewModel,
    onDone: () -> Unit
) {
    when (prompt) {
        null -> Unit

        is BookPrompt.Rename -> ShelfNameDialog(
            title = stringResource(R.string.rename_book),
            initial = prompt.book.title,
            hint = stringResource(R.string.book_title_hint),
            onConfirm = {
                viewModel.rename(prompt.book.id, it)
                onDone()
            },
            onDismiss = { onDone() }
        )

        is BookPrompt.Delete -> FlowFolioAlertDialog(
            onDismissRequest = { onDone() },
            title = { Text(stringResource(R.string.delete_title, prompt.book.title)) },
            text = {
                val copied = prompt.book.storage is BookStorage.Copied
                val body = if (copied) R.string.delete_body_copy else R.string.delete_body_link
                Text(stringResource(body))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(prompt.book.id)
                    onDone()
                }) { Text(stringResource(R.string.action_delete), style = FlowFolio.subItem) }
            },
            dismissButton = {
                TextButton(onClick = { onDone() }) {
                    Text(stringResource(R.string.action_cancel), style = FlowFolio.hint)
                }
            }
        )

        is BookPrompt.Shelves -> ShelveBookFlow(prompt.book, state.shelves, shelvesVm) { onDone() }
    }
}

@Composable
internal fun ShelfPageContent(state: ShelfPageUiState, onBack: (() -> Unit)?, actions: BookRowActions) {
    var view by rememberSaveable { mutableIntStateOf(0) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val padding = PaddingValues(
        start = 18.dp,
        end = 18.dp,
        top = insets.calculateTopPadding(),
        bottom = insets.calculateBottomPadding() + 24.dp
    )
    // Select mode chooses folders as well as books on a folder's page.
    val keys = state.subfolders.map { it.selectionId } + state.items.map { item -> item.book.id }
    val header: @Composable () -> Unit = {
        ShelfPageHeader(
            state = state,
            view = view,
            onView = { view = it },
            onBack = onBack,
            onSort = actions.onSort,
            selectionRow = {
                actions.selection?.let {
                    SelectionRow(it, keys, actions.onDeleteChosen, onAddToShelf = actions.onShelveChosen)
                }
            }
        ) {
            actions.selection?.let {
                SelectControls(it, keys) { actions.onAddBooks?.let { add -> AddBooksButton(add) } }
            }
        }
    }
    // Books can be dragged into a new order when Custom sort is active and not in select mode.
    val canReorder =
        actions.onReorder != null && actions.selection?.active != true && state.sort == ShelfSort.CUSTOM
    if (view == 0) {
        ShelfTiles(state, padding, header, actions, canReorder)
    } else {
        ShelfList(state, padding, header, actions, canReorder)
    }
}

/** A shelf's "+" (owner, 2026-10-26): add books from the library to it. */
@Composable
private fun AddBooksButton(onClick: () -> Unit) {
    FlowFolioIconButton(onClick = onClick, modifier = Modifier.testTag(LibraryTestTags.ADD_BOOKS)) {
        FlowFolioGlyph(Glyph.Add, contentDescription = stringResource(R.string.action_add_books))
    }
}

@Suppress("LongParameterList")
@Composable
private fun ShelfPageHeader(
    state: ShelfPageUiState,
    view: Int,
    onView: (Int) -> Unit,
    onBack: (() -> Unit)?,
    onSort: (ShelfSort) -> Unit,
    selectionRow: @Composable () -> Unit,
    select: @Composable () -> Unit
) {
    val name = state.folder?.substringAfterLast('/') ?: state.shelf?.name ?: stringResource(
        when {
            state.special == ShelfPageViewModel.FINISHED -> R.string.shelf_finished
            state.special == ShelfPageViewModel.FAVORITES -> R.string.shelf_favorites
            state.noShelves -> R.string.shelf_all_books
            else -> R.string.shelf_loose
        }
    )
    // The page's name, the line under it saying what it holds, and the + / Select controls on the slogan's line
    // (owner, M4b/M4c), as every other top-level page wears it.
    val slogan = stringResource(state.sloganRes())
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        ScreenHeader(
            title = name,
            slogan = slogan,
            leading = onBack?.let { back ->
                {
                    FlowFolioIconButton(onClick = back, modifier = Modifier.padding(end = 10.dp)) {
                        FlowFolioGlyph(Glyph.Back, contentDescription = stringResource(R.string.action_back))
                    }
                }
            },
            actions = select
        )
        selectionRow()
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                listOfNotNull(
                    state.subfolders.size.takeIf { it > 0 }?.let {
                        pluralStringResource(R.plurals.folder_count, it, it)
                    },
                    // A folder holding only folders says just that.
                    pluralStringResource(R.plurals.book_count, state.items.size, state.items.size)
                        .takeIf { state.items.isNotEmpty() || state.subfolders.isEmpty() }
                ).joinToString(" · "),
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.weight(1f)
            )
            if (state.canSort) {
                ShelfSortButton(current = state.sort, onSelect = onSort)
            }
            FlowFolioSegmented(
                listOf(stringResource(R.string.view_tiles), stringResource(R.string.view_list)),
                view,
                onView,
                modifier = Modifier.testTag(LibraryTestTags.SHELF_VIEW)
            )
        }
    }
}

@Composable
private fun ShelfSortButton(current: ShelfSort, onSelect: (ShelfSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val p = FlowFolio.palette
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(p.surface)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .testTag(LibraryTestTags.SHELF_SORT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            FlowFolioGlyph(
                Glyph.Sliders,
                contentDescription = stringResource(R.string.action_sort),
                color = p.glyph,
                size = 15.dp
            )
            Text(
                stringResource(current.labelRes()),
                style = FlowFolioType.pill.merge(FlowFolio.item),
                maxLines = 1
            )
        }
        FlowFolioMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ShelfSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(sort.labelRes()),
                            style = if (sort == current) FlowFolio.title else FlowFolio.item
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(sort)
                    }
                )
            }
        }
    }
}

private fun ShelfSort.labelRes(): Int = when (this) {
    ShelfSort.CUSTOM -> R.string.sort_custom
    ShelfSort.NAME -> R.string.sort_name
    ShelfSort.DATE_ADDED -> R.string.sort_date_added
    ShelfSort.PROGRESS -> R.string.sort_progress
    ShelfSort.READ_TIME -> R.string.sort_read_time
    ShelfSort.READ_GOAL -> R.string.sort_read_goal
}

internal const val SHELF_PAGE_ORIGIN = "shelfpage"

/** 30% bigger than the shelf page's first tiles (96 x 136 dp; owner, 2026-10-26). */
internal val TILE_BOOK_WIDTH = 125.dp
internal val TILE_BOOK_HEIGHT = 177.dp

/** A cell just holds a book and its spine, so a row takes as many books as fit (five on the open Fold). */
internal val TILE_CELL = TILE_BOOK_WIDTH + BOOK_SPINE_ROOM

internal val TILE_COLUMN_GAP = 12.dp
internal val TILE_ROW_GAP = 28.dp

/** The line under a shelf page's name, saying what the page holds (owner, M4b). */
private fun ShelfPageUiState.sloganRes(): Int = when {
    folder != null -> R.string.folder_slogan
    special == ShelfPageViewModel.FINISHED -> R.string.finished_slogan
    special == ShelfPageViewModel.FAVORITES -> R.string.favourites_slogan
    noShelves -> R.string.all_books_slogan
    shelf != null -> R.string.shelf_slogan
    else -> R.string.loose_slogan
}
