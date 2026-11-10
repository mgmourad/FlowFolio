@file:Suppress("TooManyFunctions") // a header, a dialog and a row per action

package com.flowfolio.feature.library

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.ButtonStyle
import com.flowfolio.core.designsystem.DialogAction
import com.flowfolio.core.designsystem.FlatCover
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioAlertDialog
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioDialog
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioMenu
import com.flowfolio.core.designsystem.FlowFolioMenuDivider
import com.flowfolio.core.designsystem.FlowFolioMenuHeader
import com.flowfolio.core.designsystem.FlowFolioMenuItem
import com.flowfolio.core.designsystem.FlowFolioProgressBar
import com.flowfolio.core.designsystem.FlowFolioSegmented
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.Selection
import com.flowfolio.core.designsystem.color
import com.flowfolio.core.designsystem.rememberSelection
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.Shelf
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
    val onSort: (ShelfSort) -> Unit = {},
    /** The shelf's own menu: rename, colour, add books, delete; null on pages that are not a shelf. */
    val shelfMenu: ShelfPageMenu? = null
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
    var shelfEdit by remember { mutableStateOf<ShelfPageEdit?>(null) }
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
        onSort = viewModel::setSort,
        shelfMenu = state.shelf?.let {
            ShelfPageMenu(
                onRename = { shelfEdit = ShelfPageEdit.RENAME },
                onColour = { shelfEdit = ShelfPageEdit.COLOUR },
                onAddBooks = { adding = true },
                onDelete = { shelfEdit = ShelfPageEdit.DELETE }
            )
        }
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
    if (confirmBulk) {
        if (state.special == ShelfPageViewModel.FAVORITES) {
            ConfirmFavoritesDelete(selection, viewModel) { confirmBulk = false }
        } else {
            ConfirmShelfBooksDelete(selection, state, viewModel) { confirmBulk = false }
        }
    }
    removing?.let { folder -> RemoveFolderDialog(folder, { viewModel.removeFolder(folder.path) }) { removing = null } }
    state.shelf?.let { s -> ShelfPageEdits(s, shelfEdit, shelvesVm, onBack) { shelfEdit = null } }
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

/** The Favourites page's select-mode delete: take the books off Favourites, or remove them from the library. */
@Composable
private fun ConfirmFavoritesDelete(selection: Selection, viewModel: ShelfPageViewModel, onClose: () -> Unit) {
    val n = selection.chosen.size
    FlowFolioAlertDialog(
        onDismissRequest = onClose,
        title = { Text(pluralStringResource(R.plurals.delete_books_title, n, n)) },
        text = { Text(stringResource(R.string.favorites_remove_body)) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    viewModel.unfavoriteChosen(selection.chosen.toSet())
                    selection.done()
                    onClose()
                }) {
                    Text(stringResource(R.string.action_favorite_remove), style = FlowFolio.subItem)
                }
                TextButton(onClick = {
                    viewModel.deleteChosen(selection.chosen.toSet())
                    selection.done()
                    onClose()
                }) {
                    Text(stringResource(R.string.action_remove_from_library), style = FlowFolio.subItem)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.action_cancel), style = FlowFolio.hint)
            }
        }
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

