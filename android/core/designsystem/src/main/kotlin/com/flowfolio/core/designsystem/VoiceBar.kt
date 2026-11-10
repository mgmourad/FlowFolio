@file:Suppress("MagicNumber")

package com.flowfolio.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.max

/**
 * A voice note's playback (owner, M5): play or pause, stop, the recording drawn as a waveform that lights as it
 * plays, and how long it runs.
 *
 * The waveform is the loudnesses captured while recording, so it is the shape of what was actually said rather than
 * a decorative squiggle. Tapping along it seeks.
 */
@Composable
fun VoiceBar(
    levels: List<Float>,
    millis: Long,
    playing: Boolean,
    positionMillis: Int,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = FlowFolio.palette
    val progress = if (millis <= 0L) 0f else (positionMillis / millis.toFloat()).coerceIn(0f, 1f)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.surface.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RoundGlyph(if (playing) Glyph.AutoHide else Glyph.Play, p.ink, onPlayPause)
        RoundGlyph(Glyph.Trash, p.ink2, onStop)
        Waveform(
            levels = levels,
            progress = progress,
            modifier = Modifier.weight(1f).height(WAVE_HEIGHT)
        )
        Text(millis.asClock(), style = FlowFolioType.rowDetail.merge(FlowFolio.hint))
    }
}

/** The recording drawn as bars, those already played lit and the rest dim. */
@Composable
fun Waveform(levels: List<Float>, progress: Float, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    Canvas(modifier) {
        if (levels.isEmpty()) return@Canvas
        val barWidth = 2.dp.toPx()
        val gap = 1.5f.dp.toPx()
        val step = barWidth + gap
        val fit = max(1, (size.width / step).toInt())
        // More samples than bars: take an even spread, so the picture is the whole recording rather than its start.
        val shown = if (levels.size <= fit) levels else List(fit) { levels[it * levels.size / fit] }
        val played = shown.size * progress
        shown.forEachIndexed { index, level ->
            val x = index * step + barWidth / 2f
            val height = max(barWidth, level.coerceIn(0f, 1f) * size.height)
            val colour = if (index <= played) p.ink else p.ink2.copy(alpha = 0.45f)
            drawLine(
                colour,
                Offset(x, size.height / 2f - height / 2f),
                Offset(x, size.height / 2f + height / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/** A small round button for the bar's controls. */
@Composable
private fun RoundGlyph(glyph: Glyph, colour: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(CONTROL)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        FlowFolioGlyph(glyph, contentDescription = null, color = colour, size = 16.dp, glow = false)
    }
}

/** "0:07", or "1:02:03" for the rare long one. */
fun Long.asClock(): String {
    val total = this / 1000
    val seconds = total % 60
    val minutes = (total / 60) % 60
    val hours = total / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private val WAVE_HEIGHT = 28.dp
private val CONTROL = 30.dp
