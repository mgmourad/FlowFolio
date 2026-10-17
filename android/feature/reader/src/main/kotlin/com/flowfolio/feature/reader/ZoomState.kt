package com.flowfolio.feature.reader

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.unit.IntSize
import com.flowfolio.feature.reader.cache.TileKey
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Pinch-zoom over the open book. Content (the pages as laid out unzoomed) is drawn at screen = [scale] * content +
 * [offset]. The content always covers the screen, so panning stops at the page edges.
 */
@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    /** One step of a pinch or pan: zoom by [zoom] around [centroid], then move by [pan]. */
    fun transform(centroid: Offset, pan: Offset, zoom: Float, screen: IntSize) {
        val usable = centroid.isValid() && pan.isValid() && zoom > 0f // NaN > 0 is false too
        if (!usable) return
        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val raw = centroid - (centroid - offset) * (newScale / scale) + pan
        scale = newScale
        offset = clamp(raw, screen)
    }

    /** Double-tap: zoom to [DOUBLE_TAP_SCALE] around [point]. */
    fun zoomTo(point: Offset, target: Float, screen: IntSize) = transform(point, Offset.Zero, target / scale, screen)

    private fun clamp(raw: Offset, screen: IntSize) = Offset(
        raw.x.coerceIn(screen.width * (1 - scale), 0f),
        raw.y.coerceIn(screen.height * (1 - scale), 0f)
    )

    /** Back at (almost) 1x: the reader leaves zoom and page turns work again. */
    val isAtRest: Boolean get() = scale < REST_SCALE

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 6f
        const val DOUBLE_TAP_SCALE = 2.5f
        const val REST_SCALE = 1.05f
    }
}

private fun Offset.isValid() = isSpecified && !x.isNaN() && !y.isNaN()

/** Which zoom tiles to render for the current view: only for pages on screen, nearest the centre first. */
internal object TilePlan {
    /** Tiles are rendered at one of a few levels (zoom factors) so a small pinch reuses the tiles it already has. */
    private val LEVELS = floatArrayOf(1.5f, 2f, 3f, 4.5f, 6f)

    /** Below this zoom the page image itself is sharp enough (it is rendered at screen resolution). */
    private const val TILES_FROM_SCALE = 1.15f

    fun levelFor(scale: Float): Float? =
        if (scale < TILES_FROM_SCALE) null else LEVELS.firstOrNull { it >= scale - LEVEL_TOLERANCE } ?: LEVELS.last()

    fun visible(frames: List<PageFrames.Frame>, scale: Float, offset: Offset, screen: IntSize): List<TileKey> {
        val level = levelFor(scale) ?: return emptyList()
        // The screen in content coordinates.
        val left = -offset.x / scale
        val top = -offset.y / scale
        val view = Rect(left, top, left + screen.width / scale, top + screen.height / scale)
        return frames.flatMap { tilesOf(it, level, view) }.sortedBy { (_, centre) ->
            hypot(centre.x - view.center.x, centre.y - view.center.y)
        }.map { it.first }
    }

    /** The tiles of one page that intersect [view], each with its centre in content coordinates. */
    private fun tilesOf(f: PageFrames.Frame, level: Float, view: Rect): List<Pair<TileKey, Offset>> {
        val pageW = (f.w * level).roundToInt()
        val pageH = (f.h * level).roundToInt()
        val step = TileKey.TILE_PX / level // a tile's size in content pixels
        val xs = floor((view.left - f.x).coerceAtLeast(0f) / step).toInt() until
            ceil((view.right - f.x).coerceAtMost(f.w.toFloat()) / step).toInt()
        val ys = floor((view.top - f.y).coerceAtLeast(0f) / step).toInt() until
            ceil((view.bottom - f.y).coerceAtMost(f.h.toFloat()) / step).toInt()
        return ys.flatMap { ty -> xs.map { tx -> TileKey(f.page, level, pageW, pageH, tx, ty) } }
            .filter { it.widthPx > 0 && it.heightPx > 0 }
            .map { it to Offset(f.x + (it.tx + HALF) * step, f.y + (it.ty + HALF) * step) }
    }
    private const val HALF = 0.5f
    private const val LEVEL_TOLERANCE = 0.05f
}
