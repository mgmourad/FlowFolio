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
import com.flowfolio.core.designsystem.FlowFolioProgressBar
import com.flowfolio.core.designsystem.FlowFolioSegmented
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Selection
import com.flowfolio.core.designsystem.rememberSelection
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.pageLabel
import com.flowfolio.core.model.printedPages

/** The shelf page as tiles: its folders, then its books, which a shelf lets you drag into a new order. */
@Composable
internal fun ShelfTiles(
    state: ShelfPageUiState,
    padding: PaddingValues,
    header: @Composable () -> Unit,
    actions: BookRowActions,
    canReorder: Boolean
) {
    val folders = state.subfolders.size
    val bookKeys = state.items.map { it.book.id }
    val byId = state.items.associateBy { it.book.id }
    val grid = rememberLazyGridState()
    val reorder = actions.onReorder?.let { rememberGridReorder(bookKeys, grid, it) }?.takeIf { canReorder }
    val rowActions = actions.copy(reorder = reorder)
    val shown = reorder?.order?.mapNotNull(byId::get) ?: state.items
    LazyVerticalGrid(
        columns = GridCells.Adaptive(TILE_CELL),
        state = grid,
        modifier = Modifier.fillMaxSize().testTag(LibraryTestTags.SHELF_TILES),
        contentPadding = padding,
        // Rows well apart, so each title clearly belongs to the book above it (owner, 2026-10-23).
        horizontalArrangement = Arrangement.spacedBy(TILE_COLUMN_GAP),
        verticalArrangement = Arrangement.spacedBy(TILE_ROW_GAP)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.reveal(0)) { header() } }
        itemsIndexed(state.subfolders, key = { _, f -> f.selectionId }) { i, folder ->
            Box(Modifier.animateItem().reveal(1 + i).fillMaxWidth(), Alignment.TopCenter) {
                FolderTile(folder, actions.folders)
            }
        }
        itemsIndexed(shown, key = { _, entry -> entry.book.id }) { i, item ->
            val dragged = reorder?.dragging == item.book.id
            Box(
                Modifier.zIndex(if (dragged) 1f else 0f)
                    .then(if (dragged) Modifier else Modifier.animateItem())
                    .reveal(1 + folders + i)
                    .fillMaxWidth(),
                Alignment.TopCenter
            ) {
                BookTile(item, rowActions)
            }
        }
    }
}

/** The shelf page as a list: its folders, then its books, which a shelf lets you drag into a new order. */
@Composable
internal fun ShelfList(
    state: ShelfPageUiState,
    padding: PaddingValues,
    header: @Composable () -> Unit,
    actions: BookRowActions,
    canReorder: Boolean
) {
    val folders = state.subfolders.size
    val bookKeys = state.items.map { it.book.id }
    val byId = state.items.associateBy { it.book.id }
    val list = rememberLazyListState()
    val reorder = actions.onReorder?.let { rememberColumnReorder(bookKeys, list, it) }?.takeIf { canReorder }
    val rowActions = actions.copy(reorder = reorder)
    val shown = reorder?.order?.mapNotNull(byId::get) ?: state.items
    LazyColumn(
        Modifier.fillMaxSize().testTag(LibraryTestTags.SHELF_LIST),
        state = list,
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Box(Modifier.reveal(0)) { header() } }
        itemsIndexed(state.subfolders, key = { _, f -> f.selectionId }) { i, folder ->
            Box(Modifier.animateItem().reveal(1 + i)) { FolderRow(folder, actions.folders) }
        }
        itemsIndexed(shown, key = { _, entry -> entry.book.id }) { i, item ->
            val dragged = reorder?.dragging == item.book.id
            Box(
                Modifier.zIndex(if (dragged) 1f else 0f)
                    .then(if (dragged) Modifier else Modifier.animateItem())
                    .reveal(1 + folders + i)
            ) { BookListRow(item, rowActions) }
        }
    }
}

/**
 * A tile: the 3D book, no shadow under it, then its title (two lines at most) and author, left-aligned and no wider
 * than the book (owner, 2026-10-26); press and hold for the book's menu.
 */
