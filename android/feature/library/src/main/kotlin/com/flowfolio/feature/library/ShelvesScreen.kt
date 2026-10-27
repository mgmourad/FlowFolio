package com.flowfolio.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.Book3D
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioAlertDialog
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalChromePadding
import com.flowfolio.core.designsystem.Selection
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.color
import com.flowfolio.core.designsystem.jiggle
import com.flowfolio.core.designsystem.rememberSelection
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.Shelf

/** What is being edited on the Shelves screen. */
private sealed interface ShelfEdit {
    data object New : ShelfEdit

    data class Rename(val shelf: Shelf) : ShelfEdit

    data class Delete(val shelf: Shelf) : ShelfEdit

    /** "Add books" from a shelf's press-and-hold menu (owner, 2026-10-26). */
    data class AddBooks(val shelf: Shelf) : ShelfEdit
}

/** Shelves (the mock's `#s-shelves`): a card per shelf; tapping one shows it in the library. */
@Composable
fun ShelvesScreen(onOpenShelf: (Long) -> Unit, viewModel: ShelvesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<ShelfEdit?>(null) }
    val selection = rememberSelection()
    var confirmBulk by remember { mutableStateOf(false) }
    ShelvesContent(
        state = state,
        onOpen = onOpenShelf,
        onNew = { edit = ShelfEdit.New },
        onRename = { edit = ShelfEdit.Rename(it) },
        onDelete = { edit = ShelfEdit.Delete(it) },
        selection = selection,
        onDeleteChosen = { confirmBulk = true },
        onAddBooks = { edit = ShelfEdit.AddBooks(it) }
    )
    if (confirmBulk) ConfirmShelvesDelete(selection, viewModel) { confirmBulk = false }
    BackHandler(selection.active) { selection.done() }
    when (val e = edit) {
        null -> Unit

        is ShelfEdit.AddBooks -> AddBooksDialog(e.shelf, state.books, onConfirm = {
            viewModel.addBooks(e.shelf.id, it)
            edit = null
        }, onDismiss = { edit = null })

        ShelfEdit.New -> ShelfDialog(stringResource(R.string.shelf_new), "", null, onConfirm = { name, color ->
            viewModel.create(name, color = color)
            edit = null
        }, onDismiss = { edit = null })

        is ShelfEdit.Rename -> ShelfDialog(
            stringResource(R.string.shelf_edit),
            e.shelf.name,
            e.shelf.color,
            onConfirm = { name, color ->
                viewModel.rename(e.shelf.id, name)
                viewModel.setColor(e.shelf.id, color)
                edit = null
            },
            onDismiss = { edit = null }
        )

        is ShelfEdit.Delete -> FlowFolioAlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(stringResource(R.string.shelf_delete_title, e.shelf.name)) },
            text = { Text(stringResource(R.string.shelf_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(e.shelf.id)
                    edit = null
                }) { Text(stringResource(R.string.action_remove), style = FlowFolio.subItem) }
            },
            dismissButton = {
                TextButton(onClick = { edit = null }) {
                    Text(stringResource(R.string.action_cancel), style = FlowFolio.hint)
                }
            }
        )
    }
}

/** Asks before select mode removes the chosen shelves (their books stay). */
@Composable
private fun ConfirmShelvesDelete(selection: Selection, viewModel: ShelvesViewModel, onClose: () -> Unit) {
    val n = selection.chosen.size
    BulkDeleteDialog(
        pluralStringResource(R.plurals.delete_shelves_title, n, n),
        stringResource(R.string.delete_shelves_body),
        onConfirm = {
            selection.chosen.forEach { viewModel.delete(it.toLong()) }
            selection.done()
            onClose()
        },
        onDismiss = onClose
    )
}

@Composable
internal fun ShelvesContent(
    state: ShelvesUiState,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onRename: (Shelf) -> Unit,
    onDelete: (Shelf) -> Unit,
    selection: Selection = rememberSelection(),
    onDeleteChosen: () -> Unit = {},
    onAddBooks: (Shelf) -> Unit = {}
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val chrome = LocalChromePadding.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CARD_MIN),
        modifier = Modifier.fillMaxSize().chromeStart().testTag(LibraryTestTags.SHELVES),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = insets.calculateTopPadding(),
            bottom = insets.calculateBottomPadding() + chrome.calculateBottomPadding() + 24.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            val keys = state.cards.map { it.shelf.id.toString() }
            Column(Modifier.reveal(0)) {
                ShelvesHeader {
                    // New shelf steps aside with Select while choosing.
                    SelectControls(selection, keys) {
                        FlowFolioIconButton(onClick = onNew, modifier = Modifier.testTag(LibraryTestTags.ADD_SHELF)) {
                            FlowFolioGlyph(Glyph.Add, contentDescription = stringResource(R.string.shelf_new))
                        }
                    }
                }
                SelectionRow(selection, keys, onDeleteChosen)
            }
        }
        if (!state.loading) {
            itemsIndexed(state.cards, key = { _, entry -> entry.shelf.id }) { i, card ->
                val key = card.shelf.id.toString()
                val open: (Long) -> Unit = { if (selection.active) selection.toggle(key) else onOpen(it) }
                Box(Modifier.animateItem().reveal(1 + i).jiggle(selection.active, key)) {
                    ShelfCardView(
                        card,
                        open,
                        ShelfMenu(onRename, onDelete, onAddBooks, onSelect = { selection.startWith(key) }),
                        Modifier,
                        menuEnabled = !selection.active
                    )
                    SelectionMark(selection, key)
                }
            }
            if (!selection.active) item(key = "new") { NewShelfCard(onNew, Modifier.reveal(1 + state.cards.size)) }
        }
    }
}

