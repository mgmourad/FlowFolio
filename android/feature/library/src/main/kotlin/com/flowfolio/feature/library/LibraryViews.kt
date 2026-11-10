package com.flowfolio.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.BookCover
import com.flowfolio.core.designsystem.CoverBadge
import com.flowfolio.core.designsystem.CoverSpot
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioMenu
import com.flowfolio.core.designsystem.FlowFolioMenuDivider
import com.flowfolio.core.designsystem.FlowFolioMenuHeader
import com.flowfolio.core.designsystem.FlowFolioMenuItem
import com.flowfolio.core.designsystem.FlowFolioPill
import com.flowfolio.core.designsystem.FlowFolioProgressBar
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalCoverFlights
import com.flowfolio.core.designsystem.ShelfPlank
import com.flowfolio.core.designsystem.coverLanding
import com.flowfolio.core.designsystem.coverSpot
import com.flowfolio.core.designsystem.detailsCoverKey
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.designsystem.shelfCoverKey
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.LibraryView
import kotlin.math.roundToInt

/** Whether the library is laid out for the unfolded screen (six books a row) rather than the cover screen (three). */
@Composable
internal fun libraryWide(): Boolean = LocalWindowInfo.current.containerDpSize.width >= WIDE

/** Books a row: three on the cover screen, six unfolded (`.libgrid`). */
@Composable
internal fun booksPerRow(): Int = if (libraryWide()) WIDE_PER_ROW else NARROW_PER_ROW

/** The page's side margin (`.scroll` padding): 22 dp folded, 30 dp unfolded. */
@Composable
internal fun libraryGutter(): Dp = if (libraryWide()) 30.dp else 22.dp

/**
 * A book in the library (`.bk`, ADR-0052): its cover at the one 2:3 size with its badges, its title under it, then a
 * thin progress bar, or "Finished" in the success colour, or "Not started". Tap opens its details, the cover flying
 * there; a long press opens the book's menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookTile(item: LibraryItem, origin: String, actions: LibraryActions, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    val book = item.book
    val cover by rememberCover(book)
    val flights = LocalCoverFlights.current
    val spot = remember { CoverSpot() }
    spot.face = book.face(cover)
    var menu by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(
        modifier
            .semantics { contentDescription = book.title }
            .testTag(LibraryTestTags.book(book.id))
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = {
                    if (item.availability != Availability.MISSING) flights?.launch(spot, detailsCoverKey(book.id))
                    actions.onOpen(item, origin)
                },
                onLongClick = { menu = true }
            )
    ) {
        Box {
            BookCover(
                book.face(cover),
                Modifier
                    .fillMaxWidth()
                    .scale(if (pressed) PRESSED else 1f)
                    .coverSpot(spot)
                    .coverLanding(shelfCoverKey(book.id, origin), 0f),
                badges = item.badges()
            )
            item.statusLabel()?.let { label ->
                FlowFolioPill(stringResource(label), FlowFolio.subItem, Modifier.padding(4.dp))
            }
            BookMenu(item, origin, menu, actions) { menu = false }
        }
        Text(
            book.title,
            style = FlowFolioType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp)
        )
        when {
            book.finished -> TileNote(stringResource(R.string.tile_finished), p.ok)
            book.started() -> FlowFolioProgressBar(book.progress.coerceAtLeast(MIN_SHOWN), Modifier.padding(top = 7.dp))
            else -> TileNote(stringResource(R.string.tile_not_started), p.ink3)
        }
    }
}

@Composable
private fun TileNote(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = FlowFolioType.body.copy(fontSize = 12.sp, color = color),
        maxLines = 1,
        modifier = Modifier.padding(top = 2.dp)
    )
}

/**
 * A shelf's head in the library (`.sec`): its name in Fraunces (in the shelf's own colour when it has one) and a
 * "N books ›" link to the whole shelf.
 */
