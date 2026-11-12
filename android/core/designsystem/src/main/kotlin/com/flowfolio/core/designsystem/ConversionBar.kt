package com.flowfolio.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** How far each converting book's Dynamic Flow is (0 to 1), by book id; books not converting are absent (TM1). */
val LocalConversionProgress = compositionLocalOf<Map<String, Float>> { emptyMap() }

/**
 * Live progress on a book's cover while its Dynamic Flow converts (TM1; made unmissable 2026-11-12, owner: "a very
 * informative cue"): the foot of the cover darkens, and over it sit "Dynamic Flow · 42%" and a bar in the accent,
 * so it reads on light and dark covers alike. A cover too narrow for words (the list view) keeps the bar alone.
 * Nothing when the book is not converting.
 */
fun Modifier.conversionBar(bookId: String): Modifier = composed {
    val target = LocalConversionProgress.current[bookId] ?: return@composed this
    val progress by animateFloatAsState(target.coerceIn(0f, 1f), tween(Motion.MEDIUM_MS), label = "conversion")
    val accent = FlowFolio.palette.accent
    val measurer = rememberTextMeasurer()
    drawWithContent {
        drawContent()
        val inset = BAR_INSET.toPx()
        val width = size.width - inset * 2
        if (width <= 0f) return@drawWithContent
        val h = BAR_HEIGHT.toPx()
        val labelled = size.width >= MIN_LABEL_WIDTH.toPx()
        // Above the cover's badges (format, ribbon, star) where there is room for words, so none hides the bar.
        val top = size.height - inset - h - if (labelled) BADGE_CLEARANCE.toPx() else 0f
        val band = if (labelled) BAND_HEIGHT.toPx() else h + inset * BARE_BAND_INSETS
        // Rounded like the cover's foot, so the shade never spills past its corners.
        drawRoundRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                SCRIM_RAMP to Color.Black.copy(alpha = SCRIM_ALPHA * SCRIM_RAMP_SHARE),
                1f to Color.Black.copy(alpha = SCRIM_ALPHA),
                startY = size.height - band,
                endY = size.height
            ),
            Offset(0f, size.height - band),
            Size(size.width, band),
            CornerRadius(COVER_CORNER.toPx(), COVER_CORNER.toPx())
        )
        val radius = CornerRadius(h / 2, h / 2)
        drawRoundRect(Color.White.copy(alpha = TRACK_ALPHA), Offset(inset, top), Size(width, h), radius)
        val filled = width * progress.coerceAtLeast(MIN_SHOWN)
        drawRoundRect(accent, Offset(inset, top), Size(filled, h), radius)
        if (labelled) {
            val percent = "${(progress * PERCENT).roundToInt()}%"
            val style = TextStyle(color = Color.White, fontSize = LABEL_SIZE, fontWeight = FontWeight.SemiBold)
            // "Dynamic Flow · 42%" where it fits, else the percentage alone (a shelf's narrow covers).
            val label = measurer.measure("$LABEL · $percent", style, maxLines = 1)
                .takeIf { it.size.width <= width }
                ?: measurer.measure(percent, style, maxLines = 1)
            drawText(label, topLeft = Offset(inset, top - LABEL_GAP.toPx() - label.size.height))
        }
    }
}

private const val LABEL = "Dynamic Flow"
private val COVER_CORNER = 4.dp
private val BAR_HEIGHT = 5.dp
private val BAR_INSET = 7.dp
private val BAND_HEIGHT = 76.dp
private val BADGE_CLEARANCE = 26.dp
private val MIN_LABEL_WIDTH = 64.dp
private val LABEL_GAP = 4.dp
private val LABEL_SIZE = 10.sp
private const val SCRIM_ALPHA = 0.8f

/** The shade is most of the way there a third down the band, where the words sit. */
private const val SCRIM_RAMP = 0.35f
private const val SCRIM_RAMP_SHARE = 0.75f
private const val TRACK_ALPHA = 0.28f
private const val PERCENT = 100

/** Without words the shade is just deep enough to frame the bar. */
private const val BARE_BAND_INSETS = 3

/** A sliver of the bar shows from the start, so a book just queued reads as converting. */
private const val MIN_SHOWN = 0.04f
