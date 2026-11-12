@file:Suppress("TooManyFunctions")

package com.flowfolio.feature.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.Book3D
import com.flowfolio.core.designsystem.ButtonStyle
import com.flowfolio.core.designsystem.CoverSpot
import com.flowfolio.core.designsystem.DialogAction
import com.flowfolio.core.designsystem.DialogField
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioButton
import com.flowfolio.core.designsystem.FlowFolioDialog
import com.flowfolio.core.designsystem.FlowFolioGhostButton
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioMenu
import com.flowfolio.core.designsystem.FlowFolioMenuDivider
import com.flowfolio.core.designsystem.FlowFolioMenuItem
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioTabs
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.FlowFolioUnderlineTabs
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalBookOpener
import com.flowfolio.core.designsystem.LocalCoverFlights
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.coverLanding
import com.flowfolio.core.designsystem.coverSpot
import com.flowfolio.core.designsystem.detailsCoverKey
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.designsystem.shelfCoverKey
import com.flowfolio.core.designsystem.swipeTabs
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.ReadingIntent
import com.flowfolio.core.model.pageLabel
import kotlinx.coroutines.launch

/**
 * A book's details page (the mock's `#s-details`). [origin] is the library row the book was opened from, so its cover
 * flies in from there. [onRead] opens the reader; it runs after the book-opening hinge. [onEdit] opens Edit details;
 * [onOpenBook] another book's details (the Author tab's other books).
 */
@Composable
fun DetailsScreen(
    origin: String,
    onBack: () -> Unit,
    onRead: (Book) -> Unit,
    onEdit: (Book) -> Unit = {},
    onOpenBook: (Book) -> Unit = {},
    onNotebook: (Book) -> Unit = {},
    onShelf: (Long) -> Unit = {},
    viewModel: DetailsViewModel = hiltViewModel(),
    shelvesVm: ShelvesViewModel = hiltViewModel(),
    libraryVm: LibraryViewModel = hiltViewModel()
) {
    val base by viewModel.uiState.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val state = base.copy(tags = tags, allTags = allTags)
    var picking by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val book = state.book
    val relink = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.relink(uri.toString())
    }
    val locate = { relink.launch(arrayOf("application/pdf", "application/epub+zip")) }
    LaunchedEffect(state.loading, book) { if (!state.loading && book == null) onBack() }
    if (book == null) return
    val hero = remember { CoverSpot().apply { angle = HERO_ANGLE } }
    val opener = LocalBookOpener.current
    val open: (Int?) -> Unit = { page ->
        viewModel.markOpened()
        val show: () -> Unit = { if (page != null) viewModel.startAt(page) { onRead(book) } else onRead(book) }
        opener?.open(hero, show) ?: show()
    }
    val flights = LocalCoverFlights.current
    val actions = DetailsActions(
        onBack = {
            // The cover flies back to the shelf it came from (when there is one).
            if (origin.isNotEmpty()) flights?.launch(hero, shelfCoverKey(book.id, origin))
            onBack()
        },
        onRead = { if (state.availability == Availability.MISSING) locate() else open(null) },
        onChapter = { open(it) },
        onShelves = { picking = true },
        onGoal = rememberBookGoalDialog(book, state.bookGoal, viewModel),
        onLocate = locate,
        onDelete = { libraryVm.onRequestDelete(book) },
        onEdit = { onEdit(book) },
        onBook = onOpenBook,
        onFavorite = { libraryVm.toggleFavorite(book) },
        onIntent = { libraryVm.setIntent(book, it) },
        onFinished = { libraryVm.toggleFinished(book) },
        onNotebook = { onNotebook(book) },
        onAddToShelf = { picking = true },
        onExportNotes = { scope.launch { share(context, viewModel.notesMarkdown(), "text/markdown") } },
        onResetProgress = { resetting = true },
        onShelf = onShelf,
        onAddTag = { viewModel.addTag(it) },
        onRemoveTag = { viewModel.removeTag(it) }
    )
    // System Back flies the cover home too.
    BackHandler(onBack = actions.onBack)
    DetailsContent(state, book, hero, actions)
    LibraryDialogs(libraryVm.uiState.collectAsStateWithLifecycle().value.dialog, libraryVm)
    if (picking) ShelveBookFlow(book, state.shelves, shelvesVm) { picking = false }
    if (resetting) ResetProgressDialog({ resetting = false }) { viewModel.resetProgress() }
}

