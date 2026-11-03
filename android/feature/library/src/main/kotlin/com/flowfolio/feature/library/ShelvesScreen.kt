@file:Suppress("TooManyFunctions")

package com.flowfolio.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
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
import com.flowfolio.core.designsystem.ScrollToTopOnTab
import com.flowfolio.core.designsystem.Selection
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.color
import com.flowfolio.core.designsystem.jiggle
import com.flowfolio.core.designsystem.rememberSelection
import com.flowfolio.core.designsystem.rememberZoomSource
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

    /** "Batch Dynamic Flow" confirmation for an entire shelf. */
    data class BatchFlow(val shelf: Shelf) : ShelfEdit
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
        onAddBooks = { edit = ShelfEdit.AddBooks(it) },
        onBatchFlow = { edit = ShelfEdit.BatchFlow(it) },
        onToggleFlow = { card ->
            if (card.flowActive) {
                viewModel.setShelfFlowOff(card.shelf.id)
            } else if (viewModel.isShelfFlowReady(card.shelf.id)) {
                viewModel.startBatchFlow(card.shelf.id)
            } else {
                edit = ShelfEdit.BatchFlow(card.shelf)
            }
        }
    )
    if (confirmBulk) ConfirmShelvesDelete(selection, viewModel) { confirmBulk = false }
    BackHandler(selection.active) { selection.done() }
    ShelfEditDialogs(edit, state, viewModel) { edit = null }
}

