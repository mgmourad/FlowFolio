// Values here are the mock's CSS copied verbatim (shadow offsets, gradient stops, angles), each cited beside its
// use; naming every one would hide the correspondence with docs/design/FlowFolio_Mock.html (ADR-0015).
@file:Suppress("MagicNumber")

package com.flowfolio.core.designsystem

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * The mock's surfaces, drawn by hand: CSS box-shadow (outer and inset), the 160-degree card gradient, the faint grain
 * and the radial page background. Outer shadows are cached bitmaps (ShadowBitmaps); no minimum Android version.
 */

/** CSS blur radius to a BlurMaskFilter radius (CSS blur = 2 sigma; BlurMaskFilter sigma ~= 0.57735 r + 0.5). */
private fun cssBlurToRadius(blurPx: Float) = max(0.5f, (blurPx / 2f - 0.5f) / 0.57735f)

private fun Density.outlinePath(
    shape: Shape,
    size: Size,
    direction: LayoutDirection,
    offset: Offset = Offset.Zero
): Path {
    val path = Path()
    when (val outline = shape.createOutline(size, direction, this)) {
        is Outline.Rectangle -> path.addRect(outline.rect)
        is Outline.Rounded -> path.addRoundRect(outline.roundRect)
        is Outline.Generic -> path.addPath(outline.path)
    }
    if (offset != Offset.Zero) path.translate(offset)
    return path
}

/**
 * `box-shadow: 0 [offsetY] [blur] [spread] [color]` behind [shape]. The blurred shape comes from [ShadowBitmaps]
 * (rendered once per shape and size, then drawn tinted), so shadows hold no GPU render targets.
 */
fun Modifier.softShadow(shape: Shape, color: Color, blur: Dp, offsetY: Dp = 0.dp, spread: Dp = 0.dp): Modifier =
    drawWithCache {
        val s = spread.toPx()
        val w = (size.width + 2 * s).roundToInt().coerceAtLeast(1)
        val h = (size.height + 2 * s).roundToInt().coerceAtLeast(1)
        val grown = Size(w.toFloat(), h.toFloat())
        val outline = shape.createOutline(grown, layoutDirection, this)
        val key = ShadowBitmaps.Key(outline.shadowKey(), w, h, cssBlurToRadius(blur.toPx()))
        val shadow = if (color.alpha == 0f) {
            null
        } else {
            ShadowBitmaps.get(key) { paint ->
                drawPath(outlinePath(shape, grown, layoutDirection).asAndroidPath(), paint)
            }
        }
        val tint = ColorFilter.tint(color)
        val topLeft = Offset(-s - (shadow?.margin ?: 0), offsetY.toPx() - s - (shadow?.margin ?: 0))
        onDrawBehind { shadow?.let { drawImage(it.image, topLeft, colorFilter = tint) } }
    }

/** `box-shadow: inset 0 [offsetY] [blur] [color]` inside [shape], drawn over the content. */
fun Modifier.innerShadow(shape: Shape, color: Color, blur: Dp, offsetY: Dp = 0.dp): Modifier = drawWithCache {
    val inner = outlinePath(shape, size, layoutDirection)
    val hole = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(androidx.compose.ui.geometry.Rect(-size.width, -size.height, 2 * size.width, 2 * size.height))
        addPath(outlinePath(shape, size, layoutDirection, Offset(0f, offsetY.toPx())))
    }
    val radius = cssBlurToRadius(blur.toPx())
    onDrawWithContent {
        drawContent()
        clipPath(inner) {
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.toArgb()
                    if (radius > 0.5f) maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawPath(hole.asAndroidPath(), paint)
            }
        }
    }
}

/** A CSS `linear-gradient(<deg>, start, end)` across the element. */
fun angledGradient(degrees: Float, start: Color, end: Color, size: Size): Brush {
    val rad = Math.toRadians(degrees.toDouble())
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    // Half the gradient line length per CSS: |w sin a| + |h cos a|, halved.
    val half = (kotlin.math.abs(size.width * dx) + kotlin.math.abs(size.height * dy)) / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    return Brush.linearGradient(listOf(start, end), c - Offset(dx, dy) * half, c + Offset(dx, dy) * half)
}

/** The mock's `--shadow-soft`: `0 22px 44px -16px` plus a 1 px top inner highlight. */
@Composable
fun Modifier.softElevation(shape: Shape): Modifier {
    val p = FlowFolio.palette
    return softShadow(shape, p.shadow, blur = 44.dp, offsetY = 22.dp, spread = (-16).dp)
}

/** The mock's `--card`: 160-degree gradient fill, soft shadow, top inner highlight, clipped to [shape]. */
@Composable
fun Modifier.card(shape: Shape = RoundedCornerShape(22.dp)): Modifier {
    val p = FlowFolio.palette
    return softElevation(shape)
        .drawWithCache {
            val brush = angledGradient(CARD_ANGLE, p.cardStart, p.cardEnd, size)
            val outline = shape.createOutline(size, layoutDirection, this)
            onDrawBehind { drawOutline(outline, brush) }
        }
        .innerShadow(shape, p.highlight, blur = 0.dp, offsetY = 1.dp)
}

/** The mock's `--shadow-press`: a sunken look for tracks, segmented controls and pressed buttons. */
@Composable
fun Modifier.pressedInset(shape: Shape): Modifier = innerShadow(shape, FlowFolio.palette.pressShadow, 6.dp, 2.dp)

/** One 180 px tile of the mock's grain (`feTurbulence`, alpha .07 at 55% opacity), generated once. */
private val grainTile: ImageBitmap by lazy {
    val n = GRAIN_TILE
    val pixels = IntArray(n * n)
    val random = Random(GRAIN_SEED)
    for (i in pixels.indices) {
        // Two octaves of value noise, like numOctaves=2: fine speckle plus a softer layer.
        val v = (random.nextFloat() * 0.6f + random.nextFloat() * 0.4f)
        val a = (v * GRAIN_ALPHA * 255).toInt().coerceIn(0, 255)
        pixels[i] = a shl 24
    }
    android.graphics.Bitmap.createBitmap(pixels, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** The faint paper grain the mock lays over every screen. */
fun Modifier.grain(): Modifier = drawWithCache {
    val brush = ShaderBrush(ImageShader(grainTile, TileMode.Repeated, TileMode.Repeated))
    onDrawWithContent {
        drawContent()
        drawRect(brush)
    }
}

/**
 * The mock's page background: `radial-gradient(1200x700 at 50% 0, surface, bg-deep 70%)`, laid with the theme's
 * texture (ADR-0028): grain, wood grain, or nothing.
 */
fun Modifier.appBackground(palette: Palette): Modifier = drawWithCache {
    val radius = max(size.width, size.height) * BACKGROUND_REACH
    val brush = Brush.radialGradient(
        0f to palette.surface,
        BACKGROUND_STOP to palette.backgroundDeep,
        center = Offset(size.width / 2f, 0f),
        radius = radius
    )
    onDrawBehind { drawRect(brush) }
}.then(
    when (palette.texture) {
        SurfaceTexture.GRAIN -> Modifier.grain()
        SurfaceTexture.WOOD -> Modifier.woodGrain(palette.dark)
        SurfaceTexture.NONE -> Modifier
    }
)

private const val CARD_ANGLE = 160f
private const val GRAIN_TILE = 180
private const val GRAIN_SEED = 1792
private const val GRAIN_ALPHA = 0.07f * 0.55f
private const val BACKGROUND_REACH = 1.1f
private const val BACKGROUND_STOP = 0.7f