internal data class DetailsActions(
    val onBack: () -> Unit = {},
    val onRead: () -> Unit = {},
    val onChapter: (Int) -> Unit = {},
    val onShelves: () -> Unit = {},
    val onGoal: () -> Unit = {},
    val onLocate: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onBook: (Book) -> Unit = {},
    /** Makes the book a favourite, or not; marks it finished, or not (owner, M3T2). */
    val onFavorite: () -> Unit = {},
    val onFinished: () -> Unit = {},
    /** The reader's own intention for the book (owner, M5). */
    val onIntent: (ReadingIntent?) -> Unit = {},
    /** This book's notebook, optionally on one kind (ADR-0052). */
    val onNotebook: () -> Unit = {},
    val onAddToShelf: () -> Unit = {},
    val onExportNotes: () -> Unit = {},
    /** Asks, then starts the book again from its first page. */
    val onResetProgress: () -> Unit = {},
    /** Opens one of the book's shelves (the Overview's shelf chip). */
    val onShelf: (Long) -> Unit = {},
    /** The book's own tags (owner, 2026-11-10). */
    val onAddTag: (String) -> Unit = {},
    val onRemoveTag: (String) -> Unit = {}
)

/**
 * The details page (`.det`, ADR-0052; owner, 2026-11-10: "the same setup as the mock"). Folded, one centred column:
 * the cover, title, author, the actions, the four facts and the tabs. Unfolded, the cover stands in a 240 dp column
 * on the left and everything else reads down the right, aligned to the start. No blurred backdrop, and no shadow
 * under the book.
 */
@Composable
internal fun DetailsContent(state: DetailsUiState, book: Book, hero: CoverSpot, actions: DetailsActions) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // A sideways swipe anywhere on the page moves between the tabs (owner, 2026-10-23).
    BoxWithConstraints(Modifier.fillMaxSize().swipeTabs(tab, TAB_COUNT) { tab = it }.testTag(LibraryTestTags.DETAILS)) {
        val wide = maxWidth >= TWO_PANE_WIDTH
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(start = SIDE, end = SIDE, top = TOP_ROOM, bottom = 24.dp)
        ) {
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Box(Modifier.width(WIDE_COVER_COLUMN).padding(top = 4.dp)) {
                        DetailsCover(book, hero, WIDE_COVER)
                    }
                    Column(Modifier.weight(1f)) {
                        DetailsHeading(state, book, actions, wide = true)
                        FactTiles(state, book, wide = true)
                        DetailsTabs(state, book, tab, { tab = it }, actions)
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DetailsCover(book, hero, FOLDED_COVER)
                }
                DetailsHeading(state, book, actions, wide = false, modifier = Modifier.padding(top = 18.dp))
                FactTiles(state, book, wide = false)
                DetailsTabs(state, book, tab, { tab = it }, actions)
            }
        }
        TopButtons(state, actions)
    }
}

/**
 * The cover (`.covwrap`): the 3D book swaying gently, with no contact shadow under it (owner, 2026-11-10). [hero]
 * records where the book is, for the book-opening animation.
 */
@Composable
private fun DetailsCover(book: Book, hero: CoverSpot, width: Dp) {
    val cover by rememberCover(book)
    val sway = rememberInfiniteTransition(label = "sway")
    val t by sway.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(SWAY_MS, easing = Motion.EaseInOut), RepeatMode.Reverse),
        label = "swayT"
    )
    hero.face = book.face(cover)
    Box(Modifier.graphicsLayer { translationY = -SWAY_RISE.toPx() * t }.coverSpot(hero)) {
        Book3D(
            book.face(cover),
            width,
            width * COVER_RATIO,
            modifier = Modifier.coverLanding(detailsCoverKey(book.id), SWAY_FROM),
            restAngle = SWAY_FROM + (SWAY_TO - SWAY_FROM) * t,
            shadow = false
        )
    }
}