@Suppress("LongMethod")
@Composable
internal fun ShelfPageContent(state: ShelfPageUiState, onBack: (() -> Unit)?, actions: BookRowActions) {
    var view by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val favorites = state.special == ShelfPageViewModel.FAVORITES
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val padding = PaddingValues(
        start = 18.dp,
        end = 18.dp,
        top = insets.calculateTopPadding(),
        bottom = insets.calculateBottomPadding() + 24.dp
    )
    // The Favourites page searches its books by title and author (owner, 2026-11-07); every other page shows all.
    val shown = remember(state.items, query) {
        if (!favorites) {
            state.items
        } else {
            val q = query.trim()
            if (q.isEmpty()) {
                state.items
            } else {
                state.items.mapNotNull { item ->
                    val score = maxOf(Fuzzy.score(q, item.book.title), Fuzzy.score(q, item.book.author))
                    if (score > 0) item to score else null
                }.sortedByDescending { it.second }.map { it.first }
            }
        }
    }
    val shownState = if (favorites) state.copy(items = shown) else state
    // Select mode chooses folders as well as books on a folder's page.
    val keys = state.subfolders.map { it.selectionId } + shown.map { item -> item.book.id }
    val header: @Composable () -> Unit = {
        if (favorites) {
            FavoritesHeader(
                state = state,
                view = view,
                onView = { view = it },
                onBack = onBack,
                searching = searching,
                onToggleSearch = {
                    searching = !searching
                    if (!searching) query = ""
                },
                query = query,
                onQuery = { query = it },
                actions = actions,
                keys = keys
            )
        } else {
            ShelfPageHeader(
                state = state,
                view = view,
                onView = { view = it },
                onBack = onBack,
                onSort = actions.onSort,
                shelfMenu = actions.shelfMenu,
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
    }
    // Books can be dragged into a new order when Custom sort is active and not in select mode.
    val canReorder =
        actions.onReorder != null && actions.selection?.active != true && state.sort == ShelfSort.CUSTOM
    if (view == 0) {
        ShelfTiles(shownState, padding, header, actions, canReorder)
    } else {
        ShelfList(shownState, padding, header, actions, canReorder)
    }
}

/** The Favourites page's header (owner, 2026-11-07): a top-level page's left title and slogan, search and select. */
@Suppress("LongMethod", "LongParameterList")
@Composable
private fun FavoritesHeader(
    state: ShelfPageUiState,
    view: Int,
    onView: (Int) -> Unit,
    onBack: (() -> Unit)?,
    searching: Boolean,
    onToggleSearch: () -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    actions: BookRowActions,
    keys: List<String>
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        val searchBtn: @Composable () -> Unit = {
            FlowFolioIconButton(
                onClick = onToggleSearch,
                modifier = Modifier.testTag(LibraryTestTags.SEARCH),
                tooltip = stringResource(R.string.action_search)
            ) {
                FlowFolioGlyph(Glyph.Search, stringResource(R.string.action_search))
            }
        }
        ScreenHeader(
            title = stringResource(R.string.shelf_favorites),
            slogan = stringResource(R.string.favourites_slogan),
            leading = onBack?.let { back ->
                {
                    FlowFolioIconButton(
                        onClick = back,
                        glass = true,
                        modifier = Modifier.padding(end = 14.dp),
                        tooltip = stringResource(R.string.action_back)
                    ) {
                        FlowFolioGlyph(Glyph.Back, stringResource(R.string.action_back), color = FlowFolio.palette.text)
                    }
                }
            },
            actions = {
                actions.selection?.let { sel -> SelectControls(sel, keys, normal = searchBtn) } ?: searchBtn()
            }
        )
        AnimatedVisibility(
            visible = searching,
            enter = expandVertically(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) +
                slideInVertically(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) { -it / SEARCH_SLIDE } +
                fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)),
            exit = shrinkVertically(tween(Motion.FAST_MS, easing = Motion.EaseInOut)) +
                fadeOut(tween(Motion.FAST_MS, easing = Motion.EaseInOut))
        ) {
            SearchField(
                query = query,
                onQuery = onQuery,
                hintRes = R.string.search_favorites_hint,
                autoFocus = true
            )
        }
        actions.selection?.let {
            SelectionRow(it, keys, actions.onDeleteChosen)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                pluralStringResource(R.plurals.book_count, state.items.size, state.items.size),
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.weight(1f)
            )
            FlowFolioSegmented(
                listOf(stringResource(R.string.view_tiles), stringResource(R.string.view_list)),
                view,
                onView,
                modifier = Modifier.testTag(LibraryTestTags.SHELF_VIEW)
            )
        }
    }
}

