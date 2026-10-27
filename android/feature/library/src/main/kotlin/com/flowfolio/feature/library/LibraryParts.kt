package com.flowfolio.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.flowfolio.core.designsystem.Book3D
import com.flowfolio.core.designsystem.CoverSpot
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioChip
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioPill
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioProgressBar
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalCoverFlights
import com.flowfolio.core.designsystem.SHELF_ANGLE
import com.flowfolio.core.designsystem.ShelfPlank
import com.flowfolio.core.designsystem.color
import com.flowfolio.core.designsystem.coverLanding
import com.flowfolio.core.designsystem.coverSpot
import com.flowfolio.core.designsystem.detailsCoverKey
import com.flowfolio.core.designsystem.jiggle
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.designsystem.shelfCoverKey
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.pageLabel
import com.flowfolio.core.model.printedPages

/**
 * `.hdr`: "Library", the library's totals, and search, sort and import. On a narrow screen (the cover screen) the
 * title keeps its one line and the totals and buttons share the row under it (owner, 2026-10-22).
 */
@Composable
internal fun LibraryHeader(
    state: LibraryUiState,
    stats: LibraryStats,
    searching: Boolean,
    onSearch: () -> Unit,
    actions: LibraryActions,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 10.dp, bottom = 6.dp)) {
        val narrow = maxWidth < NARROW_HEADER
        val title = @Composable {
            Text(
                stringResource(R.string.library_title),
                style = FlowFolioType.screenTitle.merge(FlowFolio.title),
                maxLines = 1,
                softWrap = false
            )
        }
        val totals = @Composable { if (!state.libraryEmpty) LibraryTotals(state, stats, Modifier.padding(top = 8.dp)) }
        val buttons = @Composable { HeaderButtons(state, searching, onSearch, actions) }
        if (narrow) {
            Column(Modifier.fillMaxWidth()) {
                title()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) { totals() }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) { buttons() }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    title()
                    totals()
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    buttons()
                }
            }
        }
    }
}

/** Search, sort and import. No select mode here: bulk deletion from the whole library was too easy to do by
 * accident (owner, 2026-10-23); shelf pages keep it. */
@Composable
private fun HeaderButtons(state: LibraryUiState, searching: Boolean, onSearch: () -> Unit, actions: LibraryActions) {
    if (!state.libraryEmpty) {
        FlowFolioIconButton(onClick = onSearch, modifier = Modifier.testTag(LibraryTestTags.SEARCH)) {
            FlowFolioGlyph(
                Glyph.Search,
                contentDescription = stringResource(R.string.action_search),
                color = if (searching) FlowFolio.palette.title else FlowFolio.palette.glyph
            )
        }
        SortMenu(state.sort, actions.onSort)
    }
    ImportMenu(actions)
}

@Composable
internal fun SearchField(query: String, onQuery: (String) -> Unit) {
    val p = FlowFolio.palette
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint), style = FlowFolio.hint) },
        leadingIcon = { FlowFolioGlyph(Glyph.Search, contentDescription = null, size = 20.dp, glow = false) },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = p.title,
            unfocusedBorderColor = p.edge,
            cursorColor = p.title,
            focusedTextColor = p.text,
            unfocusedTextColor = p.text,
            focusedContainerColor = p.surface.copy(alpha = SEARCH_FILL),
            unfocusedContainerColor = p.surface.copy(alpha = SEARCH_FILL)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .testTag(LibraryTestTags.SEARCH_FIELD)
    )
}

/** `.chips`. */
@Composable
internal fun FilterChips(
    filters: List<LibraryFilter>,
    selected: LibraryFilter,
    onFilter: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters) { f ->
            FlowFolioChip(stringResource(f.label), selected = f == selected, onClick = { onFilter(f) })
        }
    }
}

private val LibraryFilter.label: Int
    get() = when (this) {
        LibraryFilter.ALL -> R.string.filter_all
        LibraryFilter.IN_PROGRESS -> R.string.filter_in_progress
        LibraryFilter.NOT_STARTED -> R.string.filter_not_started
        LibraryFilter.RECENT -> R.string.filter_recent
        LibraryFilter.TEXTBOOKS -> R.string.filter_textbooks
        LibraryFilter.NOVELS -> R.string.filter_novels
        LibraryFilter.FOLDERS -> R.string.filter_folders
    }