/**
 * Title, subtitle, the author in the accent, the format line, then the actions (`.det h2` ... `.acts`): Continue (or
 * Start reading, Read again, Locate), Shelf, Goal and the star. Centred folded, aligned to the start unfolded.
 */
@Suppress("LongMethod") // one label per state of the book
@Composable
private fun DetailsHeading(
    state: DetailsUiState,
    book: Book,
    actions: DetailsActions,
    wide: Boolean,
    modifier: Modifier = Modifier
) {
    var showFullTitle by remember { mutableStateOf(false) }
    var truncated by remember { mutableStateOf(false) }
    val p = FlowFolio.palette
    val align = if (wide) TextAlign.Start else TextAlign.Center
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally
    ) {
        Text(
            book.title,
            style = FlowFolioType.bookTitle.copy(
                fontSize = if (wide) 36.sp else 28.sp,
                lineHeight = if (wide) 39.sp else 30.sp,
                color = p.ink
            ),
            textAlign = align,
            // Three lines at most; a longer title truncates and the ellipsis opens it whole (owner, 2026-11-07).
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { truncated = it.hasVisualOverflow },
            modifier = Modifier
                .reveal(1)
                .clickable(enabled = truncated) { showFullTitle = true }
        )
        book.details.subtitle?.let {
            Text(
                it,
                style = FlowFolioType.sub.copy(color = p.ink2),
                textAlign = align,
                modifier = Modifier.padding(top = 6.dp).reveal(2)
            )
        }
        book.author?.let {
            Text(
                it,
                style = FlowFolioType.rowLabel.copy(color = p.accent, fontWeight = FontWeight.SemiBold),
                textAlign = align,
                modifier = Modifier.padding(top = 8.dp).reveal(2)
            )
        }
        DetailsMeta(state, book, Modifier.padding(top = 6.dp).reveal(3))
        DetailsActionsRow(state, book, actions, wide)
    }
    if (showFullTitle) FullTitleDialog(book.title) { showFullTitle = false }
}

/** The actions under the title (`.acts`): Continue (or Start reading, Read again, Locate), Shelf, Goal and the star. */
@Composable
private fun DetailsActionsRow(state: DetailsUiState, book: Book, actions: DetailsActions, wide: Boolean) {
    val p = FlowFolio.palette
    // The button says where it lands (ADR-0052): "Continue · p. 55", "Start reading" or "Read again".
    val read = when {
        state.availability == Availability.MISSING -> stringResource(R.string.action_locate)
        book.finished -> stringResource(R.string.details_read_again)
        (book.lastPage ?: 0) > 0 -> stringResource(R.string.details_continue_at, book.pageLabel(book.lastPage ?: 0))
        else -> stringResource(R.string.details_start)
    }
    FlowRow(
        Modifier.padding(top = 16.dp).reveal(4),
        horizontalArrangement = Arrangement.spacedBy(
            8.dp,
            if (wide) Alignment.Start else Alignment.CenterHorizontally
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        // Unfolded the right column is narrower than the mock's, so the row takes the compact size to stay on one line.
        FlowFolioButton(
            read,
            actions.onRead,
            Modifier.testTag(LibraryTestTags.READ),
            small = wide,
            icon = Glyph.Play
        )
        FlowFolioButton(
            stringResource(R.string.action_add_to_shelf_compact),
            actions.onShelves,
            Modifier.testTag(LibraryTestTags.ADD_TO_SHELF),
            style = ButtonStyle.GHOST,
            small = wide,
            icon = Glyph.Shelves
        )
        FlowFolioButton(
            stringResource(R.string.action_add_goal_short),
            actions.onGoal,
            Modifier.testTag(LibraryTestTags.ADD_GOAL),
            style = ButtonStyle.GHOST,
            small = wide,
            icon = Glyph.ReadingGoal
        )
        FlowFolioIconButton(
            onClick = actions.onFavorite,
            tooltip = stringResource(
                if (book.favorite) R.string.action_favorite_remove else R.string.action_favorite_add
            ),
            modifier = Modifier.testTag(LibraryTestTags.DETAILS_FAVORITE)
        ) {
            FlowFolioGlyph(
                if (book.favorite) Glyph.StarFilled else Glyph.Star,
                null,
                color = if (book.favorite) p.accent else p.ink2,
                size = 20.dp,
                glow = false
            )
        }
    }
}

/** The full book title in a small window, opened by tapping a title that truncated (owner, 2026-11-07). */
@Composable
private fun FullTitleDialog(title: String, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = FULL_TITLE_WIDTH)
                .fillMaxWidth()
                .clip(shape)
                .background(FlowFolio.palette.background, shape)
                .border(1.dp, FlowFolio.palette.edge, shape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, style = FlowFolioType.bookTitle.merge(FlowFolio.title), textAlign = TextAlign.Center)
            FlowFolioGhostButton(text = stringResource(R.string.action_close), onClick = onDismiss)
        }
    }
}

