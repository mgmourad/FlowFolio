package com.flowfolio.feature.reader

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioSegmented
import com.flowfolio.core.designsystem.FlowFolioType
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The narrator's speed (owner, 2026-11-13): the set speeds as segments only as wide as they need, and beside them
 * a slider from 0.25× to 2.5× for fine-tuning, a turtle at the slow end and a running rabbit at the fast one, a
 * mark at every step and a tick of haptics as each is passed. Side by side where the panel leaves the slider a
 * usable length (a tablet); one above the other on a phone's panel, where beside the speeds the slider was left
 * under 3 dp a step (checked on the unfolded Fold, 2026-11-13).
 */
@Composable
internal fun NarratorSpeed(rate: Float, onRate: (Float) -> Unit, modifier: Modifier = Modifier) {
    val preset = SPEEDS.indexOfFirst { abs(it - rate) < HALF_STEP }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val segments = @Composable {
            FlowFolioSegmented(
                options = SPEEDS.map { speedLabel(it) },
                selected = preset,
                onSelect = { onRate(SPEEDS[it]) }
            )
        }
        if (maxWidth >= SIDE_BY_SIDE) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                segments()
                SpeedSlider(rate, onRate, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                segments()
                SpeedSlider(rate, onRate, Modifier.fillMaxWidth())
            }
        }
    }
}

/** The fine-tuning slider: [rate] in steps of 0.05× from 0.25× to 2.5×; [onRate] once the finger lifts. */
// One control: its gestures, haptics and marks belong together.
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun SpeedSlider(rate: Float, onRate: (Float) -> Unit, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    val view = LocalView.current
    val commit by rememberUpdatedState(onRate)
    val current by rememberUpdatedState(rate)
    var dragging by remember { mutableStateOf(false) }
    var shown by remember { mutableFloatStateOf(rate) }
    val value = if (dragging) shown else rate
    var widthPx by remember { mutableFloatStateOf(1f) }

    /** Moves the thumb to [x] px along the track, with a tick of haptics for each step passed. */
    fun moveTo(x: Float) {
        val next = snap(MIN + (x / widthPx).coerceIn(0f, 1f) * (MAX - MIN))
        if (next != shown) {
            val major = (next / MAJOR).let { abs(it - it.roundToInt()) < EPSILON }
            view.performHapticFeedback(
                if (major) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.CLOCK_TICK
            )
            shown = next
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🐢", fontSize = ICON_SP.sp)
        Column(Modifier.weight(1f)) {
            // The value rides above the thumb.
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
                Text(
                    speedLabel(value),
                    style = FlowFolioType.chip.copy(
                        fontSize = 12.5.sp,
                        fontWeight = if (dragging) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (dragging) p.accent else p.ink2
                    ),
                    modifier = Modifier.align(
                        androidx.compose.ui.BiasAlignment((value - MIN) / (MAX - MIN) * 2f - 1f, 0f)
                    )
                )
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(TRACK_HEIGHT)
                    .padding(horizontal = 2.dp)
                    .semantics {
                        contentDescription = "Speed"
                        progressBarRangeInfo = ProgressBarRangeInfo(value, MIN..MAX, STEPS - 1)
                        setProgress { target ->
                            commit(snap(target))
                            true
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            shown = current
                            moveTo(offset.x)
                            commit(shown)
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                shown = current
                                dragging = true
                                moveTo(offset.x)
                            },
                            onDragEnd = {
                                dragging = false
                                commit(shown)
                            },
                            onDragCancel = { dragging = false }
                        ) { change, _ ->
                            change.consume()
                            moveTo(change.position.x)
                        }
                    }
            ) {
                widthPx = size.width
                val mid = size.height / 2
                // A mark at every 0.05× step, taller at each quarter, the 1× mark tallest.
                for (i in 0 until STEPS) {
                    val v = MIN + i * STEP
                    val x = (v - MIN) / (MAX - MIN) * size.width
                    val quarter = abs(v / MAJOR - (v / MAJOR).roundToInt()) < EPSILON
                    val one = abs(v - 1f) < EPSILON
                    val h = size.height * when {
                        one -> 0.9f
                        quarter -> 0.6f
                        else -> 0.28f
                    }
                    drawLine(
                        if (v <= value + EPSILON) p.accent.copy(alpha = 0.85f) else p.ink3.copy(alpha = 0.55f),
                        Offset(x, mid - h / 2),
                        Offset(x, mid + h / 2),
                        strokeWidth = if (quarter) 2.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                val thumb = (value - MIN) / (MAX - MIN) * size.width
                drawCircle(p.accent, radius = if (dragging) 9.dp.toPx() else 7.dp.toPx(), center = Offset(thumb, mid))
                drawCircle(p.surface, radius = 2.5.dp.toPx(), center = Offset(thumb, mid))
            }
        }
        Text("🐇", fontSize = ICON_SP.sp)
    }
}

/** [v] on the slider's steps: 0.25× to 2.5× in 0.05× steps. */
private fun snap(v: Float): Float = ((v.coerceIn(MIN, MAX) / STEP).roundToInt() * STEP * HUNDRED).roundToInt() / HUNDRED

/** The narrator's speeds (ADR-0052, the mock's `.mini` row; 0.5× and 0.75× added 2026-11-13). */
internal val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** "0.75×", "1×", "1.15×": no trailing zeros. */
internal fun speedLabel(rate: Float): String =
    java.math.BigDecimal(((rate * HUNDRED).roundToInt() / HUNDRED).toString()).stripTrailingZeros().toPlainString() +
        "×"

private const val MIN = 0.25f
private const val MAX = 2.5f
private const val STEP = 0.05f
private const val MAJOR = 0.25f
private const val STEPS = 46
private const val HALF_STEP = 0.025f
private const val EPSILON = 0.001f
private const val HUNDRED = 100f
private const val ICON_SP = 20
private val TRACK_HEIGHT = 30.dp
private val SIDE_BY_SIDE = 600.dp
