package com.flowfolio.feature.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.Spreads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Slide: pages (or spreads) slide sideways like a carousel. Swipe either way; tap the outer thirds to go back or
 * forward. The units are the same as the curl's, so switching styles keeps the place.
 */
@Composable
internal fun SlideReader(state: ReaderState.Ready, images: Map<Int, ImageBitmap>, onUnitShown: (Int) -> Unit) {
    val opened = remember { state.unit }
    val pager = rememberPagerState(initialPage = opened) { state.unitCount }
    val scope = rememberCoroutineScope()
    val report by rememberUpdatedState(onUnitShown)
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { report(it) } }
    LaunchedEffect(state.unit) { if (pager.settledPage != state.unit) pager.scrollToPage(state.unit) }
    HorizontalPager(
        state = pager,
        beyondViewportPageCount = 0,
        modifier = Modifier
            .fillMaxSize()
            .testTag(ReaderTestTags.PAGE)
            .sideTaps(scope, back = { pager.animateScrollToPage(pager.currentPage - 1) }) {
                pager.animateScrollToPage(pager.currentPage + 1)
            }
    ) { unit ->
        if (state.layout == ReaderLayout.SPREAD) {
            Row(Modifier.fillMaxSize()) {
                val left = Spreads.leftOf(unit, state.pageCount, state.coverAlone)
                val right = Spreads.rightOf(unit, state.pageCount, state.coverAlone)
                val half = Modifier.weight(1f).fillMaxHeight()
                PagePane(left, images[left], Alignment.CenterEnd, Spine.RIGHT, modifier = half)
                PagePane(right, images[right], Alignment.CenterStart, Spine.LEFT, modifier = half)
            }
        } else {
            PagePane(unit, images[unit], Alignment.Center, Spine.LEFT, SINGLE_GUTTER)
        }
    }
}

/**
 * Scroll: the whole book as one continuous column of pages, each as wide as the screen. The page at the top counts
 * as the page being read; tapping the outer thirds moves a page up or down.
 */
@Composable
internal fun ScrollReader(
    state: ReaderState.Ready,
    images: Map<Int, ImageBitmap>,
    placeholderAspect: Float,
    onUnitShown: (Int) -> Unit
) {
    val opened = remember { state.unit }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = opened)
    val scope = rememberCoroutineScope()
    val report by rememberUpdatedState(onUnitShown)
    LaunchedEffect(list) { snapshotFlow { list.firstVisibleItemIndex }.collect { report(it) } }
    LaunchedEffect(state.unit) { if (list.firstVisibleItemIndex != state.unit) list.scrollToItem(state.unit) }
    LazyColumn(
        state = list,
        modifier = Modifier
            .fillMaxSize()
            .testTag(ReaderTestTags.PAGE)
            .sideTaps(scope, back = { list.animateScrollToItem((list.firstVisibleItemIndex - 1).coerceAtLeast(0)) }) {
                list.animateScrollToItem(list.firstVisibleItemIndex + 1)
            }
    ) {
        items(state.pageCount, key = { it }) { page ->
            val image = images[page]
            val aspect = image?.let { it.width.toFloat() / it.height } ?: placeholderAspect
            PagePane(
                page,
                image,
                Alignment.TopCenter,
                Spine.LEFT,
                shadingStrength = 0f,
                modifier = Modifier.fillMaxWidth().aspectRatio(aspect)
            )
        }
    }
}

/** Taps on the outer thirds call [back] (left) or [forward] (right); the centre is left to the reader's controls. */
private fun Modifier.sideTaps(scope: CoroutineScope, back: suspend () -> Unit, forward: suspend () -> Unit): Modifier =
    pointerInput(Unit) {
        detectTapGestures { tap ->
            when {
                tap.x < size.width * SIDE_ZONE -> scope.launch { back() }
                tap.x > size.width * (1f - SIDE_ZONE) -> scope.launch { forward() }
            }
        }
    }

/**
 * The mock's edge bounce: at the first or last page, a turn the other way nudges the page 10 dp and springs it back,
 * so the reader feels the end of the book instead of nothing happening.
 */
@Stable
internal class EdgeBounce {
    val offset = Animatable(0f)

    suspend fun bounce(direction: Int, distancePx: Float) {
        offset.animateTo(
            0f,
            keyframes {
                durationMillis = BOUNCE_MS
                -direction * distancePx at BOUNCE_PEAK_MS using Motion.EaseOut
                0f at BOUNCE_MS using Motion.Spring
            }
        )
    }
}

/**
 * Watches (without consuming) horizontal swipes and side taps on the book; when they would go past the first or last
 * unit, plays [bounce]. Applies the bounce offset to the content.
 */
internal fun Modifier.edgeBounce(bounce: EdgeBounce, atStart: Boolean, atEnd: Boolean): Modifier = composed {
    val scope = rememberCoroutineScope()
    val start by rememberUpdatedState(atStart)
    val end by rememberUpdatedState(atEnd)
    val distance = with(LocalDensity.current) { BOUNCE_DISTANCE.toPx() }
    graphicsLayer { translationX = bounce.offset.value }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var dx = 0f
                var lifted = false
                while (!lifted) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) {
                        lifted = true
                        val up = change?.position ?: down.position
                        dx = up.x - down.position.x
                        val direction = turnDirection(dx, up.x, size.width.toFloat(), viewConfiguration.touchSlop)
                        val blocked = if (direction < 0) start else direction > 0 && end
                        if (blocked) scope.launch { bounce.bounce(direction, distance) }
                    }
                }
            }
        }
}

/**
 * Which way a gesture turns: a swipe of [dx] (right = back, left = forward), or for a tap at [x] (a move under
 * [slop]) its side third. -1 back, 1 forward, 0 neither.
 */
internal fun turnDirection(dx: Float, x: Float, width: Float, slop: Float): Int {
    val tapped = kotlin.math.abs(dx) < slop
    return when {
        !tapped -> if (dx > SWIPE_THRESHOLD_PX) {
            -1
        } else if (dx < -SWIPE_THRESHOLD_PX) {
            1
        } else {
            0
        }

        x < width * SIDE_ZONE -> -1

        x > width * (1f - SIDE_ZONE) -> 1

        else -> 0
    }
}

private const val SIDE_ZONE = 0.3f
private const val SINGLE_GUTTER = 0.6f
private const val BOUNCE_MS = 320
private const val BOUNCE_PEAK_MS = 110
private val BOUNCE_DISTANCE = 10.dp

/** Wraps the readers for a [ReaderState.Ready] so ReadyReader can pick by turn style. */
@Composable
internal fun BookPages(
    state: ReaderState.Ready,
    images: Map<Int, ImageBitmap>,
    placeholderAspect: Float,
    onUnitShown: (Int) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        when {
            state.turn == com.flowfolio.core.model.PageTurn.SCROLL ->
                ScrollReader(state, images, placeholderAspect, onUnitShown)

            state.turn == com.flowfolio.core.model.PageTurn.SLIDE -> SlideReader(state, images, onUnitShown)

            state.layout == ReaderLayout.SPREAD -> SpreadReader(state, images, onUnitShown)

            else -> SinglePageReader(state, images, onUnitShown)
        }
    }
}