/** Back and More at the top of the page (`.hrow`). */
@Composable
private fun TopButtons(state: DetailsUiState, actions: DetailsActions) {
    var menu by remember { mutableStateOf(false) }
    val p = FlowFolio.palette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .reveal(0),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        FlowFolioIconButton(onClick = actions.onBack, glass = true, tooltip = stringResource(R.string.action_back)) {
            FlowFolioGlyph(Glyph.Back, stringResource(R.string.action_back), color = p.text)
        }
        Box {
            FlowFolioIconButton(
                onClick = { menu = true },
                glass = true,
                tooltip = stringResource(R.string.action_more)
            ) {
                FlowFolioGlyph(Glyph.More, stringResource(R.string.action_more), color = p.text)
            }
            DetailsMenu(state, menu, actions) { menu = false }
        }
    }
}

@Composable
private fun DetailsTabs(state: DetailsUiState, book: Book, tab: Int, onTab: (Int) -> Unit, actions: DetailsActions) {
    // The mock's order (ADR-0052): Overview, Contents, Notebook, Details, Author.
    val tabs = listOf(
        stringResource(R.string.tab_overview),
        stringResource(R.string.tab_contents),
        stringResource(R.string.tab_notebook),
        stringResource(R.string.tab_details),
        stringResource(R.string.tab_author)
    )
    Box(Modifier.padding(top = 26.dp).reveal(5).horizontalScroll(rememberScrollState())) {
        FlowFolioTabs(tabs, tab, onTab, counts = listOf(null, null, state.counts.total, null, null))
    }
    // The new tab's content slides in from the side its tab lies on, the old one fades (ADR-0033).
    AnimatedContent(
        tab,
        Modifier.padding(top = 22.dp, bottom = 10.dp).reveal(6),
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (
                slideInHorizontally(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) { it / TAB_SLIDE * dir } +
                    fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut))
                )
                .togetherWith(fadeOut(tween(Motion.FAST_MS)))
                .using(SizeTransform(clip = false))
        },
        label = "detailsTab"
    ) { shown ->
        when (shown) {
            0 -> OverviewPanel(state, book, actions)
            1 -> ContentsPanel(state.contents, book, actions.onChapter)
            2 -> NotebookPanel(state, actions.onNotebook)
            3 -> InfoPanel(state, book)
            else -> AuthorPanel(state, book, actions.onBook)
        }
    }
}

/** The play mark on Read and Resume, in the theme's colour for an icon on a primary button. */
@Composable
internal fun PlayGlyph() {
    FlowFolioGlyph(
        Glyph.Play,
        contentDescription = null,
        color = FlowFolio.palette.buttonGlyph,
        // Large enough to read as "play" at a glance (owner, 2026-10-23).
        size = 20.dp,
        glow = false
    )
}

