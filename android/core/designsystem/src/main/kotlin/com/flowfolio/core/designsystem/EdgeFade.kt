package com.flowfolio.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Soft, blurred fades at the top and bottom of the screen (owner, 2026-10-23), so content scrolling under the status
 * bar, the front camera and the navigation bar melts into the theme instead of running into them. Each fade blurs what
 * is behind the system bar and tints it with the background colour (ADR-0052: the status-bar fade), the tint fading out
 * a little past the bar. [bottom] is off where the screen's bottom edge is clear (the unfolded rail). The blur needs
 * [LocalGlassState]; without it the tint alone shows.
 */
@Composable
fun BoxScope.EdgeFades(alpha: Float = 1f, topStrength: Float = 1f, bottom: Boolean = true) {
    val density = LocalDensity.current
    val status = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navigation = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val p = FlowFolio.palette
    val top = status + TOP_REACH
    val bottomHeight = navigation + BOTTOM_REACH
    EdgeFade(
        Modifier.align(Alignment.TopCenter).alpha(alpha),
        top,
        p.background,
        fromTop = true,
        hold = status / top,
        strength = topStrength
    )
    if (bottom) {
        EdgeFade(
            Modifier.align(Alignment.BottomCenter).alpha(alpha),
            bottomHeight,
            p.background,
            fromTop = false,
            hold = navigation / bottomHeight
        )
    }
}

/**
 * One soft, blurred fade in [color], [height] tall, darkest at the screen's edge ([fromTop] or bottom). The first
 * [hold] of it (a share of the height) stays at full strength, for something drawn over it, such as the reader's top
 * bar (owner, 2026-10-23); the rest fades away.
 */
@Composable
fun EdgeFade(modifier: Modifier, height: Dp, color: Color, fromTop: Boolean, hold: Float = 0f, strength: Float = 1f) {
    val state = LocalGlassState.current
    val edge = color.copy(alpha = EDGE_ALPHA * strength)
    val mid = color.copy(alpha = MID_ALPHA * strength)
    // Full strength for the held share, half strength a little further on, then nothing.
    val midStop = hold + (1f - hold) * MID_STOP
    val brush = if (fromTop) {
        Brush.verticalGradient(0f to edge, hold to edge, midStop to mid, 1f to Color.Transparent)
    } else {
        Brush.verticalGradient(0f to Color.Transparent, 1f - midStop to mid, 1f - hold to edge, 1f to edge)
    }
    // Full blur over the held share (behind the system bar or the reader's buttons), then bands of ever lighter blur
    // over the rest, so the blur eases out instead of ending in a line (owner, 2026-10-23); the tint fades on top.
    Box(modifier.fillMaxWidth().height(height)) {
        if (state != null && hold > 0f) {
            val band = height * (1f - hold) / BANDS
            val radii = (0 until BANDS).map { EDGE_BLUR * ((BANDS - it - HALF) / BANDS) }
            Column(
                Modifier.matchParentSize(),
                verticalArrangement = if (fromTop) Arrangement.Top else Arrangement.Bottom
            ) {
                val blurs = listOf(height * hold to EDGE_BLUR) + radii.map { band to it }
                (if (fromTop) blurs else blurs.reversed()).forEach { (h, radius) ->
                    Box(Modifier.fillMaxWidth().height(h).edgeBlur(state, color, radius))
                }
            }
        }
        Box(Modifier.matchParentSize().drawBehind { drawRect(brush) })
    }
}

/** Blurs what is behind by [radius], over the page or theme [color]. */
private fun Modifier.edgeBlur(state: HazeState, color: Color, radius: Dp): Modifier = hazeBlur(
    HazeInput.Sources(state),
    HazeBlurStyle {
        blurRadius(radius)
        backgroundColor(color)
    }
)

/** Clear of the screens' titles (owner, 2026-10-23). */
private val TOP_REACH = 10.dp
private val BOTTOM_REACH = 24.dp
private val EDGE_BLUR = 18.dp
private const val BANDS = 4
private const val HALF = 0.5f
private const val EDGE_ALPHA = 0.92f
private const val MID_STOP = 0.45f
private const val MID_ALPHA = 0.55f
