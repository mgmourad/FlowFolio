package com.flowfolio.feature.reader

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.exp

/**
 * Skeuomorphic paper shading (owner request, 2026-10-17): a dispersed shadow into the gutter at the binding, a fine
 * crease at the spine with a faint highlight where the paper bulges, and soft shading on the outer edge for the page
 * stack, with light from the upper left (the right-hand edge is a little darker).
 *
 * Everything is multi-stop linear gradients that follow a Gaussian falloff, so it reads like a soft blur without the
 * offscreen GPU layer a real blur needs (ADR-0013: the blurred curl shadow cost 36-40 MB).
 */
fun Modifier.pageShading(spine: Spine, strength: Float = 1f, darkPaper: Boolean = false): Modifier = drawWithCache {
    val w = size.width
    val gutterWidth = w * GUTTER_FRACTION
    val creaseWidth = w * CREASE_FRACTION
    val edgeWidth = w * EDGE_FRACTION
    val atSpine = if (spine == Spine.LEFT) 0f else w
    val toward = if (spine == Spine.LEFT) 1f else -1f
    val outer = w - atSpine
    // Light from the upper left: the right-hand outer edge sits in a little more shade.
    val outerAlpha = if (spine == Spine.LEFT) EDGE_ALPHA_RIGHT else EDGE_ALPHA_LEFT

    // On dark paper a white highlight reads as a light stripe, and the shadow needs more weight to show at all.
    val gutterAlpha = if (darkPaper) NIGHT_GUTTER_ALPHA else GUTTER_ALPHA
    val gutter = softBand(atSpine, toward, gutterWidth, gutterAlpha * strength, Color.Black)
    val crease = softBand(atSpine, toward, creaseWidth, CREASE_ALPHA * strength, Color.Black)
    val bulgeAlpha = if (darkPaper) 0f else BULGE_ALPHA * strength
    val bulge = bumpBand(atSpine + toward * gutterWidth * BULGE_OFFSET, toward, gutterWidth, bulgeAlpha)
    val edge = softBand(outer, -toward, edgeWidth, outerAlpha * strength, Color.Black)

    onDrawWithContent {
        drawContent()
        drawRect(bulge.brush, topLeft = bulge.topLeft(), size = bulge.size(size))
        drawRect(gutter.brush, topLeft = gutter.topLeft(), size = gutter.size(size))
        drawRect(crease.brush, topLeft = crease.topLeft(), size = crease.size(size))
        drawRect(edge.brush, topLeft = edge.topLeft(), size = edge.size(size))
    }
}

/** A vertical band starting at [x], extending [width] in direction [toward] (+1 right, -1 left), fading out. */
private class Band(val x: Float, val toward: Float, val width: Float, val brush: Brush) {
    fun topLeft() = Offset(if (toward > 0) x else x - width, 0f)

    fun size(size: Size) = Size(width, size.height)
}

// Runs when the shading is (re)built in drawWithCache, not per frame, so the spread copy is negligible.
@Suppress("SpreadOperator")
private fun softBand(x: Float, toward: Float, width: Float, alpha: Float, color: Color): Band {
    val stops = gaussianStops(color, alpha)
    val end = x + toward * width
    return Band(x, toward, width, Brush.horizontalGradient(colorStops = *stops, startX = x, endX = end))
}

// A highlight that rises and falls smoothly (no hard leading edge), like light catching the paper's curve.
@Suppress("SpreadOperator")
private fun bumpBand(x: Float, toward: Float, width: Float, alpha: Float): Band {
    val stops = Array(STOP_COUNT) { i ->
        val t = i / (STOP_COUNT - 1f)
        val d = t * 2f - 1f
        t to Color.White.copy(alpha = alpha * exp(-FALLOFF * d * d).toFloat() * (1f - d * d))
    }
    return Band(x, toward, width, Brush.horizontalGradient(colorStops = *stops, startX = x, endX = x + toward * width))
}

/** Colour stops approximating a Gaussian falloff from [alpha] at 0 to nothing at 1. */
internal fun gaussianStops(color: Color, alpha: Float): Array<Pair<Float, Color>> = Array(STOP_COUNT) { i ->
    val t = i / (STOP_COUNT - 1f)
    t to color.copy(alpha = alpha * exp(-FALLOFF * t * t).toFloat() * (1f - t))
}

private const val STOP_COUNT = 8
private const val FALLOFF = 4.5f
private const val GUTTER_FRACTION = 0.085f
private const val GUTTER_ALPHA = 0.30f
private const val NIGHT_GUTTER_ALPHA = 0.55f
private const val CREASE_FRACTION = 0.012f
private const val CREASE_ALPHA = 0.28f
private const val BULGE_OFFSET = 0.55f
private const val BULGE_ALPHA = 0.10f
private const val EDGE_FRACTION = 0.03f
private const val EDGE_ALPHA_LEFT = 0.07f
private const val EDGE_ALPHA_RIGHT = 0.12f
