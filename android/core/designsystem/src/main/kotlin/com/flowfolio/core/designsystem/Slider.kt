package com.flowfolio.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** A slider's value as a percentage, the default readout. */
val PercentReadout: (Float) -> String = { "${(it * 100).roundToInt()}%" }

/**
 * A 0 to 1 slider (`.range`, ADR-0052): a 4 dp track filled in the accent, a 22 dp thumb on the surface with an
 * accent ring, and **its value always shown** at the end ([readout], a percentage by default). It follows the finger
 * on its own and reports the value once, when let go ([onValueChangeFinished]), so a setting is saved once rather than
 * every frame. [steps] snaps it to that many stops between the ends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowFolioSlider(
    value: Float,
    onValueChangeFinished: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readout: (Float) -> String = PercentReadout,
    steps: Int = 0
) {
    val p = FlowFolio.palette
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: value
    val text = readout(shown)
    Row(
        modifier.alpha(if (enabled) 1f else DISABLED),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let(onValueChangeFinished)
                dragging = null
            },
            enabled = enabled,
            steps = steps,
            modifier = Modifier.weight(1f).semantics {
                contentDescription = label
                stateDescription = text
            },
            thumb = {
                Box(
                    Modifier
                        .size(22.dp)
                        .background(p.surface, CircleShape)
                        .border(2.dp, p.accent, CircleShape)
                )
            },
            track = { state ->
                val range = state.valueRange
                val fraction = ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    val r = CornerRadius(size.height / 2f)
                    drawRoundRect(p.surface3, cornerRadius = r)
                    drawRoundRect(p.accent, Offset.Zero, Size(size.width * fraction, size.height), r)
                }
            }
        )
        Text(
            text,
            style = FlowFolioType.body.copy(fontSize = 12.5.sp, color = p.ink2, textAlign = TextAlign.End),
            modifier = Modifier.widthIn(min = 40.dp)
        )
    }
}

private const val DISABLED = 0.45f
