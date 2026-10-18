package com.flowfolio.feature.reader

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.color
import com.flowfolio.core.pagecurl.config.PageCurlConfig
import com.flowfolio.core.pagecurl.config.rememberPageCurlConfig
import com.flowfolio.core.pagecurl.page.PageCurl
import com.flowfolio.core.pagecurl.page.PageCurlState
import com.flowfolio.core.pagecurl.page.rememberPageCurlState
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.Spreads
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Original-mode PDF reader: page curl, single page or two-page spread (Phase 1, milestone 3). */
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
    bookmarks: ReaderBookmarksViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val images by viewModel.pageImages.collectAsStateWithLifecycle()
    val navigation by viewModel.navigation.collectAsStateWithLifecycle()
    TrimMemoryEffect(viewModel::onTrimMemory)
    // System Back closes the book the same way as the Back button (open panels catch it first).
    BackHandler(onBack = onBack)

    val tone = (state as? ReaderState.Ready)?.tone ?: PageTone.PRINTED
    val marks by bookmarks.bookmarks.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalTone provides tone, LocalRibbons provides marks.associate { it.page to it.color }) {
        PaperRevealHost(FlowFolio.palette.dark, state, images) {
            StatusBarForPaper(LocalPaperReveal.current?.fromTone ?: tone)
            ReaderRoot(state, images, navigation, viewModel, bookmarks, onBack)
        }
    }
}

@Composable
private fun ReaderRoot(
    state: ReaderState,
    images: Map<Int, ImageBitmap>,
    navigation: BookNavigation,
    viewModel: ReaderViewModel,
    bookmarks: ReaderBookmarksViewModel,
    onBack: () -> Unit
) {
    val dark = FlowFolio.palette.dark
    BoxWithConstraints(Modifier.fillMaxSize().background(PAPER)) {
        val viewport = Viewport(constraints.maxWidth, constraints.maxHeight, maxWidth.value, maxHeight.value, dark)
        LaunchedEffect(viewport) { viewModel.onViewport(viewport) }

        when (val s = state) {
            ReaderState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            is ReaderState.NeedsPassword -> PasswordPrompt(s.wrongPassword, viewModel::submitPassword, onBack)

            is ReaderState.Failed -> Message(
                stringResource(if (s.missingFile) R.string.reader_missing else R.string.reader_damaged),
                onBack
            )

            is ReaderState.Ready -> ReadyReader(s, images, navigation, viewModel, bookmarks, onBack)
        }
    }
}

@Composable
internal fun SinglePageReader(state: ReaderState.Ready, images: Map<Int, ImageBitmap>, onUnitShown: (Int) -> Unit) {
    val curl = rememberReaderCurlState(state.unit)
    ReportUnit(curl, onUnitShown)
    FollowJumps(curl, state.unit)
    PageCurl(
        count = state.unitCount,
        state = curl,
        gradientShadow = true,
        config = swipeAnywhereConfig(),
        modifier = Modifier.fillMaxSize().testTag(ReaderTestTags.PAGE)
    ) { page ->
        // Single page: bound on the left like a book, with a lighter gutter than an open spread.
        PagePane(page, images[page], Alignment.Center, Spine.LEFT, SINGLE_PAGE_GUTTER_STRENGTH)
    }
}

/**
 * Book spread: the left page is static, the right half is the curl. Each curl "page" is a spread; the back of the
 * turning sheet shows the next spread's left page (vendored pagecurl change, ADR-0011). Swipes work on either page:
 * on the right page the sheet follows the finger; on the left page a swipe right turns back and a swipe left turns
 * forward (animated), and a tap on the left page turns back.
 */
