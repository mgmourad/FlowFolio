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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.Book3D
import com.flowfolio.core.designsystem.BookCover
import com.flowfolio.core.designsystem.ButtonStyle
import com.flowfolio.core.designsystem.COVER_ASPECT
import com.flowfolio.core.designsystem.DialogAction
import com.flowfolio.core.designsystem.DialogField
import com.flowfolio.core.designsystem.DialogSwatches
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
import com.flowfolio.core.designsystem.FlowFolioSnackbarHost
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalChromePadding
import com.flowfolio.core.designsystem.ScrollToTopOnTab
import com.flowfolio.core.designsystem.Selection
import com.flowfolio.core.designsystem.card
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.color
import com.flowfolio.core.designsystem.jiggle
import com.flowfolio.core.designsystem.rememberSelection
import com.flowfolio.core.designsystem.rememberZoomSource
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.designsystem.showFlowFolio
import com.flowfolio.core.designsystem.tint
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.Shelf
import com.flowfolio.core.model.ShelfColor
import com.flowfolio.core.model.ShelvesOrder
import kotlinx.coroutines.launch

/** What is being edited on the Shelves screen. */
private sealed interface ShelfEdit {
    data object New : ShelfEdit

    data class Rename(val shelf: Shelf) : ShelfEdit

    data class Delete(val shelf: Shelf) : ShelfEdit

    /** "Add books" from a shelf's press-and-hold menu (owner, 2026-10-26). */
    data class AddBooks(val shelf: Shelf) : ShelfEdit

    /** "Batch Dynamic Flow" confirmation for an entire shelf. */
    data class BatchFlow(val shelf: Shelf) : ShelfEdit

    data class Colour(val shelf: Shelf) : ShelfEdit
}

/**
 * Shelves (ADR-0052): folder cards, two columns folded and three unfolded, each with its colour as a border, its name,
 * up to five covers fanned, and its totals; a dashed card makes a new shelf. The header searches, orders and adds.
 */
@Suppress("LongMethod")
@Composable
fun ShelvesScreen(onOpenShelf: (Long) -> Unit, viewModel: ShelvesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<ShelfEdit?>(null) }
    val selection = rememberSelection()
    var confirmBulk by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        // Pull down to bring imported folders up to date (TM2).
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh
        ) {
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
                },
                onColour = { edit = ShelfEdit.Colour(it) },
                onOrder = viewModel::setOrder
            )
        }
        FlowFolioSnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).padding(bottom = LocalChromePadding.current.calculateBottomPadding())
        )
    }
    if (confirmBulk) ConfirmShelvesDelete(selection, viewModel) { confirmBulk = false }
    BackHandler(selection.active) { selection.done() }
    ShelfEditDialogs(
        edit,
        state,
        viewModel,
        onMade = { shelf ->
            scope.launch {
                val r = snackbar.showFlowFolio(
                    resources.getString(R.string.shelf_made, shelf.name),
                    Glyph.Shelves,
                    resources.getString(R.string.action_add_books)
                )
                if (r == SnackbarResult.ActionPerformed) edit = ShelfEdit.AddBooks(shelf)
            }
        },
        onDeleted = { name, undo ->
            scope.launch {
                val r = snackbar.showFlowFolio(
                    resources.getString(R.string.shelf_deleted, name),
                    Glyph.Trash,
                    resources.getString(R.string.action_undo)
                )
                if (r == SnackbarResult.ActionPerformed) undo()
            }
        }
    ) { edit = null }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun ShelfEditDialogs(
    edit: ShelfEdit?,
    state: ShelvesUiState,
    viewModel: ShelvesViewModel,
    onMade: (Shelf) -> Unit,
    onDeleted: (String, () -> Unit) -> Unit,
    onDone: () -> Unit
) {
    when (val e = edit) {
        null -> Unit

        is ShelfEdit.AddBooks -> AddBooksDialog(e.shelf, state.books, onConfirm = {
            viewModel.addBooks(e.shelf.id, it)
            onDone()
        }, onDismiss = onDone)

        ShelfEdit.New -> ShelfNameColourDialog(
            title = stringResource(R.string.shelf_new),
            confirm = stringResource(R.string.action_create),
            initialName = "",
            initialColour = ShelfColor.entries.first(),
            onConfirm = { name, colour ->
                viewModel.createShelf(name, colour, onMade)
                onDone()
            },
            onDismiss = onDone
        )

        is ShelfEdit.Rename -> ShelfNameColourDialog(
            title = stringResource(R.string.shelf_rename),
            confirm = stringResource(R.string.action_save),
            initialName = e.shelf.name,
            initialColour = null,
            onConfirm = { name, _ ->
                viewModel.rename(e.shelf.id, name)
                onDone()
            },
            onDismiss = onDone
        )

        is ShelfEdit.Colour -> ShelfColourDialog(e.shelf, onPick = {
            viewModel.setColor(e.shelf.id, it)
            onDone()
        }, onDismiss = onDone)

        is ShelfEdit.Delete -> FlowFolioDialog(
            onDismissRequest = onDone,
            title = stringResource(R.string.shelf_delete_title, e.shelf.name),
            body = stringResource(R.string.shelf_delete_body),
            glyph = Glyph.Trash,
            destructive = true,
            actions = listOf(
                DialogAction(stringResource(R.string.action_cancel)) { onDone() },
                DialogAction(stringResource(R.string.action_delete), ButtonStyle.DANGER) {
                    val card = state.cards.firstOrNull { it.shelf.id == e.shelf.id }
                    if (card != null) {
                        viewModel.deleteForUndo(card) { undo -> onDeleted(e.shelf.name, undo) }
                    } else {
                        viewModel.delete(e.shelf.id)
                    }
                    onDone()
                }
            )
        )

        is ShelfEdit.BatchFlow -> FlowFolioDialog(
            onDismissRequest = onDone,
            title = stringResource(R.string.batch_flow_title),
            body = stringResource(R.string.batch_flow_body, e.shelf.name),
            glyph = Glyph.DynamicFlow,
            actions = listOf(
                DialogAction(stringResource(R.string.action_cancel)) { onDone() },
                DialogAction(stringResource(R.string.action_convert), ButtonStyle.PRIMARY) {
                    viewModel.startBatchFlow(e.shelf.id)
                    onDone()
                }
            )
        )
    }
}

