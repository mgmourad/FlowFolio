package com.flowfolio.feature.library

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlatCover
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioAlertDialog
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioProgressBar
import com.flowfolio.core.designsystem.FlowFolioSegmented
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Selection
import com.flowfolio.core.designsystem.rememberSelection
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
    /** Select mode (owner, 2026-10-22); null where books cannot be selected. */
    val selection: Selection? = null,
    val onDeleteChosen: () -> Unit = {}
)

/**
 * A whole shelf (opened from its book count in the library): its books as 3D tiles, or as a list for fast scrolling
 * whose rows swipe to show more actions.
 */
@Composable
fun ShelfPageScreen(
    onBack: () -> Unit,
    onOpenDetails: (Book, String) -> Unit,
    onBookmarks: (Book) -> Unit = {},
    viewModel: ShelfPageViewModel = hiltViewModel(),
    shelvesVm: ShelvesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var prompt by remember { mutableStateOf<BookPrompt?>(null) }
    val selection = rememberSelection()
    var confirmBulk by remember { mutableStateOf(false) }
    var relinkId by rememberSaveable { mutableStateOf<String?>(null) }
    val relink = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = relinkId
        if (uri != null && id != null) viewModel.relink(id, uri.toString())
        relinkId = null
    }
    val actions = BookRowActions(
        onOpen = { item ->
            if (item.availability == Availability.MISSING) {
                relinkId = item.book.id
                relink.launch(arrayOf("application/pdf", "application/epub+zip"))
            } else {
                onOpenDetails(item.book, SHELF_PAGE_ORIGIN)
            }
        },
        onRename = { prompt = BookPrompt.Rename(it) },
        onDelete = { prompt = BookPrompt.Delete(it) },
        onShelves = { prompt = BookPrompt.Shelves(it) },
        onLocate = {
            relinkId = it.id
            relink.launch(arrayOf("application/pdf", "application/epub+zip"))
        },
        onBookmarks = onBookmarks,
        selection = selection,
        onDeleteChosen = { confirmBulk = true }
    )
    ShelfPageContent(state, onBack, actions)
    if (confirmBulk) ConfirmShelfBooksDelete(selection, viewModel) { confirmBulk = false }
    BackHandler(selection.active) { selection.done() }
    BookPromptDialogs(prompt, state, viewModel, shelvesVm) { prompt = null }
}

/** Asks before select mode removes the chosen books from the library (as on the Library tab). */
@Composable
private fun ConfirmShelfBooksDelete(selection: Selection, viewModel: ShelfPageViewModel, onClose: () -> Unit) {
    val n = selection.chosen.size
    BulkDeleteDialog(
        pluralStringResource(R.plurals.delete_books_title, n, n),
        stringResource(R.string.delete_books_body),
        onConfirm = {
            selection.chosen.forEach(viewModel::delete)
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
internal fun ShelfPageContent(state: ShelfPageUiState, onBack: () -> Unit, actions: BookRowActions) {
    var view by rememberSaveable { mutableIntStateOf(0) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val padding = PaddingValues(
        start = 18.dp,
        end = 18.dp,
        top = insets.calculateTopPadding(),
        bottom = insets.calculateBottomPadding() + 24.dp
    )
    val header: @Composable () -> Unit = {
        ShelfPageHeader(state, view, { view = it }, onBack) {
            actions.selection?.let { selection ->
                SelectControls(selection, state.items.map { it.book.id }, actions.onDeleteChosen)
            }
        }
    }
    if (view == 0) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(TILE_MIN),
            modifier = Modifier.fillMaxSize().testTag(LibraryTestTags.SHELF_TILES),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
            items(state.items, key = { it.book.id }) { item -> BookTile(item, actions) }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().testTag(LibraryTestTags.SHELF_LIST),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { header() }
            items(state.items, key = { it.book.id }) { item -> BookListRow(item, actions) }
        }
    }
}

@Composable
private fun ShelfPageHeader(
    state: ShelfPageUiState,
    view: Int,
    onView: (Int) -> Unit,
    onBack: () -> Unit,
    select: @Composable () -> Unit
) {
    val name = state.shelf?.name
        ?: stringResource(if (state.noShelves) R.string.shelf_all_books else R.string.shelf_loose)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FlowFolioIconButton(onClick = onBack) {
                FlowFolioGlyph(Glyph.Back, contentDescription = stringResource(R.string.action_back))
            }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) { select() }
        }
        Text(
            name,
            style = FlowFolioType.screenTitle.merge(FlowFolio.title),
            modifier = Modifier.padding(top = 14.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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

/** A tile: the 3D book with its title; press and hold for the book's menu. */
@Composable
private fun BookTile(item: LibraryItem, actions: BookRowActions) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ShelfBook(
            item,
            SHELF_PAGE_ORIGIN,
            LibraryActions(
                onOpen = { tapped, _ -> actions.onOpen(tapped) },
                onDelete = actions.onDelete,
                onLocate = actions.onLocate,
                onAddToShelf = actions.onShelves,
                onRename = actions.onRename,
                onBookmarks = actions.onBookmarks,
                selection = actions.selection
            ),
            width = TILE_BOOK_WIDTH,
            height = TILE_BOOK_HEIGHT
        )
        // In the shelf title's own face, bold and centred under the book (owner, 2026-10-22).
        Text(
            item.book.title,
            style = FlowFolioType.sectionTitle.copy(fontSize = TILE_TITLE_SP, lineHeight = TILE_TITLE_LINE)
                .merge(FlowFolio.title),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp)
        )
    }
}

