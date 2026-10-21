package com.flowfolio.core.designsystem

import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * A 0 to 1 slider in the toggle's colours, for levels such as a sound's volume. It follows the finger on its own and
 * reports the value once, when let go ([onValueChangeFinished]), so a setting is saved once rather than every frame.
 */
@Composable
fun FlowFolioSlider(
    value: Float,
    onValueChangeFinished: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val p = FlowFolio.palette
    var dragging by remember { mutableStateOf<Float?>(null) }
    Slider(
        value = dragging ?: value,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let(onValueChangeFinished)
            dragging = null
        },
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = p.toggleOnThumb,
            activeTrackColor = p.toggleOnTrack,
            inactiveTrackColor = p.toggleOffTrack,
            disabledThumbColor = p.toggleOffThumb,
            disabledActiveTrackColor = p.toggleOffTrack,
            disabledInactiveTrackColor = p.toggleOffTrack
        ),
        modifier = modifier.semantics { contentDescription = label }
    )
}
