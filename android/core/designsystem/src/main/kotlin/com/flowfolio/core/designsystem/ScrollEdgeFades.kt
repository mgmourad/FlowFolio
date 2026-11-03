package com.flowfolio.core.designsystem

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fades a scrollable row out at whichever end still has something past it (owner, M5 round 3), so a bar of chips
 * that runs off the screen says so instead of looking as though it simply ends there.
 *
 * The fade is done on the content's own alpha through an offscreen layer, not by painting the background colour over
 * it, so it works on a card, on glass or on the page without being told what is behind it. Each end only fades while
 * there is more to scroll to, which keeps the fade honest: no fade means nothing is hidden.
 */
fun Modifier.scrollEdgeFades(state: ScrollState, width: Dp = EDGE_FADE_WIDTH): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = width.toPx().coerceAtMost(size.width / 2f)
        // Read in the draw lambda, so scrolling repaints without recomposing the row.
        if (state.value > 0) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = w),
                blendMode = BlendMode.DstIn
            )
        }
        if (state.value < state.maxValue) {
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Black, Color.Transparent),
                    startX = size.width - w,
                    endX = size.width
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

private val EDGE_FADE_WIDTH = 28.dp
