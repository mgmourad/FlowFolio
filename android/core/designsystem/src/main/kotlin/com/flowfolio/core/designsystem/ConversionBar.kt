package com.flowfolio.core.designsystem

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** How far each converting book's Dynamic Flow is (0 to 1), by book id; books not converting are absent (TM1). */
val LocalConversionProgress = compositionLocalOf<Map<String, Float>> { emptyMap() }

/**
 * A live progress bar on a book's cover while its Dynamic Flow converts (TM1, owner 2026-11-12): a slim pill inside
 * the cover's foot, the accent over a dark track, so it reads on light and dark covers alike. Nothing when the book is
 * not converting.
 */
fun Modifier.conversionBar(bookId: String): Modifier = composed {
    val progress = LocalConversionProgress.current[bookId] ?: return@composed this
    val accent = FlowFolio.palette.accent
    drawWithContent {
        drawContent()
        val h = BAR_HEIGHT.toPx()
        val inset = BAR_INSET.toPx()
        val width = size.width - inset * 2
        if (width <= 0f) return@drawWithContent
        val top = size.height - inset - h
        val radius = CornerRadius(h / 2, h / 2)
        drawRoundRect(Color.Black.copy(alpha = TRACK_ALPHA), Offset(inset, top), Size(width, h), radius)
        val filled = width * progress.coerceIn(MIN_SHOWN, 1f)
        drawRoundRect(accent, Offset(inset, top), Size(filled, h), radius)
    }
}

private val BAR_HEIGHT = 4.dp
private val BAR_INSET = 6.dp
private const val TRACK_ALPHA = 0.45f

/** A sliver of the bar shows from the start, so a book just queued reads as converting. */
private const val MIN_SHOWN = 0.04f