/** A shelf's "+" (owner, 2026-10-26): add books from the library to it. */
@Composable
private fun AddBooksButton(onClick: () -> Unit) {
    FlowFolioIconButton(
        onClick = onClick,
        modifier = Modifier.testTag(LibraryTestTags.ADD_BOOKS),
        tooltip = stringResource(R.string.action_add_books)
    ) {
        FlowFolioGlyph(Glyph.Add, contentDescription = stringResource(R.string.action_add_books))
    }
}

@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
private fun ShelfPageHeader(
    state: ShelfPageUiState,
    view: Int,
    onView: (Int) -> Unit,
    onBack: (() -> Unit)?,
    onSort: (ShelfSort) -> Unit,
    selectionRow: @Composable () -> Unit,
    shelfMenu: ShelfPageMenu?,
    select: @Composable () -> Unit
) {
    val p = FlowFolio.palette
    val name = state.folder?.substringAfterLast('/') ?: state.shelf?.name ?: stringResource(
        when {
            state.special == ShelfPageViewModel.FINISHED -> R.string.shelf_finished
            state.special == ShelfPageViewModel.FAVORITES -> R.string.shelf_favorites
            state.noShelves -> R.string.shelf_all_books
            else -> R.string.shelf_loose
        }
    )
    val wide = libraryWide()
    // ADR-0052: Back on the left, Sort and view, Add books and the shelf's ⋯ on the right; then the shelf's colour
    // dot and name, and what it holds.
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            onBack?.let { back ->
                FlowFolioIconButton(onClick = back, tooltip = stringResource(R.string.action_back)) {
                    FlowFolioGlyph(Glyph.Back, null, size = 20.dp)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ShelfSortViewMenu(state.canSort, state.sort, onSort, view, onView)
                select()
                shelfMenu?.let { ShelfPageMoreMenu(it) }
            }
        }
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            state.shelf?.let { shelf ->
                Box(
                    Modifier
                        .padding(end = 10.dp)
                        .size(14.dp)
                        .background(shelf.color?.color ?: p.accent, CircleShape)
                )
            }
            Text(
                name,
                style = FlowFolioType.screenTitle.copy(
                    fontSize = if (wide) 40.sp else 30.sp,
                    lineHeight = if (wide) 42.sp else 32.sp,
                    color = p.ink
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            listOfNotNull(
                state.subfolders.size.takeIf { it > 0 }?.let {
                    pluralStringResource(R.plurals.folder_count, it, it)
                },
                pluralStringResource(R.plurals.book_count, state.items.size, state.items.size)
                    .takeIf { state.items.isNotEmpty() || state.subfolders.isEmpty() }
            ).joinToString(" · "),
            style = FlowFolioType.sub.copy(color = p.ink2),
            modifier = Modifier.padding(top = 6.dp)
        )
        selectionRow()
    }
}

/** A shelf's own menu on its page (ADR-0052). */
internal class ShelfPageMenu(
    val onRename: () -> Unit,
    val onColour: () -> Unit,
    val onAddBooks: () -> Unit,
    val onDelete: () -> Unit
)

@Composable
private fun ShelfPageMoreMenu(menu: ShelfPageMenu) {
    var open by remember { mutableStateOf(false) }
    fun run(action: () -> Unit): () -> Unit = {
        open = false
        action()
    }
    Box {
        FlowFolioIconButton(onClick = { open = true }, tooltip = stringResource(R.string.shelf_options)) {
            FlowFolioGlyph(Glyph.More, null, size = 20.dp)
        }
        FlowFolioMenu(expanded = open, onDismissRequest = { open = false }) {
            FlowFolioMenuItem(stringResource(R.string.action_rename), run(menu.onRename), glyph = Glyph.Edit)
            FlowFolioMenuItem(stringResource(R.string.shelf_colour), run(menu.onColour), glyph = Glyph.Paper)
            FlowFolioMenuItem(stringResource(R.string.action_add_books), run(menu.onAddBooks), glyph = Glyph.Add)
            FlowFolioMenuDivider()
            FlowFolioMenuItem(
                stringResource(R.string.shelf_delete),
                run(menu.onDelete),
                glyph = Glyph.Trash,
                destructive = true
            )
        }
    }
}