/** New shelf and Rename (ADR-0052): a name, and for a new shelf a row of colours. */
@Composable
internal fun ShelfNameColourDialog(
    title: String,
    confirm: String,
    initialName: String,
    initialColour: ShelfColor?,
    onConfirm: (String, ShelfColor?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var colour by remember { mutableStateOf(initialColour) }
    val dark = FlowFolio.palette.dark
    FlowFolioDialog(
        onDismissRequest = onDismiss,
        title = title,
        glyph = Glyph.Shelves,
        field = DialogField(name, { name = it }, stringResource(R.string.shelf_name_hint)),
        swatches = initialColour?.let {
            DialogSwatches(
                ShelfColor.entries.map { it.tint(dark) },
                ShelfColor.entries.map { it.name.lowercase() },
                ShelfColor.entries.indexOf(colour)
            ) { colour = ShelfColor.entries[it] }
        },
        actions = listOf(
            DialogAction(stringResource(R.string.action_cancel)) { onDismiss() },
            DialogAction(confirm, ButtonStyle.PRIMARY) { if (name.isNotBlank()) onConfirm(name.trim(), colour) }
        )
    )
}

/** Change colour (ADR-0052): the shelf's colours, the current one ringed. */
@Composable
internal fun ShelfColourDialog(shelf: Shelf, onPick: (ShelfColor) -> Unit, onDismiss: () -> Unit) {
    val dark = FlowFolio.palette.dark
    FlowFolioDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.shelf_colour),
        glyph = Glyph.Paper,
        swatches = DialogSwatches(
            ShelfColor.entries.map { it.tint(dark) },
            ShelfColor.entries.map { it.name.lowercase() },
            ShelfColor.entries.indexOf(shelf.color)
        ) { onPick(ShelfColor.entries[it]) },
        actions = listOf(DialogAction(stringResource(R.string.action_done)) { onDismiss() })
    )
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
    onToggleFlow: (ShelfCard) -> Unit = {},
    onColour: (Shelf) -> Unit = {},
    onOrder: (ShelvesOrder) -> Unit = {}
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
    val wide = libraryWide()
    val gutter = libraryGutter()
    // Tapping the tab shows this page from the top (owner, M4c).
    ScrollToTopOnTab("shelves", grid)
    LazyVerticalGrid(
        state = grid,
        columns = GridCells.Fixed(if (wide) WIDE_COLUMNS else NARROW_COLUMNS),
        modifier = Modifier.fillMaxSize().chromeStart().testTag(LibraryTestTags.SHELVES),
        contentPadding = PaddingValues(
            start = gutter,
            end = gutter,
            top = insets.calculateTopPadding(),
            bottom = insets.calculateBottomPadding() + chrome.calculateBottomPadding() + 24.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(if (wide) 18.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (wide) 18.dp else 14.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            val keys = filteredCards.map { it.shelf.id.toString() }
            Column(Modifier.reveal(0).padding(bottom = 6.dp)) {
                ScreenHeader(
                    title = stringResource(R.string.shelves_title),
                    slogan = stringResource(R.string.shelves_sub),
                    actions = {
                        // Search, Sort and New shelf step aside for Select's controls while choosing.
                        SelectControls(selection, keys) {
                            FlowFolioIconButton(
                                onClick = {
                                    searchOpen = !searchOpen
                                    if (!searchOpen) query = ""
                                },
                                glowing = searchOpen,
                                tooltip = stringResource(R.string.action_search),
                                modifier = Modifier.testTag(LibraryTestTags.SEARCH)
                            ) {
                                FlowFolioGlyph(Glyph.Search, null, size = 20.dp)
                            }
                            ShelvesOrderMenu(state.order, onOrder)
                            FlowFolioIconButton(
                                onClick = onNew,
                                tooltip = stringResource(R.string.shelf_new),
                                modifier = Modifier.testTag(LibraryTestTags.ADD_SHELF)
                            ) {
                                FlowFolioGlyph(Glyph.Add, null, size = 20.dp)
                            }
                        }
                    }
                )
                if (searchOpen) {
                    SearchField(query = query, onQuery = {
                        query = it
                    }, hintRes = R.string.search_shelves_hint, autoFocus = true)
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
                            onSelect = { selection.startWith(key) },
                            onColour = onColour,
                            onOpen = { onOpen(card.shelf.id) }
                        ),
                        wide = wide,
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

/** Sort shelves (ADR-0052): their own order, by name, most books, most read or most marks. */
@Composable
private fun ShelvesOrderMenu(order: ShelvesOrder, onOrder: (ShelvesOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FlowFolioIconButton(
            onClick = { open = true },
            tooltip = stringResource(R.string.shelves_sort),
            modifier = Modifier.testTag(LibraryTestTags.SHELVES_SORT)
        ) {
            FlowFolioGlyph(Glyph.Sort, null, size = 20.dp)
        }
        FlowFolioMenu(expanded = open, onDismissRequest = { open = false }) {
            FlowFolioMenuHeader(stringResource(R.string.library_sort_by))
            ShelvesOrder.entries.forEach { o ->
                FlowFolioMenuItem(stringResource(o.label), {
                    open = false
                    onOrder(o)
                }, checked = o == order)
            }
        }
    }
}

private val ShelvesOrder.label: Int
    get() = when (this) {
        ShelvesOrder.CUSTOM -> R.string.sort_custom
        ShelvesOrder.NAME -> R.string.sort_name
        ShelvesOrder.MOST_BOOKS -> R.string.shelves_most_books
        ShelvesOrder.MOST_READ -> R.string.shelves_most_read
        ShelvesOrder.MOST_MARKS -> R.string.shelves_most_marks
    }

/** What a shelf card's menu does. */
@Suppress("LongParameterList") // one handler per menu entry
private class ShelfMenu(
    val onRename: (Shelf) -> Unit,
    val onDelete: (Shelf) -> Unit,
    val onAddBooks: (Shelf) -> Unit,
    val onBatchFlow: (Shelf) -> Unit,
    val onToggleFlow: () -> Unit,
    val onSelect: () -> Unit,
    val onColour: (Shelf) -> Unit,
    val onOpen: () -> Unit
)

/**
 * A shelf card (`.folder`, ADR-0052): a 2 dp border in the shelf's colour, its name on two lines with the Dynamic Flow
 * switch and ⋯ beside it, up to five covers fanned, and its books, annotations, ribbons, words and time read.
 */
@Suppress("LongMethod")
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfCardView(
    card: ShelfCard,
    onOpen: (Long) -> Unit,
    actions: ShelfMenu,
    wide: Boolean,
    modifier: Modifier,
    menuEnabled: Boolean = true
) {
    val p = FlowFolio.palette
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    val outline = card.shelf.color?.color ?: p.line
    Box(
        modifier
            .card(shape)
            .border(OUTLINE, outline, shape)
            .clip(shape)
            .combinedClickable(onClick = { onOpen(card.shelf.id) }, onLongClick = { if (menuEnabled) menu = true })
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)
            .testTag(LibraryTestTags.shelf(card.shelf.id))
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    card.shelf.name,
                    style = FlowFolioType.rowLabel.copy(fontSize = 15.sp, lineHeight = 18.sp, color = p.ink),
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(top = 8.dp, end = 4.dp)
                )
                Box {
                    FlowFolioIconButton(
                        onClick = { if (menuEnabled) menu = true },
                        size = 36.dp,
                        ghost = true,
                        tooltip = stringResource(R.string.shelf_options)
                    ) {
                        FlowFolioGlyph(Glyph.More, null, color = p.ink2, size = 18.dp)
                    }
                    ShelfCardMenu(card, menu, actions) { menu = false }
                }
            }
            Fan(card.books.take(FAN_SIZE), if (wide) FAN_HEIGHT_WIDE else FAN_HEIGHT)
            // The Dynamic Flow toggle sits beside the stats: in the title row it left the name a few letters wide
            // on the folded screen ("Comp / uter...", owner 2026-11-12).
            Row(verticalAlignment = Alignment.Bottom) {
                ShelfStatsRow(card, Modifier.weight(1f))
                if (card.hasPdf) {
                    ShelfFlowToggle(card.shelf.id, card.flowActive, card.flowConverting, actions.onToggleFlow)
                }
            }
        }
    }
}

