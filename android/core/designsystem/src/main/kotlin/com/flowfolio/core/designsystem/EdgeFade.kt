package com.flowfolio.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Soft, blurred fades at the top and bottom of the screen (owner, 2026-10-23), so content scrolling under the status
 * bar, the front camera and the navigation bar melts into the theme instead of running into them. Each fade blurs what
 * is behind the system bar and tints it with the background colour drawn at that edge (the surface at the top, the
 * deep background at the bottom), the tint fading out a little past the bar. The blur needs [LocalGlassState]; without
 * it the tint alone shows.
 */
@Composable
fun BoxScope.EdgeFades(alpha: Float = 1f) {
    val density = LocalDensity.current
    val status = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navigation = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val p = FlowFolio.palette
    val top = status + TOP_REACH
    val bottom = navigation + BOTTOM_REACH
    EdgeFade(Modifier.align(Alignment.TopCenter).alpha(alpha), top, p.surface, fromTop = true, hold = status / top)
    EdgeFade(
        Modifier.align(Alignment.BottomCenter).alpha(alpha),
        bottom,
        p.backgroundDeep,
        fromTop = false,
        hold = navigation / bottom
    )
}

/**
 * One soft, blurred fade in [color], [height] tall, darkest at the screen's edge ([fromTop] or bottom). The first
 * [hold] of it (a share of the height) stays at full strength, for something drawn over it, such as the reader's top
 * bar (owner, 2026-10-23); the rest fades away.
 */
@Composable
fun EdgeFade(modifier: Modifier, height: Dp, color: Color, fromTop: Boolean, hold: Float = 0f) {
    val state = LocalGlassState.current
    val edge = color.copy(alpha = EDGE_ALPHA)
    val mid = color.copy(alpha = MID_ALPHA)
    // Full strength for the held share, half strength a little further on, then nothing.
    val midStop = hold + (1f - hold) * MID_STOP
    val brush = if (fromTop) {
        Brush.verticalGradient(0f to edge, hold to edge, midStop to mid, 1f to Color.Transparent)
    } else {
        Brush.verticalGradient(0f to Color.Transparent, 1f - midStop to mid, 1f - hold to edge, 1f to edge)
    }
    // The blur covers the held share only (behind the system bar or the reader's buttons); past it the tint alone
    // fades out, so no blurred edge ever cuts across a line of text.
    Box(modifier.fillMaxWidth().height(height)) {
        if (state != null && hold > 0f) {
            Box(
                Modifier
                    .align(if (fromTop) Alignment.TopCenter else Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(height * hold)
                    .hazeBlur(
                        HazeInput.Sources(state),
                        HazeBlurStyle {
                            blurRadius(EDGE_BLUR)
                            backgroundColor(color)
                        }
                    )
            )
        }
        Box(Modifier.matchParentSize().drawBehind { drawRect(brush) })
    }
}

private val TOP_REACH = 10.dp
private val BOTTOM_REACH = 24.dp
private val EDGE_BLUR = 12.dp
private const val EDGE_ALPHA = 0.92f
private const val MID_STOP = 0.45f
private const val MID_ALPHA = 0.55f
