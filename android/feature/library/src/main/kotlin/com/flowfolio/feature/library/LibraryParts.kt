package com.flowfolio.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.flowfolio.core.designsystem.coverLanding
import com.flowfolio.core.designsystem.coverSpot
import com.flowfolio.core.designsystem.detailsCoverKey
import com.flowfolio.core.designsystem.shelfCoverKey
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.LibrarySort

/** `.hdr`: "Library", the count line, and search, sort and import. */
@Composable
internal fun LibraryHeader(
    state: LibraryUiState,
    searching: Boolean,
    onSearch: () -> Unit,
    actions: LibraryActions,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.library_title), style = FlowFolioType.screenTitle.merge(FlowFolio.title))
            if (!state.libraryEmpty) {
                val books = pluralStringResource(R.plurals.book_count, state.totalCount, state.totalCount)
                Text(
                    stringResource(R.string.library_sub, books, state.inProgressCount),
                    style = FlowFolioType.sub.merge(FlowFolio.hint),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            FlowFolioIconButton(onClick = actions.onImport, modifier = Modifier.testTag(LibraryTestTags.IMPORT)) {
                FlowFolioGlyph(Glyph.Add, contentDescription = stringResource(R.string.action_import))
            }
        }
    }
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
    }

/** `.hero-card`: the book in progress, how far along, and Resume. */
@Composable
internal fun ContinueReadingCard(item: LibraryItem, actions: LibraryActions, modifier: Modifier = Modifier) {
    val book = item.book
    val glow = FlowFolio.palette.item
    FlowFolioCard(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag(LibraryTestTags.HERO),
        radius = 26.dp
    ) {
        // `.hero-card::before`: a soft glow in the item colour, top right.
        Box(
            Modifier.matchParentSize().drawBehind {
                val r = HERO_GLOW.toPx()
                drawCircle(
                    Brush.radialGradient(
                        listOf(glow.copy(alpha = HERO_GLOW_ALPHA), Color.Transparent),
                        center = Offset(size.width + r * 0.1f, -r * 0.1f),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(size.width + r * 0.1f, -r * 0.1f)
                )
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            val cover by rememberCover(book)
            actions.heroSpot.face = book.face(cover)
            Box(Modifier.coverSpot(actions.heroSpot)) {
                ShelfBook(item, HERO_ORIGIN, actions, width = 104.dp, height = 152.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.continue_reading).uppercase(),
                    style = FlowFolioType.eyebrow.merge(FlowFolio.subItem)
                )
                Text(
                    book.title,
                    style = FlowFolioType.heroTitle.merge(FlowFolio.item),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                )
                book.author?.let { Text(it, style = FlowFolioType.sub.merge(FlowFolio.hint), maxLines = 1) }
                FlowFolioProgressBar(book.progress, Modifier.padding(top = 12.dp, bottom = 6.dp))
                Text(
                    stringResource(R.string.page_of, (book.lastPage ?: 0) + 1, book.pageCount),
                    style = FlowFolioType.rowDetail.merge(FlowFolio.hint)
                )
                FlowFolioPrimaryButton(
                    text = stringResource(R.string.action_resume),
                    onClick = { actions.onResume(item) },
                    modifier = Modifier.padding(top = 12.dp).testTag(LibraryTestTags.RESUME),
                    icon = { FlowFolioGlyph(Glyph.Play, contentDescription = null, size = 14.dp, glow = false) }
                )
            }
        }
    }
}

/** `.shelf`: the shelf's name and count, its books standing in a row, and the plank under them. */
@Composable
internal fun ShelfRowView(row: ShelfRow, noShelves: Boolean, actions: LibraryActions, modifier: Modifier = Modifier) {
    val origin = row.shelf?.id?.toString() ?: LOOSE_ORIGIN
    val name = row.shelf?.name ?: stringResource(if (noShelves) R.string.shelf_all_books else R.string.shelf_loose)
    Column(modifier.fillMaxWidth().padding(top = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                name,
                style = FlowFolioType.sectionTitle.merge(FlowFolio.title),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Tapping the count opens the whole shelf (tiles or a list).
            Text(
                pluralStringResource(R.plurals.book_count, row.items.size, row.items.size) + "  ›",
                style = FlowFolioType.sub.copy(fontSize = 13.sp).merge(FlowFolio.subItem),
                modifier = Modifier
                    .clickable { actions.onOpenShelf(row.shelf?.id ?: ShelfPageViewModel.LOOSE) }
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
                    .testTag(LibraryTestTags.shelfCount(row.shelf?.id ?: ShelfPageViewModel.LOOSE))
            )
        }
        // `.shelf-row` with `scroll-snap-type: x mandatory`: swipe sideways through the shelf; it settles on a book.
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            flingBehavior = rememberSnapFlingBehavior(rowState, SnapPosition.Start),
            contentPadding = PaddingValues(start = 30.dp, end = 30.dp, top = 22.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            items(row.items, key = { it.book.id }) { item ->
                val h = SHELF_BOOK_HEIGHT + minOf(SHELF_BOOK_EXTRA, item.book.pageCount / PAGES_PER_DP).dp
                ShelfBook(item, origin, actions, width = SHELF_BOOK_WIDTH, height = h)
            }
        }
        ShelfPlank(Modifier.padding(horizontal = 14.dp))
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
    height: androidx.compose.ui.unit.Dp
) {
    val book = item.book
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var menu by remember { mutableStateOf(false) }
    val cover by rememberCover(book)
    val flights = LocalCoverFlights.current
    val spot = remember { CoverSpot() }
    spot.face = book.face(cover)
    Column(Modifier.width(width + BOOK_SPINE_ROOM)) {
        Box(
            Modifier
                .semantics { contentDescription = book.title }
                .testTag(LibraryTestTags.book(book.id))
                .combinedClickable(
                    interactionSource = source,
                    indication = null,
                    onClick = {
                        // The cover flies to the details page; a missing book goes to Locate instead.
                        if (item.availability != Availability.MISSING) flights?.launch(spot, detailsCoverKey(book.id))
                        actions.onOpen(item, origin)
                    },
                    onLongClick = { menu = true }
                )
        ) {
            Book3D(
                book.face(cover),
                width,
                height,
                modifier = Modifier.coverSpot(spot).coverLanding(shelfCoverKey(book.id, origin), SHELF_ANGLE),
                lifted = pressed
            )
            BookBadge(item, Modifier.align(Alignment.TopStart))
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

@Composable
internal fun SortMenu(current: LibrarySort, onSort: (LibrarySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FlowFolioIconButton(onClick = { open = true }) {
            FlowFolioGlyph(Glyph.Sort, contentDescription = stringResource(R.string.action_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                LibrarySort.RECENT to R.string.sort_recent,
                LibrarySort.TITLE to R.string.sort_title
            ).forEach { (sort, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(label),
                            style = if (sort ==
                                current
                            ) {
                                FlowFolio.title
                            } else {
                                FlowFolio.item
                            }
                        )
                    },
                    onClick = {
                        open = false
                        onSort(sort)
                    }
                )
            }
        }
    }
}

internal const val HERO_ORIGIN = "hero"
private const val LOOSE_ORIGIN = "loose"
private const val SEARCH_FILL = 0.6f
private const val HERO_GLOW_ALPHA = 0.22f
private const val PAGES_PER_DP = 70
private val HERO_GLOW = 110.dp
private val SHELF_BOOK_WIDTH = 90.dp
private val SHELF_BOOK_HEIGHT = 124.dp
private const val SHELF_BOOK_EXTRA = 18
private val BOOK_SPINE_ROOM = 8.dp
