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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.flowfolio.core.designsystem.BookCover
import com.flowfolio.core.designsystem.CoachMark
import com.flowfolio.core.designsystem.CoverSpot
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioChip
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioMenu
import com.flowfolio.core.designsystem.FlowFolioMenuDivider
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
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.pageLabel
import com.flowfolio.core.model.printedPages

/**
 * The library's head (`.hrow`, ADR-0052): "Library" and its buttons: the reading goal (folded only; unfolded it is the
 * ring in Continue reading), Sort and view, and Import.
 */
@Composable
internal fun LibraryHeader(
    state: LibraryUiState,
    wide: Boolean,
    actions: LibraryActions,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.library_title),
            style = (if (wide) FlowFolioType.screenTitleWide else FlowFolioType.screenTitle)
                .copy(color = FlowFolio.palette.ink),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = TITLE_MIN_SP,
                maxFontSize = (if (wide) FlowFolioType.screenTitleWide else FlowFolioType.screenTitle).fontSize
            ),
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!state.libraryEmpty) {
                if (!wide) {
                    FlowFolioIconButton(
                        onClick = actions.onReadingGoal,
                        tooltip = stringResource(R.string.action_reading_goal),
                        modifier = Modifier.testTag(LibraryTestTags.READING_GOAL)
                    ) {
                        FlowFolioGlyph(Glyph.ReadingGoal, null, size = 20.dp)
                    }
                }
                SortViewMenu(state.sort, state.view, actions)
            }
            CoachMark(
                visible = actions.welcomeVisible,
                title = stringResource(R.string.guide_welcome_title),
                body = stringResource(R.string.guide_welcome_body),
                dismissLabel = stringResource(R.string.guide_got_it),
                neverLabel = stringResource(R.string.guide_never_show),
                onDismiss = actions.onWelcomeSeen,
                onNeverShow = actions.onNeverShowTips
            ) {
                ImportMenu(actions)
            }
        }
    }
}

/**
 * The library's search box. [autoFocus] puts the cursor in it and raises the keyboard as it appears, so opening
 * search is one tap rather than two (owner, M4).
 *
 * Its outline is the theme's own title colour rather than the faint hairline the rest of the app uses, so the box
 * reads as the one thing waiting for input, in whichever theme is on (owner, M4).
 */
@Composable
internal fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    hintRes: Int = R.string.search_hint,
    autoFocus: Boolean = false
) {
    val p = FlowFolio.palette
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autoFocus) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Box(contentAlignment = Alignment.Center) {
        if (isFocused) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(4.dp)
                    .drawBehind {
                        val radius = 16.dp.toPx()
                        drawCircle(
                            Brush.radialGradient(listOf(p.ink.copy(alpha = 0.3f), Color.Transparent), center, radius),
                            radius = radius,
                            center = center
                        )
                    }
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text(stringResource(hintRes), style = FlowFolio.hint) },
            leadingIcon = { FlowFolioGlyph(Glyph.Search, contentDescription = null, size = 20.dp, glow = false) },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = p.ink,
                unfocusedBorderColor = p.ink.copy(alpha = SEARCH_OUTLINE),
                cursorColor = p.ink,
                focusedTextColor = p.text,
                unfocusedTextColor = p.text,
                focusedContainerColor = p.surface.copy(alpha = SEARCH_FILL),
                unfocusedContainerColor = p.surface.copy(alpha = SEARCH_FILL)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .testTag(LibraryTestTags.SEARCH_FIELD)
        )
    }
}

/** `.chips`. */
@Composable
internal fun FilterChips(
    filters: List<LibraryFilter>,
    selected: LibraryFilter,
    onFilter: (LibraryFilter) -> Unit,
    gutter: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = gutter, end = gutter, top = 18.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters) { f ->
            FlowFolioChip(stringResource(f.label), selected = f == selected, onClick = { onFilter(f) }, glyph = f.glyph)
        }
    }
}

/** Each chip's glyph (owner, M3T2). */
private val LibraryFilter.glyph: Glyph
    get() = when (this) {
        LibraryFilter.ALL -> Glyph.Library
        LibraryFilter.IN_PROGRESS -> Glyph.Hourglass
        LibraryFilter.NOT_STARTED -> Glyph.Book
        LibraryFilter.FINISHED -> Glyph.Select
        LibraryFilter.FAVORITES -> Glyph.Star
        LibraryFilter.TEXTBOOKS -> Glyph.OpenBook
        LibraryFilter.NOVELS -> Glyph.Cover
        LibraryFilter.RECENT -> Glyph.Clock
        LibraryFilter.FOLDERS -> Glyph.Folder
    }

private val LibraryFilter.label: Int
    get() = when (this) {
        LibraryFilter.ALL -> R.string.filter_all
        LibraryFilter.IN_PROGRESS -> R.string.filter_in_progress
        LibraryFilter.NOT_STARTED -> R.string.filter_not_started
        LibraryFilter.FINISHED -> R.string.filter_finished
        LibraryFilter.FAVORITES -> R.string.filter_favorites
        LibraryFilter.RECENT -> R.string.filter_recent
        LibraryFilter.TEXTBOOKS -> R.string.filter_textbooks
        LibraryFilter.NOVELS -> R.string.filter_novels
        LibraryFilter.FOLDERS -> R.string.filter_folders
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
            // The one 2:3 cover of ADR-0052 everywhere books are browsed; [Book3D] is kept for the flights.
            val lifted = pressed || reorder?.dragging == book.id
            BookCover(
                book.face(cover),
                Modifier
                    .width(width)
                    .graphicsLayer {
                        val s = if (lifted) LIFT_SCALE else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .coverSpot(spot)
                    .coverLanding(shelfCoverKey(book.id, origin), 0f),
                badges = item.badges()
            )
            item.statusLabel()?.let { FlowFolioPill(stringResource(it), FlowFolio.subItem, Modifier.padding(4.dp)) }
            selection?.let { SelectionMark(it, book.id) }
            BookMenu(item, origin, menu, actions) { menu = false }
        }
    }
}

private const val LIFT_SCALE = 1.04f

internal const val HERO_ORIGIN = "hero"

/** The search box's resting outline: the theme's title colour, dimmed but still clearly a line. */
private const val SEARCH_OUTLINE = 0.55f

private const val SEARCH_FILL = 0.6f

internal val BOOK_SPINE_ROOM = 8.dp

private val TITLE_MIN_SP = 34.sp
