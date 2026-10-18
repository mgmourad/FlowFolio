package com.flowfolio.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.Book3D
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioTabs
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.coverKey
import com.flowfolio.core.designsystem.sharedCover
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookStorage
import kotlinx.coroutines.launch

/**
 * A book's details page (the mock's `#s-details`). [origin] is the library row the book was opened from, so its cover
 * flies in from there. [onRead] opens the reader; it runs after the book-opening hinge.
 */
@Composable
fun DetailsScreen(
    origin: String,
    onBack: () -> Unit,
    onRead: (Book) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel(),
    shelvesVm: ShelvesViewModel = hiltViewModel(),
    libraryVm: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val book = state.book
    val relink = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.relink(uri.toString())
    }
    val locate = { relink.launch(arrayOf("application/pdf")) }
    LaunchedEffect(state.loading, book) { if (!state.loading && book == null) onBack() }
    if (book == null) return
    val hinge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val open: (Int?) -> Unit = { page ->
        scope.launch {
            viewModel.markOpened()
            hinge.animateTo(1f, tween(HINGE_MS, easing = Motion.EaseInOut))
            if (page != null) viewModel.startAt(page) { onRead(book) } else onRead(book)
        }
    }
    val actions = DetailsActions(
        onBack = onBack,
        onRead = { if (state.availability == Availability.MISSING) locate() else open(null) },
        onChapter = { open(it) },
        onShelves = { picking = true },
        onLocate = locate,
        onDelete = {
            libraryVm.onRequestDelete(book)
        }
    )
    DetailsContent(state, book, origin, hinge.value, actions)
    LibraryDialogs(libraryVm.uiState.collectAsStateWithLifecycle().value.dialog, libraryVm)
    if (picking) ShelveBookFlow(book, state.shelves, shelvesVm) { picking = false }
}

internal data class DetailsActions(
    val onBack: () -> Unit = {},
    val onRead: () -> Unit = {},
    val onChapter: (Int) -> Unit = {},
    val onShelves: () -> Unit = {},
    val onLocate: () -> Unit = {},
    val onDelete: () -> Unit = {}
)

/** Folded: hero, then title, actions and tabs under it. Unfolded (600 dp and wider): cover pane and tabs pane. */
@Composable
internal fun DetailsContent(state: DetailsUiState, book: Book, origin: String, hinge: Float, actions: DetailsActions) {
    val p = FlowFolio.palette
    var tab by rememberSaveable { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize().testTag(LibraryTestTags.DETAILS)) {
        if (maxWidth >= TWO_PANE_WIDTH) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(LEFT_PANE).verticalScroll(rememberScrollState())) {
                    DetailsHero(book, origin, hinge, fadeRight = true)
                    DetailsHeading(state, book, actions, Modifier.offset(y = -HERO_OVERLAP))
                }
                Column(
                    Modifier
                        .weight(1f - LEFT_PANE)
                        .verticalScroll(rememberScrollState())
                        .padding(WindowInsets.safeDrawing.asPaddingValues())
                        .padding(start = 22.dp, end = 22.dp, top = PANE_TOP, bottom = 24.dp)
                ) {
                    DetailsTabs(state, book, tab, { tab = it }, actions)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                DetailsHero(book, origin, hinge)
                Column(Modifier.offset(y = -HERO_OVERLAP).padding(horizontal = 22.dp)) {
                    DetailsHeading(state, book, actions)
                    DetailsTabs(state, book, tab, { tab = it }, actions)
                    androidx.compose.foundation.layout.Spacer(
                        Modifier.height(WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 24.dp)
                    )
                }
            }
        }
        TopButtons(state, actions)
        // The page colour rising over the screen as the cover swings open (the mock's book-opening hinge).
        if (hinge > 0f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = hinge }.background(p.paper))
    }
}

/** `.det-hero`: the cover blurred behind, fading into the background, and the 3D book swaying in front. */
@Composable
private fun DetailsHero(book: Book, origin: String, hinge: Float, fadeRight: Boolean = false) {
    val backdrop by rememberBackdrop(book)
    val cover by rememberCover(book)
    val sway = rememberInfiniteTransition(label = "sway")
    val t by sway.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(SWAY_MS, easing = Motion.EaseInOut), RepeatMode.Reverse),
        label = "swayT"
    )
    Box(Modifier.fillMaxWidth().height(HERO_HEIGHT)) {
        // `.det-bg` + `.det-fade`: the blurred cover, faded out toward the bottom with a mask (rather than painted
        // over with a flat colour), so it melts into the gradient app background without a seam. A static layer.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = FADE_TOP),
                            FADE_MID_STOP to Color.Black.copy(alpha = FADE_MID),
                            FADE_END_STOP to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                    // Two panes: also fade out toward the tabs pane, so the backdrop has no straight edge there.
                    if (fadeRight) {
                        drawRect(
                            Brush.horizontalGradient(FADE_SIDE_STOP to Color.Black, 1f to Color.Transparent),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
        ) {
            backdrop?.let {
                Image(
                    it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(BACKDROP_SATURATION) }),
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = BACKDROP_ALPHA }
                )
            }
        }
        // `@keyframes sway`: rotateY 22 -> 8 degrees while rising 8 dp, 6 s each way. The book redraws its angle;
        // the rise is a layer move, so its blurred contact shadow is not re-rasterized every frame.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = COVER_TOP)
                .graphicsLayer {
                    translationY = -SWAY_RISE.toPx() * t
                    // The hinge: the cover swings open around its spine and grows toward the reader.
                    if (hinge > 0f) {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationY = -HINGE_ANGLE * hinge
                        cameraDistance = HINGE_CAMERA * density
                        scaleX = 1f + hinge * HINGE_GROW
                        scaleY = 1f + hinge * HINGE_GROW
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                }
        ) {
            Book3D(
                book.face(cover),
                COVER_WIDTH,
                COVER_HEIGHT,
                modifier = Modifier.sharedCover(coverKey(book.id, origin)),
                restAngle = SWAY_FROM + (SWAY_TO - SWAY_FROM) * t
            )
        }
    }
}