/**
 * A list row: cover, title, author, progress and size, and a menu. Swipe right for Shelves and Locate, left for
 * Rename and Delete.
 */
@Composable
private fun BookListRow(item: LibraryItem, actions: BookRowActions) {
    val book = item.book
    val selection = actions.selection
    if (selection != null && selection.active) {
        // Select mode: a tap chooses the row (lists never jiggle; owner, 2026-10-22); no swiping.
        Box(Modifier.testTag(LibraryTestTags.row(book.id))) {
            BookRowCard(item, actions.copy(onOpen = { selection.toggle(it.book.id) }))
            SelectionMark(selection, book.id)
        }
        return
    }
    val linked = book.storage is BookStorage.Linked
    SwipeRow(
        startWidth = if (linked) ACTION_WIDTH * 3 else ACTION_WIDTH * 2,
        endWidth = ACTION_WIDTH * 2,
        start = { close ->
            SwipeAction(R.string.action_add_to_shelf, Glyph.Shelves, FlowFolio.palette.item) {
                close()
                actions.onShelves(book)
            }
            SwipeAction(R.string.action_bookmarks, Glyph.Bookmark, FlowFolio.palette.subItem) {
                close()
                actions.onBookmarks(book)
            }
            if (linked) {
                SwipeAction(R.string.action_locate, Glyph.Search, FlowFolio.palette.title) {
                    close()
                    actions.onLocate(book)
                }
            }
        },
        end = { close ->
            SwipeAction(R.string.action_rename, Glyph.Edit, FlowFolio.palette.glyph) {
                close()
                actions.onRename(book)
            }
            SwipeAction(R.string.action_delete, Glyph.Trash, FlowFolio.palette.subItem) {
                close()
                actions.onDelete(book)
            }
        },
        modifier = Modifier.testTag(LibraryTestTags.row(book.id))
    ) {
        BookRowCard(item, actions)
    }
}

@Composable
private fun BookRowCard(item: LibraryItem, actions: BookRowActions) {
    val book = item.book
    val context = LocalContext.current
    val cover by rememberCover(book)
    var menu by remember { mutableStateOf(false) }
    FlowFolioCard(
        Modifier.fillMaxWidth().clickable { actions.onOpen(item) },
        radius = 18.dp,
        padding = PaddingValues(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(ROW_COVER_WIDTH, ROW_COVER_HEIGHT)
                    .clip(RoundedCornerShape(4.dp))
                    .background(FlowFolio.palette.surface2)
            ) {
                // The same face as the shelf: the cover image, or the drawn title cover when the book has none.
                FlatCover(book.face(cover), Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    book.title,
                    style = FlowFolioType.rowLabel.merge(FlowFolio.item),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                book.author?.let {
                    Text(it, style = FlowFolioType.rowDetail.merge(FlowFolio.hint), maxLines = 1)
                }
                FlowFolioProgressBar(book.progress, Modifier.padding(top = 8.dp, bottom = 4.dp))
                val where = if (book.lastPage != null) {
                    stringResource(R.string.page_of, book.pageLabel(book.lastPage!!), book.printedPages())
                } else if (book.pageCount > 0) {
                    pluralStringResource(R.plurals.page_count, book.pageCount, book.pageCount)
                } else {
                    stringResource(book.formatLabel())
                }
                val status = when {
                    item.availability == Availability.MISSING -> " · " + stringResource(R.string.badge_missing)
                    book.passwordProtected -> " · " + stringResource(R.string.badge_locked)
                    else -> ""
                }
                Text(
                    "$where · ${Formatter.formatShortFileSize(context, book.sizeBytes)}$status",
                    style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
                    maxLines = 1
                )
            }
            Box {
                FlowFolioIconButton(onClick = { menu = true }) {
                    FlowFolioGlyph(Glyph.More, contentDescription = stringResource(R.string.action_more))
                }
                BookRowMenu(book, menu, actions) { menu = false }
            }
        }
    }
}

@Composable
private fun BookRowMenu(book: Book, open: Boolean, actions: BookRowActions, onClose: () -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onClose) {
        val entry = @Composable { label: Int, warn: Boolean, run: () -> Unit ->
            DropdownMenuItem(
                text = { Text(stringResource(label), style = if (warn) FlowFolio.subItem else FlowFolio.title) },
                onClick = {
                    onClose()
                    run()
                }
            )
        }
        entry(R.string.action_add_to_shelf, false) { actions.onShelves(book) }
        entry(R.string.action_rename, false) { actions.onRename(book) }
        if (book.storage is BookStorage.Linked) entry(R.string.action_locate, false) { actions.onLocate(book) }
        entry(R.string.action_delete, true) { actions.onDelete(book) }
    }
}

/** One action uncovered by a swipe: a glyph over its label in [color]. */
@Composable
internal fun SwipeAction(label: Int, glyph: Glyph, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Column(
        Modifier.width(ACTION_WIDTH).fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FlowFolioGlyph(glyph, contentDescription = null, color = color, size = 22.dp)
        Text(
            stringResource(label),
            style = FlowFolioType.pill.merge(FlowFolio.role(color, FlowFolio.palette.itemGlow)),
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

internal const val SHELF_PAGE_ORIGIN = "shelfpage"
private val TILE_MIN = 132.dp
private val TILE_BOOK_WIDTH = 96.dp
private val TILE_BOOK_HEIGHT = 136.dp
private val ACTION_WIDTH: Dp = 84.dp
private val ROW_COVER_WIDTH = 48.dp
private val ROW_COVER_HEIGHT = 66.dp

private val TILE_TITLE_SP = 15.sp
private val TILE_TITLE_LINE = 19.sp
