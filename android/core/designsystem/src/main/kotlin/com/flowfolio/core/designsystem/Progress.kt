package com.flowfolio.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A 6 dp progress bar (`.prog`): a sunken track and a title-to-sub-item gradient that glows and grows in on first show.
 *
 * Reaching the end throws confetti (owner, M4). [celebrationId] names the thing being finished (a book's id, a goal's
 * scope) so coming back to a screen that is already full still celebrates once a run; without one, only a completion
 * the reader watches happen is celebrated. The burst is drawn over the bar and reaches [ConfettiHeight] above it, so
 * it shows in full only where nothing clips it.
 */
@Composable
fun FlowFolioProgressBar(progress: Float, modifier: Modifier = Modifier, celebrationId: Any? = null) {
    val p = FlowFolio.palette
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val grown by animateFloatAsState(
        if (shown) progress.coerceIn(0f, 1f) else 0f,
        tween(Motion.GROW_MS, Motion.GROW_DELAY_MS, Motion.EaseOut),
        label = "progress"
    )
    val celebrate = rememberCelebration(progress, celebrationId)
    // The mock's `.pb`: a 4 dp track in surface 3, filled flat in the accent (ADR-0052).
    val shape = RoundedCornerShape(50)
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(shape)
                .drawBehind { drawRect(p.surface3) }
        ) {
            Spacer(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(grown)
                    .clip(shape)
                    .drawBehind { drawRect(p.accent) }
            )
        }
        if (celebrate.value) {
            FlowFolioConfetti(
                play = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .requiredHeight(ConfettiHeight)
            ) { celebrate.value = false }
        }
    }
}

/**
 * A progress ring ([diameter], 84 dp by default; the folded goal card's is 46 dp) in the accent on a surface-3 track,
 * sweeping in over 1.6 s (ADR-0052). A full ring
 * throws confetti on the same terms as [FlowFolioProgressBar] (owner, M4).
 */
@Composable
fun FlowFolioRing(
    progress: Float,
    modifier: Modifier = Modifier,
    celebrationId: Any? = null,
    diameter: Dp = 84.dp,
    /** The arc's colour; the accent by default (the goal ring turns to the success colour once reached). */
    color: Color? = null,
    center: @Composable BoxScope.() -> Unit = {}
) {
    val p = FlowFolio.palette
    val celebrate = rememberCelebration(progress, celebrationId)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val sweep by animateFloatAsState(
        if (shown) progress.coerceIn(0f, 1f) else 0f,
        tween(RING_MS, RING_DELAY_MS, Motion.EaseOut),
        label = "ring"
    )
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val unit = size.minDimension / 84.dp.toPx()
            val stroke = 8.dp.toPx() * unit
            val inset = stroke / 2f + 4.dp.toPx() * unit
            val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
            val topLeft = Offset(inset, inset)
            drawArc(p.surface3, 0f, FULL_TURN, false, topLeft, arcSize, style = Stroke(stroke))
            if (p.itemGlow.color.alpha > 0f) {
                blurred(p.ink.copy(alpha = 0.7f), 12.dp.toPx()) { paint ->
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.strokeWidth = stroke
                    paint.strokeCap = android.graphics.Paint.Cap.ROUND
                    drawArc(
                        topLeft.x,
                        topLeft.y,
                        topLeft.x + arcSize.width,
                        topLeft.y + arcSize.height,
                        START_ANGLE,
                        FULL_TURN * sweep,
                        false,
                        paint
                    )
                }
            }
            drawArc(
                color ?: p.accent,
                START_ANGLE,
                FULL_TURN * sweep,
                false,
                topLeft,
                arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        center()
        if (celebrate.value) {
            FlowFolioConfetti(
                play = true,
                modifier = Modifier.align(Alignment.BottomCenter).size(diameter).requiredHeight(ConfettiHeight)
            ) { celebrate.value = false }
        }
    }
}

private const val RING_MS = 1600
private const val RING_DELAY_MS = 450
private const val FULL_TURN = 360f
private const val START_ANGLE = -90f
