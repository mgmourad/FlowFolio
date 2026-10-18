package com.flowfolio.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import com.flowfolio.feature.reader.cache.TileKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Two-finger gestures on the book: from the first pinch movement the fingers drive [onTransform] (the zoom starts
 * there, no separate step); [onEnd] runs when the last finger lifts. Events are consumed in the initial pass so page
 * turns ignore pinches. One-finger gestures pass through untouched.
 */
internal fun Modifier.pinchGestures(
    onTransform: (centroid: Offset, pan: Offset, zoom: Float, size: IntSize) -> Unit,
    onEnd: () -> Unit
): Modifier = composed {
    val transform by rememberUpdatedState(onTransform)
    val end by rememberUpdatedState(onEnd)
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pinching = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) pinching = true
                // The lift event has no pressed pointers and so no centroid (Unspecified): skip it, never feed NaN.
                val centroid = event.calculateCentroid()
                if (pinching && centroid.isSpecified) {
                    transform(centroid, event.calculatePan(), event.calculateZoom(), size)
                }
                if (pinching) event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
            if (pinching) end()
        }
    }
}

/**
 * The zoomed book: the current pages magnified (blurry at first), then sharp tiles rendered for this zoom once the
 * view settles for [SETTLE_MS]. Pinch and drag move it; a double tap, or pinching back to 1x, leaves zoom ([onExit]).
 */
@Composable
internal fun ZoomView(
    zoom: ZoomState,
    frames: (IntSize) -> List<PageFrames.Frame>,
    images: Map<Int, ImageBitmap>,
    tiles: Map<TileKey, ImageBitmap>,
    onTilesWanted: (List<TileKey>) -> Unit,
    annotating: Annotating,
    onExit: () -> Unit
) {
    val highlights = annotating.byPage()
    val dark = isDarkPaper
    var screen by remember { mutableStateOf(IntSize.Zero) }
    val wanted by rememberUpdatedState(onTilesWanted)
    DisposableEffect(zoom) { onDispose { wanted(emptyList()) } } // tiles live only while zoomed
    LaunchedEffect(zoom) {
        snapshotFlow { Triple(zoom.scale, zoom.offset, screen) }.collectLatest { (scale, offset, size) ->
            delay(SETTLE_MS)
            if (size != IntSize.Zero) onTilesWanted(TilePlan.visible(frames(size), scale, offset, size))
        }
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { screen = it }
            .background(PAPER)
            .testTag(ReaderTestTags.ZOOM)
            // Selecting works zoomed in too: press and hold, as on the page.
            .annotate(annotating, enabled = true)
            .pinchGestures(
                onTransform = { c, pan, z, size -> zoom.transform(c, pan, z, size) },
                onEnd = { if (zoom.isAtRest) onExit() }
            )
            .pointerInput(zoom) { detectPan(zoom, onEnd = { if (zoom.isAtRest) onExit() }) }
            .pointerInput(zoom) {
                detectTapGestures(onDoubleTap = { onExit() }, onTap = { annotating.tap(it, size) })
            }
    ) {
        val placed = frames(IntSize(size.width.toInt(), size.height.toInt()))
        val current = TilePlan.levelFor(zoom.scale)
        translate(zoom.offset.x, zoom.offset.y) {
            scale(zoom.scale, zoom.scale, pivot = Offset.Zero) {
                for (f in placed) {
                    images[f.page]?.let { drawImage(it, topLeft = Offset(f.x.toFloat(), f.y.toFloat())) }
                }
                // Tiles of other levels first (still correct, just softer), the current level on top.
                for ((key, tile) in tiles.entries.sortedBy { it.key.level == current }) {
                    val f = placed.firstOrNull { it.page == key.page } ?: continue
                    val step = TileKey.TILE_PX / key.level
                    translate(f.x + key.tx * step, f.y + key.ty * step) {
                        scale(1 / key.level, 1 / key.level, pivot = Offset.Zero) { drawImage(tile) }
                    }
                }
                for (f in placed) {
                    val marks = highlights[f.page] ?: continue
                    translate(f.x.toFloat(), f.y.toFloat()) {
                        drawHighlights(marks, dark, Size(f.w.toFloat(), f.h.toFloat()))
                    }
                }
            }
        }
    }
}

/** One-finger drags pan the zoomed view. */
private suspend fun PointerInputScope.detectPan(zoom: ZoomState, onEnd: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val change = event.changes.singleOrNull { it.pressed }
            if (change != null && !change.isConsumed) {
                zoom.transform(change.position, event.calculatePan(), 1f, size)
                if (event.calculatePan() != Offset.Zero) change.consume()
            }
        } while (event.changes.any { it.pressed })
        onEnd()
    }
}

private const val SETTLE_MS = 120L