@Composable
private fun ShelfCardMenu(card: ShelfCard, open: Boolean, actions: ShelfMenu, onClose: () -> Unit) {
    fun run(action: () -> Unit): () -> Unit = {
        onClose()
        action()
    }
    FlowFolioMenu(expanded = open, onDismissRequest = onClose) {
        FlowFolioMenuItem(stringResource(R.string.action_open), run(actions.onOpen), glyph = Glyph.Shelves)
        FlowFolioMenuItem(
            stringResource(R.string.action_rename),
            run {
                actions.onRename(card.shelf)
            },
            glyph = Glyph.Edit
        )
        FlowFolioMenuItem(
            stringResource(R.string.shelf_colour),
            run {
                actions.onColour(card.shelf)
            },
            glyph = Glyph.Paper
        )
        FlowFolioMenuItem(
            stringResource(R.string.action_add_books),
            run {
                actions.onAddBooks(card.shelf)
            },
            glyph = Glyph.Add
        )
        if (card.hasPdf) {
            FlowFolioMenuItem(
                stringResource(if (card.flowActive) R.string.action_batch_flow_off else R.string.action_batch_flow),
                run { if (card.flowActive) actions.onToggleFlow() else actions.onBatchFlow(card.shelf) },
                glyph = Glyph.DynamicFlow
            )
        }
        FlowFolioMenuItem(stringResource(R.string.action_select), run(actions.onSelect), glyph = Glyph.Select)
        FlowFolioMenuDivider()
        FlowFolioMenuItem(
            stringResource(R.string.shelf_delete),
            run { actions.onDelete(card.shelf) },
            glyph = Glyph.Trash,
            destructive = true
        )
    }
}