/** Title, author, pills and the actions row (`.det-body` up to `.actions`). */
@Composable
private fun DetailsHeading(state: DetailsUiState, book: Book, actions: DetailsActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(book.title, style = FlowFolioType.bookTitle.merge(FlowFolio.title), textAlign = TextAlign.Center)
        book.author?.let {
            Text(
                it,
                style = FlowFolioType.rowLabel.merge(FlowFolio.item),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        DetailsPills(state, book, Modifier.padding(top = 12.dp))
        Row(
            Modifier.padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val read = when {
                state.availability == Availability.MISSING -> R.string.action_locate
                book.lastPage != null -> R.string.action_continue
                else -> R.string.action_read
            }
            FlowFolioPrimaryButton(
                text = stringResource(read),
                onClick = actions.onRead,
                modifier = Modifier.testTag(LibraryTestTags.READ),
                icon = { FlowFolioGlyph(Glyph.Play, contentDescription = null, size = 14.dp, glow = false) }
            )
            FlowFolioIconButton(
                onClick = actions.onShelves,
                modifier = Modifier.testTag(LibraryTestTags.ADD_TO_SHELF)
            ) {
                FlowFolioGlyph(
                    Glyph.Bookmark,
                    contentDescription = stringResource(R.string.action_add_to_shelf),
                    color = FlowFolio.palette.subItem
                )
            }
        }
    }
}

/** Back and More, on glass, over the hero (`.det-top`). */
@Composable
private fun TopButtons(state: DetailsUiState, actions: DetailsActions) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        FlowFolioIconButton(onClick = actions.onBack, glass = true) {
            FlowFolioGlyph(
                Glyph.Back,
                contentDescription = stringResource(R.string.action_back),
                color = FlowFolio.palette.text
            )
        }
        Box {
            FlowFolioIconButton(onClick = { menu = true }, glass = true) {
                FlowFolioGlyph(
                    Glyph.More,
                    contentDescription = stringResource(R.string.action_more),
                    color = FlowFolio.palette.text
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (state.book?.storage is BookStorage.Linked) {
                    DropdownMenuItem(text = {
                        Text(stringResource(R.string.action_locate), style = FlowFolio.title)
                    }, onClick = {
                        menu = false
                        actions.onLocate()
                    })
                }
                DropdownMenuItem(text = {
                    Text(stringResource(R.string.action_delete), style = FlowFolio.subItem)
                }, onClick = {
                    menu = false
                    actions.onDelete()
                })
            }
        }
    }
}

@Composable
private fun DetailsTabs(state: DetailsUiState, book: Book, tab: Int, onTab: (Int) -> Unit, actions: DetailsActions) {
    val tabs = listOf(
        stringResource(R.string.tab_overview),
        stringResource(R.string.tab_details),
        stringResource(R.string.tab_contents)
    )
    FlowFolioTabs(tabs, tab, onTab, Modifier.padding(top = 22.dp))
    Box(Modifier.padding(top = 20.dp, bottom = 10.dp)) {
        when (tab) {
            0 -> OverviewPanel(state, book)
            1 -> InfoPanel(state, book)
            else -> ContentsPanel(state.contents, book, actions.onChapter)
        }
    }
}

private val TWO_PANE_WIDTH = 600.dp
private const val LEFT_PANE = 0.46f
private val HERO_HEIGHT = 430.dp
private val HERO_OVERLAP = 40.dp
private val COVER_TOP = 104.dp
private val COVER_WIDTH = 156.dp
private val COVER_HEIGHT = 228.dp
private val SWAY_RISE = 8.dp
private const val SWAY_MS = 6000
private const val SWAY_FROM = 22f
private const val SWAY_TO = 8f
private const val BACKDROP_ALPHA = 0.95f
private const val BACKDROP_SATURATION = 1.35f
private const val FADE_TOP = 0.9f
private const val FADE_MID = 0.8f
private const val FADE_MID_STOP = 0.45f
private const val FADE_END_STOP = 0.96f
private const val FADE_SIDE_STOP = 0.55f
private val PANE_TOP = 72.dp
private const val HINGE_MS = 520
private const val HINGE_ANGLE = 110f
private const val HINGE_CAMERA = 12f
private const val HINGE_GROW = 0.6f
