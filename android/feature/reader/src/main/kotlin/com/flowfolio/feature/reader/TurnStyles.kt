package com.flowfolio.feature.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * Slide: the pages (or spreads) lie in a stack. Turning forward lifts the top page off to the left, uncovering the next
 * one under it; turning back lays the previous page back on from the left. The page follows the finger and the
 * direction of the swipe decides; taps on the outer thirds turn too. The units are the curl's, so switching styles
 * keeps the place.
 */
@Composable
internal fun SlideReader(
    state: ReaderState.Ready,
    images: Map<Int, ImageBitmap>,
    narrating: Boolean = false,
    onUnitShown: (Int) -> Unit
) {
    val stack = remember { PageStack(state.unit) }
    val scope = rememberCoroutineScope()
    val report by rememberUpdatedState(onUnitShown)
    val isNarrating by rememberUpdatedState(narrating)
    val last = state.unitCount - 1
    val sounds = LocalReaderSounds.current
    LaunchedEffect(state.unit) { stack.follow(state.unit, last, isNarrating, sounds) }
    val turn = { direction: Int ->
        // The flip sounds as the page starts to slide, when there is a page to slide to (ADR-0025).
        val turning = (direction > 0 && stack.unit < last) || (direction < 0 && stack.unit > 0)
        if (turning) sounds?.turn(forward = direction > 0)
        scope.launch { stack.settle(direction, last)?.let(report) }
        Unit
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .testTag(ReaderTestTags.PAGE)
            .sideTaps(scope, back = { turn(-1) }, forward = { turn(1) })
            .stackDrag(stack, last, scope, turn)
    ) {
        val width = constraints.maxWidth.toFloat()
        val p = stack.progress.value
        // Forward (p < 0): the current unit slides off the next. Back (p > 0): the previous slides onto the current.
        val under = when {
            p < 0f -> stack.unit + 1
            p > 0f -> stack.unit
            else -> null
        }
        val top = if (p > 0f) stack.unit - 1 else stack.unit
        val shift = if (p > 0f) p - 1f else p
        under?.let { StackUnit(state, images, it, Modifier.underneath(-shift)) }
        StackUnit(state, images, top, Modifier.lifted(shift, width))
    }
}

/** The unit under the top page: a little smaller and shaded, coming up to full size as it is [uncovered] (0..1). */
private fun Modifier.underneath(uncovered: Float): Modifier = graphicsLayer {
    val scale = UNDER_SCALE + (1f - UNDER_SCALE) * uncovered
    scaleX = scale
    scaleY = scale
}.drawWithContent {
    drawContent()
    drawRect(Color.Black.copy(alpha = UNDER_DIM * (1f - uncovered)))
}

/** The top page moved by [shift] widths (negative: to the left), casting a shadow past its right edge. */
private fun Modifier.lifted(shift: Float, width: Float): Modifier = graphicsLayer {
    translationX = shift * width
}.drawWithContent {
    drawContent()
    // A gradient, not a blur, so nothing is rasterized per frame (ADR-0013).
    if (shift < 0f) {
        val reach = STACK_SHADOW.toPx()
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Black.copy(alpha = STACK_SHADOW_ALPHA), Color.Transparent),
                startX = size.width,
                endX = size.width + reach
            ),
            topLeft = Offset(size.width, 0f),
            size = Size(reach, size.height)
        )
    }
}

/** The stack's top [unit] and how far a turn has gone: -1..0 lifting it off forward, 0..1 laying the previous back. */
@Stable
private class PageStack(start: Int) {
    var unit by mutableIntStateOf(start)
    val progress = Animatable(0f)

    suspend fun jumpTo(target: Int) {
        if (target == unit) return
        progress.snapTo(0f)
        unit = target
    }

    suspend fun follow(target: Int, last: Int, narrating: Boolean, sounds: ReaderSounds?) {
        if (target == unit) return
        if (narrating && target == unit + 1) {
            sounds?.turn(forward = true)
            settle(1, last)
        } else {
            if (narrating && target > unit) sounds?.turn(forward = true)
            jumpTo(target)
        }
    }

    /** Finishes a turn in [direction] (1 forward, -1 back, 0 put back); returns the unit now on top if it changed. */
    suspend fun settle(direction: Int, last: Int): Int? {
        val allowed = when (direction) {
            1 -> unit < last
            -1 -> unit > 0
            else -> false
        }
        val target = if (!allowed) {
            0f
        } else if (direction > 0) {
            -1f
        } else {
            1f
        }
        progress.animateTo(target, tween(STACK_MS, easing = Motion.EaseOut))
        if (!allowed) return null
        unit += direction
        progress.snapTo(0f)
        return unit
    }
}

/** Drags move the top page with the finger; on release the direction it was last moving decides (iOS Books). */
private fun Modifier.stackDrag(stack: PageStack, last: Int, scope: CoroutineScope, onRelease: (Int) -> Unit): Modifier =
    pointerInput(stack, last) {
        var lastDelta = 0f
        detectHorizontalDragGestures(
            onDragStart = { lastDelta = 0f },
            onHorizontalDrag = { change, delta ->
                change.consume()
                lastDelta = delta
                val min = if (stack.unit < last) -1f else 0f
                val max = if (stack.unit > 0) 1f else 0f
                val next = (stack.progress.value + delta / size.width).coerceIn(min, max)
                scope.launch { stack.progress.snapTo(next) }
            },
            onDragEnd = {
                val p = stack.progress.value
                val direction = when {
                    p < 0f && lastDelta <= 0f -> 1
                    p > 0f && lastDelta >= 0f -> -1
                    else -> 0
                }
                onRelease(direction)
            },
            onDragCancel = { onRelease(0) }
        )
    }