/** `.shelf`: the shelf's name and count, its books standing in a row, and the plank under them. */
@Composable
internal fun ShelfRowView(
    row: ShelfRow,
    noShelves: Boolean,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
    order: Int = 0
) {
    val origin = row.shelf?.id?.toString() ?: LOOSE_ORIGIN
    val name = row.shelf?.name ?: stringResource(if (noShelves) R.string.shelf_all_books else R.string.shelf_loose)
    // A shelf in its own colour; books not on a shelf (and shelves without one) keep the theme's (owner, 2026-10-22).
    val accent = row.shelf?.color?.color
    Column(modifier.fillMaxWidth().padding(top = 22.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).reveal(order),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                name,
                style = FlowFolioType.sectionTitle.merge(FlowFolio.title).let {
                    if (accent !=
                        null
                    ) {
                        it.copy(color = accent)
                    } else {
                        it
                    }
                },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Tapping the count opens the whole shelf (tiles or a list).
            // A bold pill on a soft shade (owner, 2026-10-22).
            Text(
                pluralStringResource(R.plurals.book_count, row.items.size, row.items.size) + "  ›",
                style = FlowFolioType.sub.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold).merge(FlowFolio.subItem),
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(FlowFolio.palette.subItem.copy(alpha = COUNT_SHADE))
                    .clickable { actions.onOpenShelf(row.shelf?.id ?: ShelfPageViewModel.LOOSE) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag(LibraryTestTags.shelfCount(row.shelf?.id ?: ShelfPageViewModel.LOOSE))
            )
        }
        ShelfBooks(row, origin, actions, order)
        ShelfPlank(Modifier.padding(horizontal = 14.dp).reveal(order), accent)
    }
}

/** A shelf's books standing in a row that swipes sideways; a real shelf's can be dragged into a new order. */
@Composable
private fun ShelfBooks(row: ShelfRow, origin: String, actions: LibraryActions, order: Int) {
    // `.shelf-row` with `scroll-snap-type: x mandatory`: swipe sideways through the shelf; it settles on a book.
    // As tall as the tallest book can be, whichever books are in view, so the plank never moves while the row
    // scrolls (a lazy row is otherwise as tall as the books it shows; owner, 2026-10-26).
    val rowState = rememberLazyListState()
    // A shelf's books can be dragged into a new order (owner, 2026-10-26); the books on no shelf cannot.
    val shelfId = row.shelf?.id
    val reorder = if (shelfId != null && actions.onReorder != null) {
        rememberRowReorder(row.items.map { it.book.id }, rowState) { actions.onReorder.invoke(shelfId, it) }
    } else {
        null
    }
    val byId = row.items.associateBy { it.book.id }
    val shown = reorder?.order?.mapNotNull(byId::get) ?: row.items
    LazyRow(
        state = rowState,
        modifier = Modifier.height(SHELF_ROW_TOP + SHELF_BOOK_HEIGHT + SHELF_BOOK_EXTRA.dp + SHELF_ROW_BOTTOM),
        flingBehavior = rememberSnapFlingBehavior(rowState, SnapPosition.Start),
        contentPadding = PaddingValues(start = 30.dp, end = 30.dp, top = SHELF_ROW_TOP, bottom = SHELF_ROW_BOTTOM),
        horizontalArrangement = Arrangement.spacedBy(SHELF_BOOK_GAP),
        verticalAlignment = Alignment.Bottom
    ) {
        itemsIndexed(shown, key = { _, entry -> entry.book.id }) { i, item ->
            val h = SHELF_BOOK_HEIGHT + minOf(SHELF_BOOK_EXTRA, item.book.pageCount / PAGES_PER_DP).dp
            val dragged = reorder?.dragging == item.book.id
            Box(
                Modifier.zIndex(if (dragged) 1f else 0f)
                    .then(if (dragged) Modifier else Modifier.animateItem())
                    .reveal(order + 1 + i)
            ) {
                ShelfBook(item, origin, actions, width = SHELF_BOOK_WIDTH, height = h, reorder = reorder)
            }
        }
    }
}