@Composable
private fun ShelfFlowToggle(shelfId: Long, active: Boolean, converting: Boolean, onClick: () -> Unit) {
    val p = FlowFolio.palette
    val tint = if (active) p.accent else p.ink3
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (active) p.accentSoft else p.surface2)
            .clickable(onClick = onClick)
            .testTag(LibraryTestTags.shelfFlow(shelfId)),
        contentAlignment = Alignment.Center
    ) {
        FlowFolioGlyph(
            Glyph.DynamicFlow,
            contentDescription = stringResource(
                if (converting) R.string.shelf_flow_converting else R.string.action_batch_flow
            ),
            color = tint,
            size = 16.dp,
            glow = false
        )
    }
}

/** `.fs`: the shelf's totals, each a glyph and a figure, read out with their names. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShelfStatsRow(card: ShelfCard, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ShelfStatItem(Glyph.Library, card.books.size.toString(), R.string.stats_books)
        ShelfStatItem(Glyph.Highlight, card.annotationsCount.toString(), R.string.stats_annotations)
        ShelfStatItem(Glyph.Bookmark, card.bookmarksCount.toString(), R.string.stats_bookmarks)
        ShelfStatItem(Glyph.Translate, card.dictionaryCount.toString(), R.string.stats_words)
        ShelfStatItem(Glyph.Clock, formatCompactReadTime(card.readTimeMillis), R.string.stats_reading_time)
    }
}

@Composable
private fun ShelfStatItem(glyph: Glyph, value: String, label: Int) {
    val p = FlowFolio.palette
    val name = stringResource(label)
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "$value $name" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        FlowFolioGlyph(glyph, contentDescription = null, color = p.ink3, size = 13.dp, glow = false)
        Text(value, style = FlowFolioType.body.copy(fontSize = 12.sp, color = p.ink2), maxLines = 1)
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
 * `.fan` (ADR-0052): up to five flat covers, each half the card wide, spread across it and turned 5 degrees apart from
 * the middle, the outer ones lifted a little; the middle one in front.
 */
