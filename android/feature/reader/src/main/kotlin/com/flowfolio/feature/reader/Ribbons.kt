package com.flowfolio.feature.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.model.BookmarkColor

/** Ribbons by page, for the page panes. */
internal val LocalRibbons = staticCompositionLocalOf<Map<Int, BookmarkColor>> { emptyMap() }

/**
 * The mock's ribbon (`.ribbon`): a flat, solid silk ribbon with a swallowtail notch, hanging from just above the
 * page's top edge, with a soft drop shadow under it. It drops in on a spring when the page is marked (`.marked`,
 * 700 ms) and is drawn as part of the page, so it turns with it.
 */
@Composable
internal fun Ribbon(color: Color, modifier: Modifier = Modifier) {
    // Pages are recomposed as they turn; only a ribbon that appears on a page in view drops in.
    val drop = remember { Animatable(-1f) }
    LaunchedEffect(Unit) { drop.animateTo(0f, tween(DROP_MS, easing = Motion.Spring)) }
    Canvas(
        modifier
            .graphicsLayer { translationY = drop.value * size.height * DROP_REACH }
            .size(RIBBON_WIDTH, RIBBON_HEIGHT)
    ) {
        val w = size.width
        val h = size.height
        val notch = h * NOTCH_DEPTH
        val ribbon = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, h)
            lineTo(w / 2f, h - notch)
            lineTo(0f, h)
            close()
        }
        // `drop-shadow(0 4px 6px rgba(0,0,0,.4))`, drawn as two offset copies rather than a blur.
        drawPath(Path().apply { addPath(ribbon, Offset(0f, 4.dp.toPx())) }, Color.Black.copy(alpha = SHADOW_FAR))
        drawPath(Path().apply { addPath(ribbon, Offset(0f, 2.dp.toPx())) }, Color.Black.copy(alpha = SHADOW_NEAR))
        drawPath(ribbon, color)
    }
}

private const val DROP_MS = 700
private val RIBBON_WIDTH = 22.dp
private val RIBBON_HEIGHT = 62.dp

/** The notch is the bottom 20% (`polygon(... 50% 80% ...)`). */
private const val NOTCH_DEPTH = 0.2f

/** Hidden, the ribbon sits 110% of its height up (`translateY(-110%)`). */
private const val DROP_REACH = 1.1f
private const val SHADOW_FAR = 0.14f
private const val SHADOW_NEAR = 0.16f