@Composable
private fun ShelvesHeader(select: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.shelves_title), style = FlowFolioType.screenTitle.merge(FlowFolio.title))
            Text(
                stringResource(R.string.shelves_sub),
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        select()
    }
}

/** What a shelf card's press-and-hold menu does. */
private class ShelfMenu(
    val onRename: (Shelf) -> Unit,
    val onDelete: (Shelf) -> Unit,
    val onAddBooks: (Shelf) -> Unit,
    val onSelect: () -> Unit
)

/**
 * `.col`: up to five books fanned out, the shelf's name and how many books it holds, outlined in the shelf's colour
 * (owner, 2026-10-22).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfCardView(
    card: ShelfCard,
    onOpen: (Long) -> Unit,
    actions: ShelfMenu,
    modifier: Modifier,
    menuEnabled: Boolean = true
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    val outline = card.shelf.color.color
    Box(modifier.clip(shape).border(OUTLINE, outline, shape)) {
        FlowFolioCard(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { onOpen(card.shelf.id) }, onLongClick = { if (menuEnabled) menu = true })
                .testTag(LibraryTestTags.shelf(card.shelf.id)),
            radius = 24.dp,
            padding = PaddingValues(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 14.dp)
        ) {
            Column {
                Fan(card.books.take(FAN_SIZE))
                Text(
                    card.shelf.name,
                    style = FlowFolioType.rowLabel.copy(fontSize = 16.sp).merge(FlowFolio.item),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    pluralStringResource(R.plurals.book_count, card.books.size, card.books.size),
                    style = FlowFolioType.rowDetail.merge(FlowFolio.hint)
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            HoldEntry(R.string.action_select) {
                menu = false
                actions.onSelect()
            }
            HoldEntry(R.string.action_add_books) {
                menu = false
                actions.onAddBooks(card.shelf)
            }
            HoldEntry(R.string.action_edit) {
                menu = false
                actions.onRename(card.shelf)
            }
            HoldEntry(R.string.action_remove, warn = true) {
                menu = false
                actions.onDelete(card.shelf)
            }
        }
    }
}

/**
 * `.fan`: the books overlap across the card's width, the outer ones tilted most, the middle one in front; up to five
 * (owner, 2026-10-22; the mock fanned three).
 */
@Composable
private fun Fan(books: List<Book>) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(FAN_HEIGHT)) {
        val n = books.size
        val step = if (n > 1) (maxWidth - FAN_BOOK_WIDTH - FAN_EDGE * 2) / (n - 1) else 0.dp
        val middle = (n - 1) / 2f
        // Outer books first, so the middle one is drawn in front.
        books.indices.sortedByDescending { kotlin.math.abs(it - middle) }.forEach { i ->
            val book = books[i]
            val cover by rememberCover(book)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = FAN_EDGE + step * i, bottom = 4.dp)
                    .graphicsLayer {
                        rotationZ = if (n > 1) (i - middle) / middle * FAN_MAX_TILT else 0f
                        // Drawn once at rest and turned as a layer, so the shadow is not re-blurred.
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    }
            ) {
                Book3D(book.face(cover), FAN_BOOK_WIDTH, FAN_BOOK_HEIGHT)
            }
        }
    }
}

/** `.col.new`: a dashed outline card that makes a shelf. */
@Composable
private fun NewShelfCard(onNew: () -> Unit, modifier: Modifier) {
    val p = FlowFolio.palette
    Box(
        modifier
            .heightIn(min = NEW_CARD_MIN)
            .clip(RoundedCornerShape(24.dp))
            .drawBehind {
                drawRoundRect(
                    p.edge,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                )
            }
            .clickable(onClick = onNew)
            .testTag(LibraryTestTags.NEW_SHELF_CARD),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("+", style = FlowFolioType.screenTitle.copy(fontSize = 34.sp).merge(FlowFolio.title))
            Text(stringResource(R.string.shelf_new), style = FlowFolioType.rowLabel.merge(FlowFolio.hint))
        }
    }
}

private const val FAN_SIZE = 5
private val CARD_MIN = 160.dp
private val NEW_CARD_MIN = 190.dp
private val FAN_HEIGHT = 120.dp
private val FAN_BOOK_WIDTH = 64.dp
private val FAN_BOOK_HEIGHT = 94.dp
private val FAN_EDGE = 4.dp
private const val FAN_MAX_TILT = 9f
private val OUTLINE = 2.dp
