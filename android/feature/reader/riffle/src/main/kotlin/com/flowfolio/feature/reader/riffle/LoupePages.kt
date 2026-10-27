@file:Suppress("MagicNumber") // the mock's skeleton proportions

package com.flowfolio.feature.reader.riffle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlin.random.Random

/** False when [bmp] was recycled meanwhile (the paper changed and its thumbnails went): nothing is drawn then. */
internal fun DrawScope.drawThumbnail(bmp: ImageBitmap): Boolean {
    if (bmp.isRecycled()) return false
    drawImage(
        bmp,
        srcSize = IntSize(bmp.width, bmp.height),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
    )
    return true
}

/** A thumbnail recycled by its cache (a paper change frees them) must not be drawn: the canvas would throw. */
internal fun ImageBitmap.isRecycled(): Boolean = asAndroidBitmap().isRecycled

/** A stand-in page while the thumbnail loads: a running head, paragraphs of lines, now and then a heading. */
internal fun DrawScope.drawSkeleton(page: Int, ink: Color, accent: Color, isRecto: Boolean) {
    val random = Random(page * 131 + 3)
    val margin = size.width * 0.1f
    val width = size.width - 2 * margin
    val unit = size.height * 0.82f / 27
    var y = size.height * 0.075f
    drawRect(
        ink.copy(alpha = 0.35f),
        Offset(if (isRecto) margin + width * 0.55f else margin, y),
        Size(
            width * 0.45f,
            unit * 0.22f
        )
    )
    y += unit * 1.2f
    while (y < size.height * 0.88f) {
        if (random.nextFloat() < 0.15f) {
            drawRect(
                accent.copy(alpha = 0.9f),
                Offset(margin, y + unit * 0.2f),
                Size(width * (0.4f + random.nextFloat() * 0.3f), unit * 0.55f)
            )
            y += unit * 1.6f
            continue
        }
        val lines = 3 + random.nextInt(5)
        for (i in 0 until lines) {
            val indent = if (i == 0) width * 0.08f else 0f
            val w = if (i == lines - 1) width * (0.3f + random.nextFloat() * 0.5f) else width - indent
            drawRect(ink.copy(alpha = 0.5f), Offset(margin + indent, y + unit * 0.3f), Size(w, unit * 0.32f))
            y += unit
        }
        y += unit * 0.3f
    }
    drawRect(
        ink.copy(alpha = 0.45f),
        Offset(size.width / 2 - size.width * 0.04f, size.height * 0.95f),
        Size(size.width * 0.08f, unit * 0.3f)
    )
}

/** The pages to fetch ahead: the units around [unit], more of them in the direction of travel ([step]). */
internal fun prefetchAround(book: BookGeometry, unit: Int, step: Int): List<Int> {
    val ahead = if (step >= 0) 1 else -1
    val units = listOf(0, ahead, 2 * ahead, 3 * ahead, -ahead, 4 * ahead, 5 * ahead, 6 * ahead)
        .map { unit + it }
        .filter { it in 0 until book.unitCount }
    return units.flatMap { book.pagesOf(it).toList() }.distinct()
}

/**
 * The printed spread a unit's miniature shows (left page, right page; null: nothing there): the cover alone on the
 * right, then even pages on the left and odd ones on the right, mirrored for right-to-left books. Whatever the
 * reader's layout, the loupe shows the book as printed.
 */
internal fun miniSpread(book: BookGeometry, pages: IntRange): Pair<Int?, Int?> {
    val first = pages.first
    val (left, right) = when {
        first == 0 -> null to 0
        first % 2 == 1 -> first to (first + 1).takeIf { it < book.pageCount }
        else -> first - 1 to first
    }
    return if (book.rightToLeft) right to left else left to right
}
