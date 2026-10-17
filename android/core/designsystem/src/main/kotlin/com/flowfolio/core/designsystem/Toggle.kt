package com.flowfolio.core.designsystem

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The design's on/off toggle: a rounded track with a round thumb. Off: thumb left in the off colour on a grey track;
 * on: thumb right in orange on a green (dark) or blue (light) track. Dark mode glows; light mode casts a soft shadow.
 * Pass [onCheckedChange] = null when a parent row handles the toggle (it then only draws).
 */
@Composable
fun FlowFolioToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val p = FlowFolio.palette
    val position by animateFloatAsState(if (checked) 1f else 0f, label = "toggle")
    val thumb by animateColorAsState(if (checked) p.toggleOnThumb else p.toggleOffThumb, label = "thumb")
    val track by animateColorAsState(if (checked) p.toggleOnTrack else p.toggleOffTrack, label = "track")
    val interactive = if (onCheckedChange != null) {
        Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    Canvas(modifier.then(interactive).size(TRACK_W, TRACK_H).alpha(if (enabled) 1f else DISABLED_ALPHA)) {
        val h = size.height
        val radius = CornerRadius(h / 2)
        val glow = p.dark
        // Track, with a darker rim on its inner edge for the design's slightly sunken look.
        drawSoft(track, glow && checked) { paint -> drawRoundRect(0f, 0f, size.width, h, h / 2, h / 2, paint) }
        drawRoundRect(track, cornerRadius = radius)
        drawRoundRect(Color.Black.copy(alpha = RIM_ALPHA), cornerRadius = radius, style = Stroke(h * RIM_WIDTH))
        // Thumb.
        val r = h / 2 - h * THUMB_INSET
        val cx = h / 2 + position * (size.width - h)
        val centre = Offset(cx, h / 2)
        drawSoft(thumb, glow) { paint -> drawCircle(centre.x, centre.y + if (glow) 0f else h * DROP, r, paint) }
        drawCircle(thumb, r, centre)
    }
}

/** A blurred copy under a shape: a glow in [color] (dark) or a soft drop shadow (light). */
internal fun DrawScope.drawSoft(
    color: Color,
    glow: Boolean,
    draw: android.graphics.Canvas.(android.graphics.Paint) -> Unit
) {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (glow) color.copy(alpha = GLOW_ALPHA).toArgb() else SHADOW.toArgb()
            maskFilter = BlurMaskFilter(size.height * BLUR, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.draw(paint)
    }
}

private val TRACK_W = 52.dp
private val TRACK_H = 30.dp
private const val THUMB_INSET = 0.06f
private const val RIM_ALPHA = 0.25f
private const val RIM_WIDTH = 0.06f
private const val DROP = 0.08f
private const val BLUR = 0.22f
private const val GLOW_ALPHA = 0.8f
private const val DISABLED_ALPHA = 0.4f
private val SHADOW = Color.Black.copy(alpha = 0.35f)