@Composable
internal fun SpreadReader(state: ReaderState.Ready, images: Map<Int, ImageBitmap>, onUnitShown: (Int) -> Unit) {
    val curl = rememberReaderCurlState(state.unit)
    val scope = rememberCoroutineScope()
    val latestImages by rememberUpdatedState(images)
    ReportUnit(curl, onUnitShown)
    FollowJumps(curl, state.unit)
    Row(Modifier.fillMaxSize().testTag(ReaderTestTags.PAGE)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(curl) { detectTapGestures { scope.launch { curl.prev() } } }
                .pointerInput(curl) {
                    var dragged = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = 0f },
                        onHorizontalDrag = { _, delta -> dragged += delta },
                        // iOS Books behaviour: direction decides, even a short swipe. Right = back, left = forward.
                        onDragEnd = {
                            when {
                                dragged > SWIPE_THRESHOLD_PX -> scope.launch { curl.prev() }
                                dragged < -SWIPE_THRESHOLD_PX -> scope.launch { curl.next() }
                            }
                        }
                    )
                }
        ) {
            val left = Spreads.leftOf(state.unit, state.pageCount, state.coverAlone)
            PagePane(left, images[left], Alignment.CenterEnd, Spine.RIGHT)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            PageCurl(
                count = state.unitCount,
                state = curl,
                gradientShadow = true,
                config = swipeAnywhereConfig(rightPageOfSpread = true),
                modifier = Modifier.fillMaxSize(),
                backPage = { unit ->
                    latestImages[Spreads.leftOf(unit + 1, state.pageCount, state.coverAlone)]
                }
            ) { unit ->
                val right = Spreads.rightOf(unit, state.pageCount, state.coverAlone)
                PagePane(right, images[right], Alignment.CenterStart, Spine.LEFT)
            }
        }
    }
}

/**
 * Swipe anywhere on the page (iOS Books): dragging left turns forward, dragging right turns back, the page follows the
 * finger, and releasing even slightly past the start completes the turn. A single page taps back on its left half and
 * forward on its right half; the right page of a spread taps forward anywhere (its left page taps back).
 */
@Composable
private fun swipeAnywhereConfig(rightPageOfSpread: Boolean = false) = rememberPageCurlConfig(
    backPageColor = PAPER,
    dragInteraction = PageCurlConfig.GestureDragInteraction(),
    tapBackwardEnabled = !rightPageOfSpread,
    tapInteraction = if (rightPageOfSpread) {
        PageCurlConfig.TargetTapInteraction(forward = PageCurlConfig.TargetTapInteraction.Config(Rect(0f, 0f, 1f, 1f)))
    } else {
        PageCurlConfig.TargetTapInteraction()
    }
)

/**
 * The curl state for this layout, created once from the unit the reader opened at. pagecurl keys its state on
 * initialCurrent, so passing the live unit would replace the state on every turn and strand its tap handler on a
 * stale copy (taps after the first stopped turning). The layout key above recreates it on fold/unfold.
 */
@Composable
private fun rememberReaderCurlState(unit: Int): PageCurlState {
    val openedAt = remember { unit }
    return rememberPageCurlState(initialCurrent = openedAt)
}

/** Contents, Go to page and the scrubber move the ViewModel's unit; the curl snaps to it without animating. */
@Composable
private fun FollowJumps(curl: PageCurlState, unit: Int) {
    LaunchedEffect(unit) { if (curl.current != unit) curl.snapTo(unit) }
}

@Composable
private fun ReportUnit(curl: PageCurlState, onUnitShown: (Int) -> Unit) {
    val callback by rememberUpdatedState(onUnitShown)
    LaunchedEffect(curl) { snapshotFlow { curl.current }.collect { callback(it) } }
}

/**
 * One page image at its exact rendered size (no scaling), on paper, with its ribbon if the page is bookmarked.
 * Missing images show blank paper.
 */
@Composable
internal fun PagePane(
    page: Int,
    image: ImageBitmap?,
    alignment: Alignment,
    spine: Spine,
    shadingStrength: Float = 1f,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val ribbon = LocalRibbons.current[page]
    Box(modifier.background(PAPER).pageShading(spine, shadingStrength, isDarkPaper), contentAlignment = alignment) {
        if (image != null) {
            Box {
                Image(image, contentDescription = null, contentScale = ContentScale.None)
                ribbon?.let {
                    Ribbon(
                        it.color,
                        Modifier.align(Alignment.TopEnd).offset(y = -RIBBON_RISE).padding(end = RIBBON_INSET)
                    )
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(text, color = Color.Black, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onBack) { Text(stringResource(R.string.reader_back)) }
    }
}

internal const val SWIPE_THRESHOLD_PX = 24f
private val RIBBON_INSET = 26.dp
private val RIBBON_RISE = 6.dp
private const val SINGLE_PAGE_GUTTER_STRENGTH = 0.6f
internal val EDGE_GUARD_WIDTH = 32.dp
internal val EDGE_GUARD_HEIGHT = 200.dp
