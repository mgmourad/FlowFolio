package com.flowfolio.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGhostButton
import com.flowfolio.core.designsystem.FlowFolioMenu
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioSegmented
import com.flowfolio.core.designsystem.FlowFolioToggle
import com.flowfolio.core.designsystem.FlowFolioType

/** Narrator Mode's controls: play/pause, speed, page turns, and the phone speech engine's voice (ADR-0059). */
@Suppress("LongMethod") // the panel's rows, one after another
@Composable
internal fun ReaderNarratorPanel(
    narrating: Boolean,
    rate: Float,
    continuous: Boolean,
    voices: List<NarratorVoice>,
    currentVoice: String?,
    error: String?,
    onToggle: () -> Unit,
    onRate: (Float) -> Unit,
    onContinuous: (Boolean) -> Unit,
    onVoice: (String) -> Unit,
    onSpeechSettings: () -> Unit,
    onShown: () -> Unit = {}
) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onShown() }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
        Text(
            stringResource(R.string.reader_narrator),
            style = FlowFolioType.screenTitle.copy(fontSize = 22.sp).merge(FlowFolio.title)
        )
        if (narrating) {
            FlowFolioGhostButton(
                text = stringResource(R.string.reader_narrator_pause),
                onClick = onToggle,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            FlowFolioPrimaryButton(
                text = stringResource(R.string.reader_narrator_play),
                onClick = onToggle,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        if (error != null) {
            Text(error, style = FlowFolioType.rowLabel.merge(FlowFolio.item), modifier = Modifier.padding(top = 12.dp))
        }
        Text(
            stringResource(R.string.reader_narrator_rate),
            style = FlowFolioType.rowLabel.merge(FlowFolio.item),
            modifier = Modifier.padding(top = 16.dp)
        )
        // ADR-0052: five set speeds as segments rather than a slider, which landed on rates like 1.07×. A rate saved
        // from the old slider shows on its nearest segment until another is chosen.
        FlowFolioSegmented(
            options = SPEEDS.map { speedLabel(it) },
            selected = SPEEDS.indices.minBy { kotlin.math.abs(SPEEDS[it] - rate) },
            onSelect = { onRate(SPEEDS[it]) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.reader_narrator_continuous),
                style = FlowFolioType.rowLabel.merge(FlowFolio.item)
            )
            FlowFolioToggle(continuous, onContinuous)
        }
        Text(
            stringResource(R.string.reader_narrator_voice),
            style = FlowFolioType.rowLabel.merge(FlowFolio.item),
            modifier = Modifier.padding(top = 16.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VoicePicker(voices, currentVoice, onVoice)
            FlowFolioGhostButton(
                text = stringResource(R.string.reader_narrator_speech_settings),
                onClick = onSpeechSettings
            )
        }
    }
}

/** "English (Australia) · Voice 2 · Online". */
@Composable
private fun voiceLine(v: NarratorVoice): String = listOf(
    v.language,
    stringResource(R.string.reader_narrator_voice_number, v.label),
    stringResource(if (v.online) R.string.reader_narrator_online else R.string.reader_narrator_offline)
).joinToString(" · ")

/** The voice and a menu of the engine's voices, a language heading over each language's voices. */
@Composable
private fun VoicePicker(voices: List<NarratorVoice>, current: String?, onVoice: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = voices.firstOrNull { it.name == current }
    val label = selected?.let { voiceLine(it) } ?: stringResource(R.string.reader_narrator_voice_default)
    FlowFolioGhostButton(text = label, onClick = { if (voices.isNotEmpty()) open = true })
    FlowFolioMenu(expanded = open, onDismissRequest = { open = false }) {
        var language: String? = null
        voices.forEach { v ->
            if (v.language != language) {
                language = v.language
                Text(
                    v.language,
                    style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp)
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.reader_narrator_voice_number, v.label) + " · " +
                            stringResource(
                                if (v.online) R.string.reader_narrator_online else R.string.reader_narrator_offline
                            ),
                        color = if (v.name == current) FlowFolio.palette.accent else FlowFolio.palette.text
                    )
                },
                onClick = {
                    open = false
                    onVoice(v.name)
                }
            )
        }
    }
}

/** The narrator's speeds (ADR-0052, the mock's `.mini` row). */
internal val SPEEDS = listOf(0.8f, 1f, 1.25f, 1.5f, 2f)

/** "0.8×", "1×", "1.25×": no trailing zeros. */
internal fun speedLabel(rate: Float): String =
    java.math.BigDecimal(rate.toString()).stripTrailingZeros().toPlainString() + "×"
