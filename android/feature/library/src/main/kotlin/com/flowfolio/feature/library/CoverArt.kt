package com.flowfolio.feature.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.flowfolio.core.designsystem.BookColors
import com.flowfolio.core.designsystem.BookFace
import com.flowfolio.core.designsystem.coverColors
import com.flowfolio.core.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A decoded cover thumbnail and the colours taken from it (spine, ink). */
internal class CoverArt(val image: ImageBitmap, val colors: BookColors)

/** The book's cover thumbnail (page 1, written at import) and its colours, decoded off the main thread; null until
 * ready or when there is none. */
@Composable
internal fun rememberCover(book: Book): State<CoverArt?> = produceState<CoverArt?>(null, book.coverPath) {
    value = book.coverPath?.let { path ->
        withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(path)?.let { CoverArt(it.asImageBitmap(), coverColors(it)) }
        }
    }
}

/** What the 3D book shows for [book]: its rendered first page with a spine in its colour, or a drawn cover. */
internal fun Book.face(cover: CoverArt?) = BookFace(
    title = title,
    pageCount = pageCount,
    cover = cover?.image,
    colors = cover?.colors ?: BookColors.Default
)

/**
 * The mock's blurred backdrop (`.det-bg`, `blur(34px)`), made without a GPU blur: the cover is decoded tiny and box
 * blurred on the CPU once, then drawn stretched with bilinear filtering. A live blur would leave screen-sized
 * textures in the GPU cache (see the memory notes in the changelog).
 */
@Composable
internal fun rememberBackdrop(book: Book): State<ImageBitmap?> = produceState<ImageBitmap?>(null, book.coverPath) {
    value = book.coverPath?.let { path -> withContext(Dispatchers.Default) { tinyBlurred(path) } }
}

/** Decodes [path] at roughly [BACKDROP_WIDTH] px wide or more (power-of-two subsampling), or null. */
private fun decodeSmall(path: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = Integer.highestOneBit((bounds.outWidth / BACKDROP_WIDTH).coerceAtLeast(1))
    }
    return BitmapFactory.decodeFile(path, options)
}

private fun tinyBlurred(path: String): ImageBitmap? {
    val decoded = decodeSmall(path) ?: return null
    val h = (BACKDROP_WIDTH * decoded.height / decoded.width).coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(decoded, BACKDROP_WIDTH, h, true)
    if (small !== decoded) decoded.recycle()
    val px = IntArray(small.width * small.height)
    small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
    repeat(BLUR_PASSES) { boxBlur(px, small.width, small.height) }
    val out = Bitmap.createBitmap(px, small.width, small.height, Bitmap.Config.ARGB_8888)
    small.recycle()
    return out.asImageBitmap()
}

/** One horizontal and one vertical box-blur pass (radius [BLUR_RADIUS]); three passes approximate a Gaussian. */
private fun boxBlur(px: IntArray, w: Int, h: Int) {
    val tmp = IntArray(px.size)
    for (y in 0 until h) blurLine(px, tmp, Line(start = y * w, step = 1, count = w))
    for (x in 0 until w) blurLine(tmp, px, Line(start = x, step = w, count = h))
}

/** A row or column of pixels: [count] pixels from index [start], [step] apart. */
private class Line(val start: Int, val step: Int, val count: Int)

/** Averages each pixel of [line] in [src] with its neighbours along the line (edges clamped) into [dst]. */
private fun blurLine(src: IntArray, dst: IntArray, line: Line) {
    for (i in 0 until line.count) {
        var r = 0
        var g = 0
        var b = 0
        for (d in -BLUR_RADIUS..BLUR_RADIUS) {
            val c = src[line.start + (i + d).coerceIn(0, line.count - 1) * line.step]
            r += c shr RED and BYTE
            g += c shr GREEN and BYTE
            b += c and BYTE
        }
        val n = 2 * BLUR_RADIUS + 1
        dst[line.start + i * line.step] = OPAQUE or (r / n shl RED) or (g / n shl GREEN) or (b / n)
    }
}

private const val BACKDROP_WIDTH = 40
private const val BLUR_RADIUS = 3
private const val BLUR_PASSES = 3
private const val RED = 16
private const val GREEN = 8
private const val BYTE = 0xFF
private const val OPAQUE = 0xFF shl 24
