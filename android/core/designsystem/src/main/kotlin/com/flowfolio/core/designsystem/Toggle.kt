package com.flowfolio.core.designsystem

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The toggle (`.sw`, ADR-0052): a 46 x 28 track that *is* the state, the accent when on and surface 3 when off, with
 * a white 22 dp thumb that springs across. Nothing else changes colour, so on and off read by position and track alone.
 * The touch target is at least 48 dp. Pass [onCheckedChange] = null when a parent row handles the toggle (it then only
 * draws).
 *
 * [progress] (0 to 1, or [INDETERMINATE]) rings the thumb while what the toggle turned on is being prepared (Dynamic
 * Flow converting a book).
 */
@Composable
fun FlowFolioToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    progress: Float? = null
) {
    val target = if (onCheckedChange != null) modifier.minimumInteractiveComponentSize() else modifier
    if (progress == null) {
        ToggleBody(checked, onCheckedChange, target, enabled)
        return
    }
    Box(target, contentAlignment = Alignment.Center) {
        ToggleBody(checked, onCheckedChange, Modifier, enabled)
        KnobRing(checked, progress, Modifier.size(TRACK_W.dp, TRACK_H.dp))
    }
}

/** The ring round the thumb: a faint full circle and the part done, or a turning arc when it can't say how far. */
@Composable
private fun KnobRing(checked: Boolean, progress: Float, modifier: Modifier) {
    val p = FlowFolio.palette
    val spin = rememberInfiniteTransition(label = "ring")
    val turn by spin.animateFloat(
        0f,
        FULL_TURN,
        infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing)),
        label = "turn"
    )
    val shown by animateFloatAsState(progress.coerceAtLeast(0f), tween(Motion.MEDIUM_MS), label = "progress")
    val position by animateFloatAsState(if (checked) 1f else 0f, Motion.bouncySpec(), label = "ringKnob")
    Canvas(modifier) {
        val unit = size.height / TRACK_H
        val r = (KNOB / 2f + RING_GAP) * unit
        val centre = Offset((INSET + KNOB / 2f + position * TRAVEL) * unit, size.height / 2f)
        val stroke = Stroke(RING_WIDTH * unit, cap = StrokeCap.Round)
        val box = androidx.compose.ui.geometry.Rect(centre, r)
        drawCircle(p.ink.copy(alpha = RING_TRACK_ALPHA), r, centre, style = stroke)
        if (progress < 0f) {
            drawArc(p.ink, turn - QUARTER, SPIN_SWEEP, false, box.topLeft, box.size, style = stroke)
        } else {
            drawArc(p.ink, -QUARTER, FULL_TURN * shown, false, box.topLeft, box.size, style = stroke)
        }
    }
}

/** The toggle itself (`.sw`). */
@Composable
private fun ToggleBody(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier, enabled: Boolean) {
    val p = FlowFolio.palette
    val position by animateFloatAsState(if (checked) 1f else 0f, Motion.bouncySpec(), label = "knob")
    val track by animateColorAsState(
        if (checked) p.toggleOnTrack else p.toggleOffTrack,
        Motion.colorSpec(),
        label = "track"
    )
    val interactive = if (onCheckedChange != null) {
        Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    Box(modifier.then(interactive), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(TRACK_W.dp, TRACK_H.dp).alpha(if (enabled) 1f else DISABLED_ALPHA)) {
            val unit = size.height / TRACK_H
            drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2f))
            val r = KNOB / 2f * unit
            val centre = Offset((INSET + KNOB / 2f + position * TRAVEL) * unit, size.height / 2f)
            // `box-shadow: 0 1px 3px rgba(0,0,0,.25)`, as two soft rings: cheap, no blur in a list.
            drawCircle(Color.Black.copy(alpha = 0.08f), r + 1.5f * unit, centre + Offset(0f, 1f * unit))
            drawCircle(Color.Black.copy(alpha = 0.12f), r + 0.5f * unit, centre + Offset(0f, 1f * unit))
            drawCircle(p.toggleOnThumb, r, centre)
        }
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

/** Pass as a toggle's progress when how far can't be told yet (waiting its turn). */
const val INDETERMINATE = -1f

private const val FULL_TURN = 360f
private const val QUARTER = 90f
private const val SPIN_SWEEP = 100f
private const val SPIN_MS = 1_100
private const val RING_GAP = 2.5f
private const val RING_WIDTH = 2.2f
private const val RING_TRACK_ALPHA = 0.18f
private const val TRACK_W = 46f
private const val TRACK_H = 28f
private const val KNOB = 22f
private const val INSET = 3f
private const val TRAVEL = 18f
private const val DISABLED_ALPHA = 0.4f
private const val MIN_BLUR = 0.5f
private const val GLYPH_GLOW_ALPHA = 0.75f
private const val GLYPH_BLUR = 0.3f
private val SHADOW = Color.Black.copy(alpha = 0.3f)