/** Sort and view on a shelf page (ADR-0052): the shelf's orders where it can be sorted, then Grid or List. */
@Composable
private fun ShelfSortViewMenu(
    canSort: Boolean,
    current: ShelfSort,
    onSort: (ShelfSort) -> Unit,
    view: Int,
    onView: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FlowFolioIconButton(
            onClick = { open = true },
            tooltip = stringResource(R.string.library_sort_view),
            modifier = Modifier.testTag(LibraryTestTags.SHELF_SORT)
        ) {
            FlowFolioGlyph(Glyph.Sort, null, size = 20.dp)
        }
        FlowFolioMenu(expanded = open, onDismissRequest = { open = false }) {
            if (canSort) {
                FlowFolioMenuHeader(stringResource(R.string.library_sort_by))
                ShelfSort.entries.forEach { sort ->
                    FlowFolioMenuItem(stringResource(sort.labelRes()), {
                        open = false
                        onSort(sort)
                    }, checked = sort == current)
                }
                FlowFolioMenuDivider()
            }
            FlowFolioMenuHeader(stringResource(R.string.library_view_as))
            listOf(R.string.view_grid to Glyph.Library, R.string.view_list to Glyph.Contents).forEachIndexed {
                    i,
                    (label, glyph)
                ->
                FlowFolioMenuItem(stringResource(label), {
                    open = false
                    onView(i)
                }, glyph = glyph, checked = view == i)
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

/** How far the search field rises from when it opens (the same drop as the other pages, owner 2026-11-07). */
private const val SEARCH_SLIDE = 3

/** 30% bigger than the shelf page's first tiles (96 x 136 dp; owner, 2026-10-26). */
internal val TILE_BOOK_WIDTH = 125.dp
internal val TILE_BOOK_HEIGHT = 177.dp

/** A cell just holds a book and its spine, so a row takes as many books as fit (five on the open Fold). */
internal val TILE_CELL = TILE_BOOK_WIDTH + BOOK_SPINE_ROOM

internal val TILE_COLUMN_GAP = 12.dp
internal val TILE_ROW_GAP = 28.dp

/** What is being changed about the shelf itself from its page. */
private enum class ShelfPageEdit { RENAME, COLOUR, DELETE }

/** Rename, Change colour and Delete shelf from a shelf's page; deleting leaves the page (the books stay). */
@Composable
private fun ShelfPageEdits(
    shelf: Shelf,
    edit: ShelfPageEdit?,
    shelvesVm: ShelvesViewModel,
    onBack: (() -> Unit)?,
    onDone: () -> Unit
) {
    when (edit) {
        null -> Unit

        ShelfPageEdit.RENAME -> ShelfNameColourDialog(
            title = stringResource(R.string.shelf_rename),
            confirm = stringResource(R.string.action_save),
            initialName = shelf.name,
            initialColour = null,
            onConfirm = { name, _ ->
                shelvesVm.rename(shelf.id, name)
                onDone()
            },
            onDismiss = onDone
        )

        ShelfPageEdit.COLOUR -> ShelfColourDialog(shelf, onPick = {
            shelvesVm.setColor(shelf.id, it)
            onDone()
        }, onDismiss = onDone)

        ShelfPageEdit.DELETE -> FlowFolioDialog(
            onDismissRequest = onDone,
            title = stringResource(R.string.shelf_delete_title, shelf.name),
            body = stringResource(R.string.shelf_delete_body),
            glyph = Glyph.Trash,
            destructive = true,
            actions = listOf(
                DialogAction(stringResource(R.string.action_cancel)) { onDone() },
                DialogAction(stringResource(R.string.action_delete), ButtonStyle.DANGER) {
                    shelvesVm.delete(shelf.id)
                    onDone()
                    onBack?.invoke()
                }
            )
        )
    }
}
