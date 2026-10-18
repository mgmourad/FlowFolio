package com.flowfolio.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.Shelf

/** What is being edited on the Shelves screen. */
private sealed interface ShelfEdit {
    data object New : ShelfEdit

    data class Rename(val shelf: Shelf) : ShelfEdit

    data class Delete(val shelf: Shelf) : ShelfEdit
}

/** Shelves (the mock's `#s-shelves`): a card per shelf; tapping one shows it in the library. */
@Composable
fun ShelvesScreen(onOpenShelf: (Long) -> Unit, viewModel: ShelvesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<ShelfEdit?>(null) }
    ShelvesContent(
        state = state,
        onOpen = onOpenShelf,
        onNew = { edit = ShelfEdit.New },
        onRename = { edit = ShelfEdit.Rename(it) },
        onDelete = { edit = ShelfEdit.Delete(it) }
    )
    when (val e = edit) {
        null -> Unit

        ShelfEdit.New -> ShelfNameDialog(stringResource(R.string.shelf_new), "", onConfirm = {
            viewModel.create(it)
            edit = null
        }, onDismiss = { edit = null })

        is ShelfEdit.Rename -> ShelfNameDialog(stringResource(R.string.shelf_rename), e.shelf.name, onConfirm = {
            viewModel.rename(e.shelf.id, it)
            edit = null
        }, onDismiss = { edit = null })

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

@Composable
internal fun ShelvesContent(
    state: ShelvesUiState,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onRename: (Shelf) -> Unit,
    onDelete: (Shelf) -> Unit
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
        item(span = { GridItemSpan(maxLineSpan) }) { ShelvesHeader(onNew, Modifier.reveal(0)) }
        if (!state.loading) {
            items(state.cards, key = { it.shelf.id }) { card ->
                ShelfCardView(card, onOpen, onRename, onDelete, Modifier.reveal(1))
            }
            item(key = "new") { NewShelfCard(onNew, Modifier.reveal(2)) }
        }
    }
}

@Composable
private fun ShelvesHeader(onNew: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp, bottom = 6.dp),
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
        FlowFolioIconButton(onClick = onNew, modifier = Modifier.testTag(LibraryTestTags.ADD_SHELF)) {
            FlowFolioGlyph(Glyph.Add, contentDescription = stringResource(R.string.shelf_new))
        }
    }
}

/** `.col`: up to three books fanned out, the shelf's name and how many books it holds. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfCardView(
    card: ShelfCard,
    onOpen: (Long) -> Unit,
    onRename: (Shelf) -> Unit,
    onDelete: (Shelf) -> Unit,
    modifier: Modifier
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.clip(shape)) {
        FlowFolioCard(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { onOpen(card.shelf.id) }, onLongClick = { menu = true })
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
            DropdownMenuItem(text = {
                Text(stringResource(R.string.action_rename), style = FlowFolio.title)
            }, onClick = {
                menu = false
                onRename(card.shelf)
            })
            DropdownMenuItem(text = {
                Text(stringResource(R.string.action_remove), style = FlowFolio.subItem)
            }, onClick = {
                menu = false
                onDelete(card.shelf)
            })
        }
    }
}

/** `.fan`: the books overlap, the outer two tilted (-8 and 7 degrees), the middle one in front. */
@Composable
private fun Fan(books: List<Book>) {
    Box(Modifier.fillMaxWidth().height(FAN_HEIGHT)) {
        // Outer books first so the middle one is drawn in front (`z-index:2`).
        FAN_ORDER.filter { it < books.size }.forEach { i ->
            val book = books[i]
            val cover by rememberCover(book)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = FAN_LEFT[i], bottom = 4.dp)
                    .graphicsLayer {
                        rotationZ = FAN_TILT[i]
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

private const val FAN_SIZE = 3
private val CARD_MIN = 160.dp
private val NEW_CARD_MIN = 190.dp
private val FAN_HEIGHT = 120.dp
private val FAN_BOOK_WIDTH = 64.dp
private val FAN_BOOK_HEIGHT = 94.dp
private val FAN_LEFT = listOf(6.dp, 34.dp, 62.dp)
private val FAN_TILT = listOf(-8f, 0f, 7f)
private val FAN_ORDER = listOf(0, 2, 1)
