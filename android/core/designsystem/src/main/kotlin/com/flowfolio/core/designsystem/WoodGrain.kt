package com.flowfolio.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/**
 * Wood grain (Rustic, ADR-0028): long, gently wavering darker streaks along the width, like planed planks, drawn once
 * into a tile and repeated.
 */
private val woodTile: ImageBitmap by lazy {
    val w = WOOD_TILE_W
    val h = WOOD_TILE_H
    val pixels = IntArray(w * h)
    val random = Random(WOOD_SEED)
    val phase = FloatArray(WOOD_BANDS) { random.nextFloat() * WOOD_TWO_PI }
    for (y in 0 until h) {
        for (x in 0 until w) {
            // The ring position wavers along the board; rings are thin dark lines between broad lighter wood.
            val waver = kotlin.math.sin(x * WOOD_WAVE_X + phase[y * WOOD_BANDS / h]) * WOOD_WAVE_AMP
            val ring = kotlin.math.sin((y + waver) * WOOD_RING_FREQ)
            val line = ((ring + 1f) / 2f).let { it * it * it * it * it * it }
            val fleck = random.nextFloat() * WOOD_FLECK
            val a = ((line * WOOD_ALPHA + fleck) * MAX_ALPHA).toInt().coerceIn(0, MAX_ALPHA)
            pixels[y * w + x] = a shl 24
        }
    }
    android.graphics.Bitmap.createBitmap(pixels, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Wood grain behind the content; fainter on light wood ([dark] false), where dark streaks stand out more. */
fun Modifier.woodGrain(dark: Boolean = true): Modifier = drawWithCache {
    val brush = ShaderBrush(ImageShader(woodTile, TileMode.Repeated, TileMode.Repeated))
    // Behind the content, unlike the mock's grain: streaks across covers and text would read as damage.
    onDrawBehind { drawRect(brush, alpha = if (dark) 1f else LIGHT_WOOD_ALPHA) }
}

private const val WOOD_SEED = 1792
private const val MAX_ALPHA = 255
private const val LIGHT_WOOD_ALPHA = 0.4f
private const val WOOD_TILE_W = 512
private const val WOOD_TILE_H = 256
private const val WOOD_BANDS = 8
private const val WOOD_TWO_PI = 6.2831855f
private const val WOOD_WAVE_X = 0.012f
private const val WOOD_WAVE_AMP = 9f
private const val WOOD_RING_FREQ = 0.21f
private const val WOOD_ALPHA = 0.16f
private const val WOOD_FLECK = 0.02f
