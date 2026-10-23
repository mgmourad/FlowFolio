package com.flowfolio.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.BookColors
import com.flowfolio.core.designsystem.CoverSpot
import com.flowfolio.core.designsystem.FlatCover
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioChip
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalBookOpener
import com.flowfolio.core.designsystem.LocalChromePadding
import com.flowfolio.core.designsystem.card
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.color
import com.flowfolio.core.designsystem.coverSpot
import com.flowfolio.core.designsystem.jiggle
import com.flowfolio.core.designsystem.rememberSelection
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.designsystem.softShadow
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookmarkColor

/**
 * The mock's Bookmarks tab (`#s-bookmarks`): every ribbon in the library as a card with its colour tag, the book's
 * cover, the page and the chapter it is in. Tap a card to open the book there (with the book opening); swipe right to
 * recolour it, left to remove it. Highlights and notes join it once text selection exists.
 */
@Composable
fun BookmarksScreen(
    onRead: (Book) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: BookmarksViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val opener = LocalBookOpener.current
    val selection = rememberSelection()
    var confirmBulk by remember { mutableStateOf(false) }
    BookmarksContent(
        state,
        BookmarkActions(
            onOpen = { item, spot ->
                viewModel.open(item) {
                    val show = { onRead(item.book) }
                    opener?.open(spot, show) ?: show()
                }
            },
            onRecolour = viewModel::recolour,
            onRemove = viewModel::remove,
            onFilter = viewModel::setFilter,
            onBack = onBack,
            selection = selection,
            onDeleteChosen = { confirmBulk = true }
        )
    )
    if (confirmBulk) {
        val n = selection.chosen.size
        BulkDeleteDialog(
            pluralStringResource(R.plurals.delete_ribbons_title, n, n),
            stringResource(R.string.delete_cannot_undo),
            onConfirm = {
                viewModel.removeAll(selection.chosen.map(String::toLong))
                selection.done()
                confirmBulk = false
            },
            onDismiss = { confirmBulk = false }
        )
    }
    BackHandler(selection.active) { selection.done() }
}

@Composable
fun BookmarksContent(state: BookmarksUiState, actions: BookmarkActions) {
    val chrome = LocalChromePadding.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    BoxWithConstraints(Modifier.fillMaxSize().chromeStart().testTag(LibraryTestTags.BOOKMARKS)) {
        val twoColumns = maxWidth - GRID_SIDES * 2 >= CARD_MIN_WIDTH * 2 + GRID_GAP
        LazyVerticalGrid(
            columns = GridCells.Adaptive(CARD_MIN_WIDTH),
            contentPadding = PaddingValues(
                start = GRID_SIDES,
                end = GRID_SIDES,
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding() + chrome.calculateBottomPadding() + 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
            verticalArrangement = Arrangement.spacedBy(GRID_GAP),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { BookmarksHeader(state, actions, Modifier.reveal(0)) }
            if (state.colours.size > 1) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ColourFilter(state.colours, state.filter, actions.onFilter, Modifier.reveal(1))
                }
            }
            if (!state.loading && state.total == 0) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyBookmarks(Modifier.reveal(1)) }
            }
            items(state.items, key = { it.bookmark.id }) { item -> BookmarkRow(item, actions, twoColumns) }
        }
    }
}