@Composable
internal fun BookTile(item: LibraryItem, actions: BookRowActions, origin: String = SHELF_PAGE_ORIGIN) {
    Column(Modifier.width(TILE_BOOK_WIDTH + BOOK_SPINE_ROOM)) {
        ShelfBook(
            item,
            origin,
            LibraryActions(
                onOpen = { tapped, _ -> actions.onOpen(tapped) },
                onDelete = actions.onDelete,
                onLocate = actions.onLocate,
                onAddToShelf = actions.onShelves,
                onRename = actions.onRename,
                onFlow = actions.onFlow,
                onBookmarks = actions.onBookmarks,
                selection = actions.selection
            ),
            width = TILE_BOOK_WIDTH,
            height = TILE_BOOK_HEIGHT,
            shadow = false,
            reorder = actions.reorder
        )
        Text(
            item.book.title,
            style = FlowFolioType.sectionTitle.copy(fontSize = TILE_TITLE_SP, lineHeight = TILE_TITLE_LINE)
                .merge(FlowFolio.title),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(TILE_BOOK_WIDTH).padding(top = TILE_TITLE_GAP)
        )
        item.book.author?.let {
            Text(
                it,
                style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(TILE_BOOK_WIDTH).padding(top = 2.dp)
            )
        }
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
    // A PDF's swipe also turns its Dynamic Flow on or off (ADR-0045).
    val flow = actions.onFlow?.takeIf { book.format == BookFormat.PDF }
    SwipeRow(
        startWidth = ACTION_WIDTH * (2 + (if (linked) 1 else 0) + (if (flow != null) 1 else 0)),
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
            if (flow != null) {
                val on = book.flowMode == true
                SwipeAction(
                    if (on) R.string.action_flow_off_short else R.string.action_flow_short,
                    Glyph.DynamicFlow,
                    FlowFolio.palette.title
                ) {
                    close()
                    flow(book)
                }
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
        Modifier.fillMaxWidth().then(rowGestures(item, actions) { menu = true }),
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
        actions.selection?.let { selection -> entry(R.string.action_select, false) { selection.startWith(book.id) } }
        entry(R.string.action_add_to_shelf, false) { actions.onShelves(book) }
        actions.onFavorite?.let { favorite ->
            entry(if (book.favorite) R.string.action_favorite_remove else R.string.action_favorite_add, false) {
                favorite(book)
            }
        }
        actions.onFinished?.let { finished ->
            entry(if (book.finished) R.string.action_mark_unfinished else R.string.action_mark_finished, false) {
                finished(book)
            }
        }
        actions.onFlow?.takeIf { book.format == BookFormat.PDF }?.let { flow ->
            entry(
                if (book.flowMode ==
                    true
                ) {
                    R.string.action_flow_off
                } else {
                    R.string.action_flow_on
                },
                false
            ) { flow(book) }
        }
        entry(R.string.action_rename, false) { actions.onRename(book) }
        if (book.storage is BookStorage.Linked) entry(R.string.action_locate, false) { actions.onLocate(book) }
        entry(R.string.action_delete, true) { actions.onDelete(book) }
    }
}

/**
 * A list row's tap and press and hold: press and hold opens the same menu as the row's "…" (owner, 2026-10-26), but
 * not while choosing; on a shelf, holding and dragging moves the book instead.
 */
@Composable
private fun rowGestures(item: LibraryItem, actions: BookRowActions, onMenu: () -> Unit): Modifier {
    val selecting = actions.selection?.active == true
    val reorder = actions.reorder
    return if (reorder != null && !selecting) {
        Modifier.reorderable(reorder, item.book.id, onTap = { actions.onOpen(item) }, onHold = onMenu)
    } else {
        Modifier.combinedClickable(onClick = { actions.onOpen(item) }, onLongClick = { if (!selecting) onMenu() })
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

private val ACTION_WIDTH: Dp = 84.dp
private val ROW_COVER_WIDTH = 48.dp
private val ROW_COVER_HEIGHT = 66.dp
private val TILE_TITLE_SP = 13.sp
private val TILE_TITLE_LINE = 16.sp
private val TILE_TITLE_GAP = 10.dp