@Composable
internal fun ShelfHeading(
    name: String,
    count: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null,
    tag: String? = null
) {
    val p = FlowFolio.palette
    Row(
        modifier.fillMaxWidth().padding(top = 26.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            style = FlowFolioType.sectionTitle.copy(color = color ?: p.ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            pluralStringResource(R.plurals.shelf_count_link, count, count),
            style = FlowFolioType.body.copy(fontSize = 13.sp, color = p.ink2),
            modifier = Modifier
                .heightIn(min = 40.dp)
                .padding(start = 12.dp)
                .combinedClickable(onClick = onOpen)
                .padding(vertical = 10.dp)
                .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        )
    }
}

/**
 * Books laid out as rows of tiles (`.libgrid`), [perRow] to a row; with [planks], each row stands on a plank, set
 * *below* the titles and progress lines, never across them (ADR-0052). Lazy row by row.
 */
@Suppress("LongParameterList")
internal fun LazyListScope.bookRows(
    keyPrefix: String,
    items: List<LibraryItem>,
    perRow: Int,
    gutter: Dp,
    origin: String,
    actions: LibraryActions,
    planks: Boolean,
    revealFrom: Int = 0
) {
    val rows = items.chunked(perRow)
    itemsIndexed(rows, key = { i, _ -> "$keyPrefix-$i" }) { i, row ->
        Column(Modifier.padding(horizontal = gutter).reveal(revealFrom + i)) {
            Row(horizontalArrangement = Arrangement.spacedBy(if (perRow > NARROW_PER_ROW) 18.dp else 14.dp)) {
                row.forEach { BookTile(it, origin, actions, Modifier.weight(1f)) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
            if (planks) {
                ShelfPlank(Modifier.padding(top = 16.dp, bottom = 6.dp))
            } else {
                Spacer(Modifier.padding(bottom = 16.dp))
            }
        }
    }
}

/**
 * One shelf in the library's Shelf view: all its books in a row that slides sideways across the plank (owner,
 * 2026-11-10: "like before"), each tile as wide as a grid tile so [perRow] show at once. The plank stays put.
 */
@Suppress("LongParameterList")
internal fun LazyListScope.shelfRow(
    key: String,
    items: List<LibraryItem>,
    perRow: Int,
    gutter: Dp,
    origin: String,
    actions: LibraryActions,
    revealFrom: Int
) {
    item(key = key) {
        BoxWithConstraints(Modifier.fillMaxWidth().reveal(revealFrom)) {
            val gap = if (perRow > NARROW_PER_ROW) 18.dp else 14.dp
            val tile = (maxWidth - gutter * 2 - gap * (perRow - 1)) / perRow
            Column {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    modifier = Modifier.testTag(LibraryTestTags.shelfRow(key))
                ) {
                    items(items, key = { it.book.id }) { item -> BookTile(item, origin, actions, Modifier.width(tile)) }
                }
                ShelfPlank(Modifier.padding(horizontal = gutter).padding(top = 16.dp, bottom = 6.dp))
            }
        }
    }
}

/** Books as a list (`.listrow`): a small cover, title, author · format · pages, a progress bar, and where it stands. */
internal fun LazyListScope.bookList(keyPrefix: String, items: List<LibraryItem>, gutter: Dp, actions: LibraryActions) {
    itemsIndexed(items, key = { _, entry -> "$keyPrefix-${entry.book.id}" }) { i, item ->
        BookListRow(item, actions, Modifier.padding(horizontal = gutter).reveal(i))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookListRow(item: LibraryItem, actions: LibraryActions, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    val book = item.book
    val cover by rememberCover(book)
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = book.title }
            .testTag(LibraryTestTags.book(book.id))
            .combinedClickable(onClick = { actions.onOpen(item, LIST_ORIGIN) }, onLongClick = { menu = true })
            .drawBehind {
                val h = 1.dp.toPx()
                drawRect(p.line, Offset(0f, size.height - h), Size(size.width, h))
            }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box {
            BookCover(book.face(cover), Modifier.width(52.dp))
            BookMenu(item, LIST_ORIGIN, menu, actions) { menu = false }
        }
        Column(Modifier.weight(1f)) {
            Text(
                book.title,
                style = FlowFolioType.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(
                    R.string.list_meta,
                    book.author.orEmpty().ifBlank { "—" },
                    book.format.name,
                    pluralStringResource(R.plurals.page_count, book.pageCount, book.pageCount)
                ),
                style = FlowFolioType.body.copy(fontSize = 12.sp, color = p.ink3),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (book.started() && !book.finished) {
                FlowFolioProgressBar(book.progress, Modifier.padding(top = 6.dp).fillMaxWidth(LIST_BAR))
            }
        }
        Text(
            when {
                book.finished -> stringResource(R.string.tile_finished)
                book.started() -> stringResource(R.string.hero_percent, (book.progress * 100).roundToInt())
                else -> stringResource(R.string.tile_new)
            },
            style = FlowFolioType.body.copy(fontSize = 12.sp, color = if (book.finished) p.ok else p.ink3)
        )
    }
}

internal const val LIST_ORIGIN = "list"
private val WIDE = 600.dp
private const val WIDE_PER_ROW = 6
private const val NARROW_PER_ROW = 3
private const val PRESSED = 0.97f
private const val MIN_SHOWN = 0.03f
private const val LIST_BAR = 0.6f