/** One unit of the stack: a spread's two pages side by side, or a single page. */
@Composable
private fun StackUnit(state: ReaderState.Ready, images: Map<Int, ImageBitmap>, unit: Int, modifier: Modifier) {
    if (unit !in 0 until state.unitCount) return
    if (state.layout == ReaderLayout.SPREAD) {
        Row(modifier.fillMaxSize()) {
            val left = Spreads.leftOf(unit, state.pageCount, state.coverAlone)
            val right = Spreads.rightOf(unit, state.pageCount, state.coverAlone)
            val half = Modifier.weight(1f).fillMaxHeight()
            PagePane(left, images[left], Alignment.CenterEnd, Spine.RIGHT, modifier = half)
            PagePane(right, images[right], Alignment.CenterStart, Spine.LEFT, modifier = half)
        }
    } else {
        PagePane(unit, images[unit], Alignment.Center, Spine.LEFT, SINGLE_GUTTER, modifier.fillMaxSize())
    }
}

/**
 * Scroll: the whole book as one continuous column of pages, each as wide as the screen. The page at the top counts
 * as the page being read; tapping the outer thirds moves a page up or down. An EPUB's pages show only their text
 * ([ScrollBands]), so the column reads as one continuous text, as on the web.
 */
@Composable
internal fun ScrollReader(
    state: ReaderState.Ready,
    images: Map<Int, ImageBitmap>,
    placeholderAspect: Float,
    narrating: Boolean = false,
    onUnitShown: (Int) -> Unit
) {
    val opened = remember { state.unit }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = opened)
    val scope = rememberCoroutineScope()
    val report by rememberUpdatedState(onUnitShown)
    val isNarrating by rememberUpdatedState(narrating)
    val sounds = LocalReaderSounds.current
    LaunchedEffect(list) { snapshotFlow { list.firstVisibleItemIndex }.collect { report(it) } }
    LaunchedEffect(state.unit) {
        if (list.firstVisibleItemIndex != state.unit) {
            if (isNarrating && state.unit > list.firstVisibleItemIndex) {
                sounds?.turn(forward = true)
                list.animateScrollToItem(state.unit)
            } else {
                list.scrollToItem(state.unit)
            }
        }
    }
    val bands = state.scrollBands
    val edge = with(LocalDensity.current) { (bands?.top ?: 0).toDp() }
    LazyColumn(
        state = list,
        contentPadding = PaddingValues(top = edge, bottom = edge),
        modifier = Modifier
            .fillMaxSize()
            .testTag(ReaderTestTags.PAGE)
            .sideTaps(scope, back = { list.animateScrollToItem((list.firstVisibleItemIndex - 1).coerceAtLeast(0)) }) {
                list.animateScrollToItem(list.firstVisibleItemIndex + 1)
            }
    ) {
        items(state.pageCount, key = { it }) { page ->
            val image = images[page]
            if (bands != null) {
                ScrollBand(page, image, bands)
                return@items
            }
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
internal fun Modifier.edgeBounce(bounce: EdgeBounce, atStart: Boolean, atEnd: Boolean, reversed: Boolean): Modifier =
    composed {
        val scope = rememberCoroutineScope()
        val start by rememberUpdatedState(atStart)
        val end by rememberUpdatedState(atEnd)
        val rev by rememberUpdatedState(reversed)
        val distance = with(LocalDensity.current) { BOUNCE_DISTANCE.toPx() }
        graphicsLayer { translationX = bounce.offset.value }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var dx = 0f
                    var lifted = false
                    while (!lifted) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            lifted = true
                            val up = change?.position ?: down.position
                            dx = up.x - down.position.x
                            val direction = turnDirection(dx, up.x, size.width.toFloat(), viewConfiguration.touchSlop)
                            // Right to left, a swipe right goes forward; the nudge still follows the finger.
                            val logical = if (rev) -direction else direction
                            val blocked = if (logical < 0) start else logical > 0 && end
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
private const val STACK_MS = 380
private const val UNDER_SCALE = 0.96f
private const val UNDER_DIM = 0.22f
private const val STACK_SHADOW_ALPHA = 0.28f
private val STACK_SHADOW = 28.dp
private const val BOUNCE_MS = 320
private const val BOUNCE_PEAK_MS = 110
private val BOUNCE_DISTANCE = 10.dp

/** Wraps the readers for a [ReaderState.Ready] so ReadyReader can pick by turn style. */
@Composable
internal fun BookPages(
    state: ReaderState.Ready,
    images: Map<Int, ImageBitmap>,
    placeholderAspect: Float,
    narrating: Boolean = false,
    onUnitShown: (Int) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        when {
            state.turn == com.flowfolio.core.model.PageTurn.SCROLL ->
                ScrollReader(state, images, placeholderAspect, narrating, onUnitShown)

            state.turn == com.flowfolio.core.model.PageTurn.SLIDE ->
                SlideReader(state, images, narrating, onUnitShown)

            state.layout == ReaderLayout.SPREAD ->
                SpreadReader(state, images, narrating, onUnitShown)

            else -> SinglePageReader(state, images, narrating, onUnitShown)
        }
    }
}
