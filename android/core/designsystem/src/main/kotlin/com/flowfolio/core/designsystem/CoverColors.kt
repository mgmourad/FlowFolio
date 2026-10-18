package com.flowfolio.core.designsystem

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * A book's colours taken from its cover image: [BookColors.start] is the cover's dominant colour (the spine is painted
 * in it, like a printed book), [BookColors.end] a darker shade of it, and [BookColors.ink] black or cream, whichever
 * reads better on it (the spine title). Off the main thread: it reads the bitmap's pixels.
 */
fun coverColors(cover: Bitmap): BookColors {
    val step = maxOf(1, minOf(cover.width, cover.height) / SAMPLES_PER_SIDE)
    val counts = IntArray(BUCKETS)
    val sums = Array(BUCKETS) { LongArray(CHANNELS) }
    var y = 0
    while (y < cover.height) {
        var x = 0
        while (x < cover.width) {
            val c = cover.getPixel(x, y)
            val r = c shr RED and BYTE
            val g = c shr GREEN and BYTE
            val b = c and BYTE
            // 4 bits per channel: colours a little apart still count as the same colour.
            val bucket = (r shr QUANT shl (2 * BITS)) or (g shr QUANT shl BITS) or (b shr QUANT)
            counts[bucket]++
            sums[bucket][0] += r.toLong()
            sums[bucket][1] += g.toLong()
            sums[bucket][2] += b.toLong()
            x += step
        }
        y += step
    }
    val top = counts.indices.maxBy { counts[it] }
    val n = counts[top].coerceAtLeast(1)
    val dominant = Color(
        red = (sums[top][0] / n).toInt(),
        green = (sums[top][1] / n).toInt(),
        blue = (sums[top][2] / n).toInt()
    )
    val shade = Color(dominant.red * SHADE, dominant.green * SHADE, dominant.blue * SHADE)
    val ink = if (dominant.luminance() > LIGHT_COVER) DARK_INK else LIGHT_INK
    return BookColors(dominant, shade, ink)
}

private const val SAMPLES_PER_SIDE = 64
private const val BITS = 4
private const val QUANT = 8 - BITS
private const val BUCKETS = 1 shl (3 * BITS)
private const val CHANNELS = 3
private const val RED = 16
private const val GREEN = 8
private const val BYTE = 0xFF
private const val SHADE = 0.62f
private const val LIGHT_COVER = 0.45f
private val DARK_INK = Color(0xFF1E1B24)
private val LIGHT_INK = Color(0xFFF7F1E3)