private val TWO_PANE_WIDTH = 600.dp
private val FULL_TITLE_WIDTH = 440.dp
private const val TAB_SLIDE = 8
private const val TAB_COUNT = 5

private val SIDE = 22.dp

/** Room for Back and ⋯ above the page. */
private val TOP_ROOM = 64.dp
private val WIDE_COVER_COLUMN = 200.dp
private val WIDE_COVER = 190.dp
private val FOLDED_COVER = 170.dp
private const val COVER_RATIO = 1.5f
private val SWAY_RISE = 8.dp
private const val SWAY_MS = 6000
private const val SWAY_FROM = 22f
private const val SWAY_TO = 8f

/** The sway's middle angle: where the opening flight starts from. */
private const val HERO_ANGLE = 15f

/** Reset progress (ADR-0052): asks first, since it moves the reading position back to the first page. */
@Composable
private fun ResetProgressDialog(onDismiss: () -> Unit, onReset: () -> Unit) {
    // ADR-0052: a reset cannot be undone, so it waits for the word typed out rather than one more tap.
    var typed by remember { mutableStateOf("") }
    val word = stringResource(R.string.details_reset_word)
    FlowFolioDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.details_reset_title),
        body = stringResource(R.string.details_reset_body, word),
        glyph = Glyph.Refresh,
        destructive = true,
        field = DialogField(typed, { typed = it }, word),
        actions = listOf(
            DialogAction(stringResource(R.string.action_cancel)) { onDismiss() },
            DialogAction(
                stringResource(R.string.details_reset),
                ButtonStyle.DANGER,
                enabled = typed.trim().equals(word, ignoreCase = false)
            ) {
                onReset()
                onDismiss()
            }
        )
    )
}

/** The details page's ⋯ (ADR-0052): edit, shelf, favourite, finished, locate, export notes, reset, remove. */
@Suppress("LongMethod") // one entry per action
@Composable
private fun DetailsMenu(state: DetailsUiState, open: Boolean, actions: DetailsActions, onClose: () -> Unit) {
    FlowFolioMenu(expanded = open, onDismissRequest = onClose) {
        val book = state.book
        fun run(action: () -> Unit): () -> Unit = {
            onClose()
            action()
        }
        FlowFolioMenuItem(
            stringResource(R.string.action_edit_details),
            run(actions.onEdit),
            glyph = Glyph.Edit,
            modifier = Modifier.testTag(LibraryTestTags.EDIT_MENU)
        )
        FlowFolioMenuItem(
            stringResource(R.string.action_add_to_shelf),
            run(actions.onAddToShelf),
            glyph = Glyph.Shelves
        )
        FlowFolioMenuItem(
            stringResource(
                if (book?.favorite ==
                    true
                ) {
                    R.string.action_favorite_remove
                } else {
                    R.string.action_favorite_add
                }
            ),
            run(actions.onFavorite),
            glyph = if (book?.favorite == true) Glyph.StarFilled else Glyph.Star
        )
        FlowFolioMenuItem(
            stringResource(
                if (book?.finished ==
                    true
                ) {
                    R.string.action_mark_unfinished
                } else {
                    R.string.action_mark_finished
                }
            ),
            run(actions.onFinished),
            glyph = Glyph.Check
        )
        if (book?.storage is BookStorage.Linked) {
            FlowFolioMenuItem(
                stringResource(R.string.action_locate),
                run(actions.onLocate),
                glyph = Glyph.Folder
            )
        }
        FlowFolioMenuDivider()
        FlowFolioMenuItem(
            stringResource(R.string.details_export_notes),
            run(actions.onExportNotes),
            glyph = Glyph.Share
        )
        FlowFolioMenuItem(
            stringResource(R.string.details_reset),
            run(actions.onResetProgress),
            glyph = Glyph.Refresh
        )
        FlowFolioMenuDivider()
        FlowFolioMenuItem(
            stringResource(R.string.action_remove_from_library),
            run(actions.onDelete),
            glyph = Glyph.Trash,
            destructive = true
        )
    }
}
