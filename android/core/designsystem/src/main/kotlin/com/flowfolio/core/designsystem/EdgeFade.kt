package com.flowfolio.core.designsystem

import androidx.compose.animation.core.EaseIn
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
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Soft, blurred fades at the top and bottom of the screen (owner, 2026-10-23), so content scrolling under the status
 * bar, the front camera and the navigation bar melts into the theme instead of running into them. Each fade blurs what
 * is behind it, most at the edge and not at all where it ends, and tints it with the background colour drawn at that
 * edge (the surface at the top, the deep background at the bottom). The blur needs [LocalGlassState]; without it the
 * tint alone shows.
 */
@Composable
fun BoxScope.EdgeFades(alpha: Float = 1f) {
    val density = LocalDensity.current
    val top = with(density) { WindowInsets.statusBars.getTop(this).toDp() } + TOP_REACH
    val bottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() } + BOTTOM_REACH
    val p = FlowFolio.palette
    EdgeFade(Modifier.align(Alignment.TopCenter).alpha(alpha), top, p.surface, fromTop = true)
    EdgeFade(Modifier.align(Alignment.BottomCenter).alpha(alpha), bottom, p.backgroundDeep, fromTop = false)
}

@Composable
private fun EdgeFade(modifier: Modifier, height: Dp, color: Color, fromTop: Boolean) {
    val state = LocalGlassState.current
    val edge = color.copy(alpha = EDGE_ALPHA)
    val mid = color.copy(alpha = MID_ALPHA)
    val brush = if (fromTop) {
        Brush.verticalGradient(0f to edge, MID_STOP to mid, 1f to Color.Transparent)
    } else {
        Brush.verticalGradient(0f to Color.Transparent, 1f - MID_STOP to mid, 1f to edge)
    }
    val blurred = if (state != null) {
        Modifier.hazeBlur(
            HazeInput.Sources(state),
            HazeBlurStyle {
                blurRadius(EDGE_BLUR)
                backgroundColor(color)
                progressive(
                    HazeProgressive.verticalGradient(
                        easing = EaseIn,
                        startIntensity = if (fromTop) 1f else 0f,
                        endIntensity = if (fromTop) 0f else 1f
                    )
                )
            }
        )
    } else {
        Modifier
    }
    Box(modifier.fillMaxWidth().height(height).then(blurred).drawBehind { drawRect(brush) })
}

private val TOP_REACH = 28.dp
private val BOTTOM_REACH = 24.dp
private val EDGE_BLUR = 12.dp
private const val EDGE_ALPHA = 0.92f
private const val MID_STOP = 0.45f
private const val MID_ALPHA = 0.55f
