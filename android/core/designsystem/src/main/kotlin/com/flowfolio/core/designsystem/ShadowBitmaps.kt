package com.flowfolio.core.designsystem

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.ceil

/**
 * Blurred shapes rendered once on the CPU into alpha-only bitmaps and reused. Drawing a BlurMaskFilter on the GPU
 * gives every shadow its own render target, kept in the GPU cache: a library screen of cards, chips and buttons held
 * about 190 MB that way on the unfolded Fold. A cached bitmap costs its pixels once (one byte each) and is drawn tinted
 * in any colour.
 */
internal object ShadowBitmaps {
    /** What a shadow looks like, independent of colour and position: [shape] names the outline (kind, radii). */
    data class Key(val shape: String, val width: Int, val height: Int, val blurRadius: Float)

    /** A rendered shadow and how far it reaches past the shape on each side. */
    class Entry(val image: ImageBitmap, val margin: Int)

    private val cache = object : LruCache<Key, Entry>(MAX_BYTES) {
        override fun sizeOf(key: Key, value: Entry) = value.image.width * value.image.height
    }

    /** The shadow for [key]; if not cached, [draw] paints the shape at the origin ([Key.width] x [Key.height]). */
    fun get(key: Key, draw: Canvas.(Paint) -> Unit): Entry = synchronized(cache) {
        cache[key] ?: render(key, draw).also { cache.put(key, it) }
    }

    private fun render(key: Key, draw: Canvas.(Paint) -> Unit): Entry {
        // BlurMaskFilter's radius r gives sigma ~= 0.57735 r + 0.5; the blur fades out by 3 sigma.
        val margin = ceil(SIGMA_EXTENT * (SIGMA_PER_RADIUS * key.blurRadius + SIGMA_BASE)).toInt()
        val bitmap = Bitmap.createBitmap(key.width + 2 * margin, key.height + 2 * margin, Bitmap.Config.ALPHA_8)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(key.blurRadius, BlurMaskFilter.Blur.NORMAL)
        }
        Canvas(bitmap).apply {
            translate(margin.toFloat(), margin.toFloat())
            draw(paint)
        }
        return Entry(bitmap.asImageBitmap(), margin)
    }

    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val SIGMA_PER_RADIUS = 0.57735f
    private const val SIGMA_BASE = 0.5f
    private const val SIGMA_EXTENT = 3f
}

/** A simple shape [blurredShape] can draw from the cache. */
internal enum class BlurShape { Oval, Rect }

/**
 * Draws [shape] filling [size] at [topLeft] with a CSS-like blur of [blurPx] in [color], from
 * [ShadowBitmaps]: like [blurred], but without a GPU render target per shadow.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.blurredShape(
    color: androidx.compose.ui.graphics.Color,
    blurPx: Float,
    shape: BlurShape,
    topLeft: androidx.compose.ui.geometry.Offset,
    size: androidx.compose.ui.geometry.Size
) {
    val w = kotlin.math.round(size.width).toInt().coerceAtLeast(1)
    val h = kotlin.math.round(size.height).toInt().coerceAtLeast(1)
    val key = ShadowBitmaps.Key(shape.name, w, h, (blurPx / 2f).coerceAtLeast(MIN_SHADOW_BLUR))
    val entry = ShadowBitmaps.get(key) { paint ->
        when (shape) {
            BlurShape.Oval -> drawOval(0f, 0f, w.toFloat(), h.toFloat(), paint)
            BlurShape.Rect -> drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
    }
    drawImage(
        entry.image,
        androidx.compose.ui.geometry.Offset(topLeft.x - entry.margin, topLeft.y - entry.margin),
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color)
    )
}

private const val MIN_SHADOW_BLUR = 0.5f

/** A cache key for an outline's geometry: its kind and corner radii (the size is keyed separately). */
internal fun androidx.compose.ui.graphics.Outline.shadowKey(): String = when (this) {
    is androidx.compose.ui.graphics.Outline.Rectangle -> "rect"

    is androidx.compose.ui.graphics.Outline.Rounded -> with(roundRect) {
        "rrect:$topLeftCornerRadius,$topRightCornerRadius,$bottomRightCornerRadius,$bottomLeftCornerRadius"
    }

    is androidx.compose.ui.graphics.Outline.Generic -> "path:${path.hashCode()}"
}