/**
 * A book on a shelf: the 3D book (lifts while pressed) and a badge when its file is missing or locked; its title is
 * its accessibility label. Tap opens its details; a long press offers shelves, locating the file and removing the book.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShelfBook(
    item: LibraryItem,
    origin: String,
    actions: LibraryActions,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    shadow: Boolean = true,
    /** Drag and drop on a shelf; null where books keep their order. */
    reorder: ReorderState? = null
) {
    val book = item.book
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var menu by remember { mutableStateOf(false) }
    val cover by rememberCover(book)
    val flights = LocalCoverFlights.current
    val spot = remember { CoverSpot() }
    spot.face = book.face(cover)
    val selection = actions.selection
    val selecting = selection?.active == true
    val open = {
        if (selection != null && selecting) {
            selection.toggle(book.id)
        } else {
            // The cover flies to the details page; a missing book goes to Locate instead.
            if (item.availability != Availability.MISSING) flights?.launch(spot, detailsCoverKey(book.id))
            actions.onOpen(item, origin)
        }
    }
    val gestures = if (reorder != null && !selecting) {
        Modifier.reorderable(reorder, book.id, onTap = open, onHold = { menu = true })
    } else {
        Modifier.combinedClickable(
            interactionSource = source,
            indication = null,
            onClick = open,
            onLongClick = { if (!selecting) menu = true }
        )
    }
    Column(Modifier.width(width + BOOK_SPINE_ROOM)) {
        Box(
            Modifier
                .jiggle(selecting, book.id)
                .semantics { contentDescription = book.title }
                .testTag(LibraryTestTags.book(book.id))
                .then(gestures)
        ) {
            Book3D(
                book.face(cover),
                width,
                height,
                modifier = Modifier.coverSpot(spot).coverLanding(shelfCoverKey(book.id, origin), SHELF_ANGLE),
                lifted = pressed || reorder?.dragging == book.id,
                shadow = shadow
            )
            BookBadge(item, Modifier.align(Alignment.TopStart))
            selection?.let { SelectionMark(it, book.id) }
            BookMenu(item, menu, actions) { menu = false }
        }
    }
}

@Composable
private fun BookBadge(item: LibraryItem, modifier: Modifier) {
    val label = when {
        item.availability == Availability.MISSING -> R.string.badge_missing
        item.book.passwordProtected -> R.string.badge_locked
        else -> return
    }
    FlowFolioPill(stringResource(label), FlowFolio.subItem, modifier.padding(4.dp))
}

@Composable
private fun BookMenu(item: LibraryItem, open: Boolean, actions: LibraryActions, onClose: () -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onClose) {
        actions.selection?.let { selection ->
            MenuEntry(R.string.action_select) {
                onClose()
                selection.startWith(item.book.id)
            }
        }
        MenuEntry(R.string.action_add_to_shelf) {
            onClose()
            actions.onAddToShelf(item.book)
        }
        actions.onBookmarks?.let { bookmarks ->
            MenuEntry(R.string.action_bookmarks) {
                onClose()
                bookmarks(item.book)
            }
        }
        actions.onRename?.let { rename ->
            MenuEntry(R.string.action_rename) {
                onClose()
                rename(item.book)
            }
        }
        if (item.book.storage is BookStorage.Linked) {
            MenuEntry(R.string.action_locate) {
                onClose()
                actions.onLocate(item.book)
            }
        }
        MenuEntry(R.string.action_delete, warn = true) {
            onClose()
            actions.onDelete(item.book)
        }
    }
}

@Composable
private fun MenuEntry(label: Int, warn: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label), style = if (warn) FlowFolio.subItem else FlowFolio.title) },
        onClick = onClick
    )
}

internal const val HERO_ORIGIN = "hero"

/** Narrower than this, the header's buttons go under the title. */
private val NARROW_HEADER = 560.dp
private const val COUNT_SHADE = 0.14f
private const val LOOSE_ORIGIN = "loose"
private const val SEARCH_FILL = 0.6f
private const val PAGES_PER_DP = 70
private val SHELF_BOOK_WIDTH = 90.dp
private val SHELF_BOOK_HEIGHT = 124.dp
private const val SHELF_BOOK_EXTRA = 18
internal val BOOK_SPINE_ROOM = 8.dp

/** Half the mock's 22 dp between books (owner, 2026-10-26). */
private val SHELF_BOOK_GAP = 11.dp
private val SHELF_ROW_TOP = 22.dp
private val SHELF_ROW_BOTTOM = 6.dp