@Composable
private fun Fan(books: List<Book>, height: androidx.compose.ui.unit.Dp) {
    // As tall as its covers, not a fixed height: on the folded screen the covers came out about 100 dp tall in a
    // 132 dp fan, leaving an empty band in every card (owner, 2026-11-12). [height] is now the most it may take.
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 4.dp, end = 10.dp, top = 10.dp, bottom = 12.dp)) {
        val fitted = minOf(maxWidth * FAN_COVER_SHARE, height * COVER_ASPECT) / COVER_ASPECT + FAN_LIFT
        FanCovers(books, minOf(height, fitted))
    }
}

@Composable
private fun FanCovers(books: List<Book>, height: androidx.compose.ui.unit.Dp) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
        val n = books.size
        if (n == 0) {
            Text(
                stringResource(R.string.shelf_empty),
                style = FlowFolioType.body.copy(color = FlowFolio.palette.ink3),
                modifier = Modifier.align(Alignment.Center)
            )
            return@BoxWithConstraints
        }
        val coverWidth = minOf(maxWidth * FAN_COVER_SHARE, height * COVER_ASPECT)
        val spread = maxWidth - coverWidth
        val middle = (n - 1) / 2f
        books.indices.sortedByDescending { kotlin.math.abs(it - middle) }.forEach { i ->
            val book = books[i]
            val cover by rememberCover(book)
            val tilt = if (n > 1) (i - middle) * FAN_STEP_DEG else 0f
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = if (n >
                            1
                        ) {
                            spread * (i / (n - 1f))
                        } else {
                            spread / 2
                        },
                        bottom = (kotlin.math.abs(tilt) * 0.6f).dp
                    )
                    .width(coverWidth)
                    .graphicsLayer {
                        rotationZ = tilt
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
            ) {
                BookCover(book.face(cover), Modifier.fillMaxWidth())
            }
        }
    }
}

/** `.folder.new`: a dashed card that makes a shelf. */
@Composable
private fun NewShelfCard(onNew: () -> Unit, modifier: Modifier) {
    val p = FlowFolio.palette
    Box(
        modifier
            .heightIn(min = NEW_CARD_MIN)
            .clip(RoundedCornerShape(22.dp))
            .drawBehind {
                drawRoundRect(
                    p.line,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                )
            }
            .clickable(onClick = onNew)
            .testTag(LibraryTestTags.NEW_SHELF_CARD),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FlowFolioGlyph(Glyph.Add, null, color = p.accent, size = 26.dp, glow = false)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.shelf_new), style = FlowFolioType.body.copy(color = p.ink3))
        }
    }
}

private const val FAN_SIZE = 5
private const val WIDE_COLUMNS = 3
private const val NARROW_COLUMNS = 2
private const val FAN_COVER_SHARE = 0.44f
private const val FAN_STEP_DEG = 5f
private val NEW_CARD_MIN = 200.dp
private val FAN_HEIGHT = 132.dp

/** Room above the covers for the outer ones, lifted as they tilt. */
private val FAN_LIFT = 8.dp
private val FAN_HEIGHT_WIDE = 190.dp
private val OUTLINE = 2.dp