@Composable
private fun ShelfEditDialogs(edit: ShelfEdit?, state: ShelvesUiState, viewModel: ShelvesViewModel, onDone: () -> Unit) {
    when (val e = edit) {
        null -> Unit

        is ShelfEdit.AddBooks -> AddBooksDialog(e.shelf, state.books, onConfirm = {
            viewModel.addBooks(e.shelf.id, it)
            onDone()
        }, onDismiss = onDone)

        ShelfEdit.New -> ShelfDialog(stringResource(R.string.shelf_new), "", null, onConfirm = { name, color ->
            viewModel.create(name, color = color)
            onDone()
        }, onDismiss = onDone)

        is ShelfEdit.Rename -> ShelfDialog(
            stringResource(R.string.shelf_edit),
            e.shelf.name,
            e.shelf.color,
            onConfirm = { name, color ->
                viewModel.rename(e.shelf.id, name)
                viewModel.setColor(e.shelf.id, color)
                onDone()
            },
            onDismiss = onDone
        )

        is ShelfEdit.Delete -> FlowFolioAlertDialog(
            onDismissRequest = onDone,
            title = { Text(stringResource(R.string.shelf_delete_title, e.shelf.name)) },
            text = { Text(stringResource(R.string.shelf_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(e.shelf.id)
                    onDone()
                }) { Text(stringResource(R.string.action_remove), style = FlowFolio.subItem) }
            },
            dismissButton = {
                TextButton(onClick = onDone) {
                    Text(stringResource(R.string.action_cancel), style = FlowFolio.hint)
                }
            }
        )

        is ShelfEdit.BatchFlow -> FlowFolioAlertDialog(
            onDismissRequest = onDone,
            title = { Text(stringResource(R.string.batch_flow_title)) },
            text = { Text(stringResource(R.string.batch_flow_body, e.shelf.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.startBatchFlow(e.shelf.id)
                    onDone()
                }) { Text(stringResource(R.string.action_convert), style = FlowFolio.title) }
            },
            dismissButton = {
                TextButton(onClick = onDone) {
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

@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun ShelvesContent(
    state: ShelvesUiState,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onRename: (Shelf) -> Unit,
    onDelete: (Shelf) -> Unit,
    selection: Selection = rememberSelection(),
    onDeleteChosen: () -> Unit = {},
    onAddBooks: (Shelf) -> Unit = {},
    onBatchFlow: (Shelf) -> Unit = {},
    onToggleFlow: (ShelfCard) -> Unit = {}
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val chrome = LocalChromePadding.current
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val filteredCards = remember(state.cards, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            state.cards
        } else {
            state.cards.mapNotNull { card ->
                val bookScore = card.books.maxOfOrNull {
                    maxOf(Fuzzy.score(q, it.title), Fuzzy.score(q, it.author))
                } ?: 0
                val score = maxOf(Fuzzy.score(q, card.shelf.name), bookScore)
                if (score > 0) card to score else null
            }.sortedByDescending { it.second }.map { it.first }
        }
    }
    val grid = rememberLazyGridState()
    // Tapping the tab shows this page from the top (owner, M4c).
    ScrollToTopOnTab("shelves", grid)
    LazyVerticalGrid(
        state = grid,
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
            val keys = filteredCards.map { it.shelf.id.toString() }
            Column(Modifier.reveal(0)) {
                ShelvesHeader {
                    // New shelf and Search step aside with Select while choosing.
                    SelectControls(selection, keys) {
                        FlowFolioIconButton(
                            onClick = {
                                searchOpen = !searchOpen
                                if (!searchOpen) query = ""
                            },
                            modifier = Modifier.testTag(LibraryTestTags.SEARCH)
                        ) {
                            FlowFolioGlyph(Glyph.Search, contentDescription = stringResource(R.string.action_search))
                        }
                        FlowFolioIconButton(onClick = onNew, modifier = Modifier.testTag(LibraryTestTags.ADD_SHELF)) {
                            FlowFolioGlyph(Glyph.Add, contentDescription = stringResource(R.string.shelf_new))
                        }
                    }
                }
                if (searchOpen) {
                    SearchField(
                        query = query,
                        onQuery = { query = it },
                        hintRes = R.string.search_shelves_hint
                    )
                }
                SelectionRow(selection, keys, onDeleteChosen)
            }
        }
        if (!state.loading) {
            itemsIndexed(filteredCards, key = { _, entry -> entry.shelf.id }) { i, card ->
                val key = card.shelf.id.toString()
                // The shelf's page zooms out of its card (owner, M3T1).
                val zoom = rememberZoomSource()
                val open: (Long) -> Unit = {
                    if (selection.active) {
                        selection.toggle(key)
                    } else {
                        zoom.mark()
                        onOpen(it)
                    }
                }
                Box(Modifier.animateItem().reveal(1 + i).jiggle(selection.active, key)) {
                    ShelfCardView(
                        card = card,
                        onOpen = open,
                        actions = ShelfMenu(
                            onRename = onRename,
                            onDelete = onDelete,
                            onAddBooks = onAddBooks,
                            onBatchFlow = onBatchFlow,
                            onToggleFlow = { onToggleFlow(card) },
                            onSelect = { selection.startWith(key) }
                        ),
                        modifier = zoom.modifier,
                        menuEnabled = !selection.active
                    )
                    SelectionMark(selection, key)
                }
            }
            if (!selection.active) item(key = "new") { NewShelfCard(onNew, Modifier.reveal(1 + filteredCards.size)) }
        }
    }
}

@Composable
private fun ShelvesHeader(select: @Composable () -> Unit) {
    ScreenHeader(
        title = stringResource(R.string.shelves_title),
        slogan = stringResource(R.string.shelves_sub),
        actions = select
    )
}

/** What a shelf card's press-and-hold menu does. */
private class ShelfMenu(
    val onRename: (Shelf) -> Unit,
    val onDelete: (Shelf) -> Unit,
    val onAddBooks: (Shelf) -> Unit,
    val onBatchFlow: (Shelf) -> Unit,
    val onToggleFlow: () -> Unit,
    val onSelect: () -> Unit
)

/**
 * `.col`: shelf title on top-left, Dynamic Flow toggle glyph on top-right, up to five books fanned out in the middle,
 * and minimalist stats (books, annotations, bookmarks, dictionary items, total read time) on the bottom.
 */
@Suppress("LongMethod")
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
            padding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        card.shelf.name,
                        style = FlowFolioType.rowLabel.copy(fontSize = 16.sp).merge(FlowFolio.item),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    )
                    ShelfFlowToggle(
                        shelfId = card.shelf.id,
                        active = card.flowActive,
                        converting = card.flowConverting,
                        onClick = actions.onToggleFlow
                    )
                }
                Fan(card.books.take(FAN_SIZE))
                ShelfStatsRow(card, Modifier.padding(top = 12.dp))
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
            HoldEntry(R.string.action_batch_flow) {
                menu = false
                actions.onBatchFlow(card.shelf)
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

@Composable
private fun ShelfFlowToggle(shelfId: Long, active: Boolean, converting: Boolean, onClick: () -> Unit) {
    val p = FlowFolio.palette
    val lit = active || converting
    val tint = when {
        active -> p.title
        converting -> p.subItem
        else -> p.hint.copy(alpha = 0.42f)
    }
    val bg = when {
        active -> p.title.copy(alpha = 0.18f)
        converting -> p.subItem.copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (lit) {
                    Modifier.border(1.dp, tint.copy(alpha = 0.55f), CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .testTag(LibraryTestTags.shelfFlow(shelfId)),
        contentAlignment = Alignment.Center
    ) {
        FlowFolioGlyph(
            Glyph.DynamicFlow,
            contentDescription = stringResource(R.string.action_batch_flow),
            color = tint,
            size = 16.dp
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShelfStatsRow(card: ShelfCard, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        ShelfStatItem(Glyph.Library, card.books.size.toString())
        ShelfStatItem(Glyph.Highlight, card.annotationsCount.toString())
        ShelfStatItem(Glyph.Bookmark, card.bookmarksCount.toString())
        ShelfStatItem(Glyph.Translate, card.dictionaryCount.toString())
        ShelfStatItem(Glyph.Clock, formatCompactReadTime(card.readTimeMillis))
    }
}

@Composable
private fun ShelfStatItem(glyph: Glyph, value: String) {
    val p = FlowFolio.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        FlowFolioGlyph(glyph, contentDescription = null, color = p.hint, size = 12.dp)
        Text(
            value,
            style = FlowFolioType.pill.copy(fontSize = 11.sp).merge(FlowFolio.hint),
            maxLines = 1
        )
    }
}

@Suppress("MagicNumber")
private fun formatCompactReadTime(millis: Long): String {
    val totalMinutes = (millis / 60_000L).toInt().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
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
