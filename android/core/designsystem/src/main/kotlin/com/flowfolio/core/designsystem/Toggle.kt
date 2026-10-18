package com.flowfolio.core.designsystem

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The mock's toggle (`.tgl`), exactly: a 58 x 34 track with a sunken inner shadow and a soft drop shadow; a 28 px knob
 * with a drop shadow, a top highlight and a bottom shade. Off: grey track, knob in the off colour on the left. On:
 * the on track colour, an orange knob on the right, glowing in dark mode. The knob travels on the mock's spring.
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
    val spring = tween<Float>(Motion.SPRING_MS, easing = Motion.Spring)
    val position by animateFloatAsState(if (checked) 1f else 0f, spring, label = "knob")
    val knob by animateColorAsState(
        if (checked) p.toggleOnThumb else p.toggleOffThumb,
        tween(400),
        label = "knobColour"
    )
    val track by animateColorAsState(if (checked) p.toggleOnTrack else p.toggleOffTrack, tween(400), label = "track")
    val glowing = p.dark && checked && p.titleGlow.color.alpha > 0f
    val interactive = if (onCheckedChange != null) {
        Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    Canvas(modifier.then(interactive).size(58.dp, 34.dp).alpha(if (enabled) 1f else DISABLED_ALPHA)) {
        val unit = size.height / TRACK_H
        val trackRound = RoundRect(0f, 0f, size.width, size.height, CornerRadius(TRACK_RADIUS * unit))
        // Track: `0 6px 12px -4px rgba(0,0,0,.4)` outside, `inset 0 3px 6px rgba(0,0,0,.35)` inside.
        blurred(Color.Black.copy(alpha = 0.4f), 12f * unit) {
            drawRoundRect(
                4f * unit,
                10f * unit,
                size.width - 4f * unit,
                size.height + 2f * unit,
                TRACK_RADIUS * unit,
                TRACK_RADIUS * unit,
                it
            )
        }
        drawPath(Path().apply { addRoundRect(trackRound) }, track)
        insetShadow(trackRound, Color.Black.copy(alpha = 0.35f), 6f * unit, 3f * unit)
        // Knob: 28 px, 3 px from the edges, travelling 24 px.
        val r = KNOB / 2f * unit
        val centre = Offset((INSET + KNOB / 2f + position * TRAVEL) * unit, size.height / 2f)
        if (glowing) blurred(p.toggleOnThumb.copy(alpha = 0.75f), 14f * unit) { drawCircle(centre.x, centre.y, r, it) }
        blurred(Color.Black.copy(alpha = 0.4f), 8f * unit) { drawCircle(centre.x, centre.y + 4f * unit, r, it) }
        drawCircle(knob, r, centre)
        // `inset 0 2px 3px rgba(255,255,255,.25), inset 0 -3px 4px rgba(0,0,0,.25)`: lit top, shaded bottom.
        drawCircle(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (glowing) 0.3f else 0.25f),
                0.35f to Color.Transparent,
                0.7f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.25f),
                startY = centre.y - r,
                endY = centre.y + r
            ),
            r,
            centre
        )
    }
}

/** Draws [draw] with a CSS-like blur of [blurPx] in [color]. */
internal fun DrawScope.blurred(
    color: Color,
    blurPx: Float,
    draw: android.graphics.Canvas.(android.graphics.Paint) -> Unit
) {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter((blurPx / 2f).coerceAtLeast(MIN_BLUR), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.draw(paint)
    }
}

/** A CSS `inset` shadow inside [shape]. */
internal fun DrawScope.insetShadow(shape: RoundRect, color: Color, blurPx: Float, offsetY: Float) {
    val inner = Path().apply { addRoundRect(shape) }
    val ring = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(androidx.compose.ui.geometry.Rect(-size.width, -size.height, 2 * size.width, 2 * size.height))
        addRoundRect(
            RoundRect(
                shape.left,
                shape.top + offsetY,
                shape.right,
                shape.bottom + offsetY,
                shape.topLeftCornerRadius,
                shape.topRightCornerRadius,
                shape.bottomRightCornerRadius,
                shape.bottomLeftCornerRadius
            )
        )
    }
    clipPath(inner) { blurred(color, blurPx) { drawPath(ring.asAndroidPath(), it) } }
}

/**
 * A blurred copy under a shape: a glow in [color] (dark) or a soft drop shadow (light). Used by glyphs (ADR-0014).
 */
internal fun DrawScope.drawSoft(
    color: Color,
    glow: Boolean,
    draw: android.graphics.Canvas.(android.graphics.Paint) -> Unit
) = blurred(if (glow) color.copy(alpha = GLYPH_GLOW_ALPHA) else SHADOW, size.height * GLYPH_BLUR, draw)

private const val TRACK_H = 34f
private const val TRACK_RADIUS = 20f
private const val KNOB = 28f
private const val INSET = 3f
private const val TRAVEL = 24f
private const val DISABLED_ALPHA = 0.4f
private const val MIN_BLUR = 0.5f
private const val GLYPH_GLOW_ALPHA = 0.75f
private const val GLYPH_BLUR = 0.3f
private val SHADOW = Color.Black.copy(alpha = 0.3f)