@Composable
private fun BookmarksHeader(state: BookmarksUiState, actions: BookmarkActions, modifier: Modifier) {
    Column(modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            actions.onBack?.let {
                FlowFolioIconButton(onClick = it, glass = true, modifier = Modifier.padding(end = 14.dp)) {
                    FlowFolioGlyph(Glyph.Back, stringResource(R.string.action_back), color = FlowFolio.palette.text)
                }
            }
            Text(
                stringResource(R.string.bookmarks_title),
                style = FlowFolioType.screenTitle.merge(FlowFolio.title),
                modifier = Modifier.weight(1f)
            )
            actions.selection?.let { selection ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SelectControls(selection, state.items.map { it.bookmark.id.toString() }, actions.onDeleteChosen)
                }
            }
        }
        val book = state.book
        if (book != null) {
            Text(
                stringResource(R.string.bookmarks_of, book.title),
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.padding(top = 6.dp)
            )
        } else if (state.total > 0) {
            Text(
                stringResource(
                    R.string.bookmarks_sub,
                    pluralStringResource(R.plurals.ribbon_count, state.total, state.total),
                    pluralStringResource(R.plurals.book_count, state.bookCount, state.bookCount)
                ),
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** `.chips`: All, then a chip per ribbon colour in use, each with its colour dot. */
@Composable
private fun ColourFilter(
    colours: List<BookmarkColor>,
    selected: BookmarkColor?,
    onFilter: (BookmarkColor?) -> Unit,
    modifier: Modifier
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FlowFolioChip(stringResource(R.string.bookmarks_filter_all), selected == null, { onFilter(null) })
        colours.forEach { c -> ColourChip(c, c == selected) { onFilter(c) } }
    }
}

@Composable
private fun ColourChip(colour: BookmarkColor, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val name = stringResource(colour.label)
    val ring = if (selected) {
        Modifier.border(
            1.5.dp,
            FlowFolio.palette.title.copy(alpha = RING_ALPHA),
            shape
        )
    } else {
        Modifier
    }
    val dot = colour.color
    Row(
        Modifier
            .card(shape)
            .then(ring)
            .clip(shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                contentDescription = name
                this.selected = selected
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(10.dp)) { drawCircle(dot) }
        Text(
            name,
            style = FlowFolioType.chip.merge(if (selected) FlowFolio.title else FlowFolio.hint),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** A card that slides: right for the colours, left for Remove. */
@Composable
private fun BookmarkRow(item: BookmarkItem, actions: BookmarkActions, uniform: Boolean) {
    val selection = actions.selection
    if (selection != null && selection.active) {
        // Select mode: the card jiggles and a tap chooses it; no swiping.
        val key = item.bookmark.id.toString()
        Box(Modifier.jiggle(true, key).testTag(LibraryTestTags.bookmark(item.bookmark.id))) {
            BookmarkCard(item, uniform) { selection.toggle(key) }
            SelectionMark(selection, key)
        }
        return
    }
    SwipeRow(
        startWidth = SWATCH_STRIP,
        endWidth = REMOVE_WIDTH,
        start = { close ->
            Row(
                Modifier.width(SWATCH_STRIP).fillMaxHeight(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookmarkColor.entries.forEach { c ->
                    Swatch(c, c == item.bookmark.color) {
                        close()
                        actions.onRecolour(item, c)
                    }
                }
            }
        },
        end = { close ->
            SwipeAction(R.string.action_remove, Glyph.Trash, FlowFolio.palette.subItem) {
                close()
                actions.onRemove(item)
            }
        },
        // Unfolded, a sliding card stays inside its own column; its shadow can still fall above and below.
        modifier = Modifier
            .drawWithContent {
                clipRect(0f, -size.height, size.width, size.height * 2) { this@drawWithContent.drawContent() }
            }
            .testTag(LibraryTestTags.bookmark(item.bookmark.id))
    ) { BookmarkCard(item, uniform) { spot -> actions.onOpen(item, spot) } }
}

@Composable
private fun Swatch(colour: BookmarkColor, current: Boolean, onClick: () -> Unit) {
    val fill = colour.color
    val ring = FlowFolio.palette.text
    val name = stringResource(colour.label)
    Box(
        Modifier
            .size(30.dp)
            .semantics {
                contentDescription = name
                selected = current
            }
            .clip(CircleShape)
            .drawBehind { drawCircle(fill) }
            .then(if (current) Modifier.border(2.5.dp, ring, CircleShape) else Modifier)
            .clickable(onClick = onClick)
    )
}

/**
 * `.bm`: the colour tag, the book's cover, "Ribbon · p. N", the title and the chapter in italics. [uniform] (two
 * columns) gives every card the same height: two lines of title, one of chapter.
 */
@Composable
private fun BookmarkCard(item: BookmarkItem, uniform: Boolean, onOpen: (CoverSpot) -> Unit) {
    val spot = remember { CoverSpot().apply { angle = 0f } }
    val cover by rememberCover(item.book)
    spot.face = item.book.face(cover)
    val tag = item.bookmark.color.color
    FlowFolioCard(
        Modifier.fillMaxWidth().clickable { onOpen(spot) },
        radius = 22.dp,
        padding = PaddingValues(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MiniCover(item.book, cover, Modifier.coverSpot(spot))
            Column(Modifier.weight(1f).padding(end = 20.dp)) {
                Text(
                    stringResource(R.string.bookmarks_kind, item.label),
                    style = FlowFolioType.pill.copy(fontSize = 11.5.sp).merge(FlowFolio.subItem)
                )
                Text(
                    item.book.title,
                    style = FlowFolioType.rowLabel.merge(FlowFolio.item),
                    minLines = if (uniform) 2 else 1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                (item.chapter ?: if (uniform) "" else null)?.let {
                    Text(
                        it,
                        style = FlowFolioType.sub.copy(
                            fontFamily = FlowFolioType.heroTitle.fontFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 14.sp
                        ).merge(FlowFolio.hint),
                        maxLines = if (uniform) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        // `.tag`: a small ribbon hanging from the card's top edge.
        Canvas(Modifier.align(Alignment.TopEnd).padding(end = 4.dp).offset(y = -TAG_RISE).size(14.dp, 30.dp)) {
            val w = size.width
            val h = size.height
            drawPath(
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, h)
                    lineTo(w / 2f, h * TAG_NOTCH)
                    lineTo(0f, h)
                    close()
                },
                tag
            )
        }
    }
}

/** `.mini`: a small flat cover with a book's corners and a soft shadow. */
@Composable
internal fun MiniCover(book: Book, cover: CoverArt?, modifier: Modifier) {
    val shape = RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 5.dp, bottomEnd = 5.dp)
    Box(
        modifier
            .size(48.dp, 70.dp)
            .softShadow(shape, Color.Black.copy(alpha = MINI_SHADOW), blur = 14.dp, offsetY = 8.dp, spread = (-6).dp)
            .clip(shape)
            .semantics { contentDescription = book.title }
    ) {
        // The cover image, or the drawn title cover the shelf shows for books without one.
        FlatCover(book.face(cover), Modifier.fillMaxSize())
    }
}

/** No ribbons yet: what they are and how to add one. */
@Composable
private fun EmptyBookmarks(modifier: Modifier) {
    FlowFolioCard(modifier.fillMaxWidth().padding(top = 12.dp), radius = 24.dp, padding = PaddingValues(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            FlowFolioGlyph(Glyph.Bookmark, contentDescription = null, color = FlowFolio.palette.subItem, size = 34.dp)
            Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.bookmarks_empty_title), style = FlowFolioType.rowLabel.merge(FlowFolio.item))
            Text(
                stringResource(R.string.bookmarks_empty_body),
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private val BookmarkColor.label: Int
    get() = when (this) {
        BookmarkColor.SUB_ITEM -> R.string.bookmark_colour_red
        BookmarkColor.TITLE -> R.string.bookmark_colour_orange
        BookmarkColor.ITEM -> R.string.bookmark_colour_cyan
        BookmarkColor.ACCENT -> R.string.bookmark_colour_magenta
        BookmarkColor.GREEN -> R.string.bookmark_colour_green
    }

private val CARD_MIN_WIDTH = 280.dp
private val GRID_SIDES = 18.dp
private val GRID_GAP = 12.dp
private val SWATCH_STRIP = 220.dp
private val REMOVE_WIDTH = 84.dp
private const val TAG_NOTCH = 0.78f

/** The card pads its content by 14 dp; the tag hangs from the card's own top edge. */
private val TAG_RISE = 14.dp
private const val RING_ALPHA = 0.6f
private const val MINI_SHADOW = 0.55f
