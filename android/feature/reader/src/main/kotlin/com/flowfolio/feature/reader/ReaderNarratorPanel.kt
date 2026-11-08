package com.flowfolio.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
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
import com.flowfolio.core.designsystem.FlowFolioToggle
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.voice.Voice
import com.flowfolio.core.voice.VoicePackState

/** Narrator Mode's controls (Phase 6 M3): play/pause, speed, page turns, and the Kokoro voice. */
@Composable
internal fun ReaderNarratorPanel(
    narrating: Boolean,
    rate: Float,
    continuous: Boolean,
    voices: List<Voice>,
    currentVoice: String?,
    packState: VoicePackState,
    error: String?,
    onToggle: () -> Unit,
    onRate: (Float) -> Unit,
    onContinuous: (Boolean) -> Unit,
    onVoice: (String) -> Unit
) {
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
        PackStatus(packState, error)
        Text(
            stringResource(R.string.reader_narrator_rate),
            style = FlowFolioType.rowLabel.merge(FlowFolio.item),
            modifier = Modifier.padding(top = 16.dp)
        )
        Slider(
            value = rate,
            onValueChange = onRate,
            valueRange = RATE_MIN..RATE_MAX,
            modifier = Modifier.fillMaxWidth()
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
        if (voices.size > 1) {
            Text(
                stringResource(R.string.reader_narrator_voice),
                style = FlowFolioType.rowLabel.merge(FlowFolio.item),
                modifier = Modifier.padding(top = 16.dp)
            )
            VoicePicker(voices, currentVoice, onVoice)
        }
    }
}

@Composable
private fun PackStatus(packState: VoicePackState, error: String?) {
    when (packState) {
        is VoicePackState.Downloading -> {
            Text(
                stringResource(R.string.reader_narrator_downloading, (packState.progress * 100).toInt()),
                style = FlowFolioType.rowLabel.merge(FlowFolio.item),
                modifier = Modifier.padding(top = 12.dp)
            )
            LinearProgressIndicator(
                progress = { packState.progress },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }

        is VoicePackState.Failed -> Text(
            stringResource(R.string.reader_narrator_download_failed, packState.message),
            style = FlowFolioType.rowLabel.merge(FlowFolio.item),
            modifier = Modifier.padding(top = 12.dp)
        )

        else -> Unit
    }
    if (error != null) {
        Text(
            error,
            style = FlowFolioType.rowLabel.merge(FlowFolio.item),
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

/**
 * A floating, non-modal pill at the foot of the page while the narrator's voice is downloading (owner,
 * 2026-11-08): the reader can keep reading, but the download is never silent.
 */
@Composable
internal fun BoxScope.VoiceDownloadOverlay(state: VoicePackState) {
    val progress = (state as? VoicePackState.Downloading)?.progress ?: return
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(start = 40.dp, end = 40.dp, bottom = 108.dp)
            .glass(RoundedCornerShape(24.dp), tint = FlowFolio.palette.tabBar)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            stringResource(R.string.reader_narrator_downloading_title),
            style = FlowFolioType.rowLabel.merge(FlowFolio.title)
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
        Text(
            stringResource(R.string.reader_narrator_downloading, (progress * 100).toInt()),
            style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun VoicePicker(voices: List<Voice>, current: String?, onVoice: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = voices.firstOrNull { it.name == current }?.label
        ?: stringResource(R.string.reader_narrator_voice_default)
    FlowFolioGhostButton(text = label, onClick = { open = true })
    FlowFolioMenu(expanded = open, onDismissRequest = { open = false }) {
        voices.forEach { v ->
            DropdownMenuItem(
                text = {
                    Column {
                        Text(v.label)
                        Text(
                            v.name,
                            fontSize = 11.sp,
                            color = FlowFolio.palette.text.copy(alpha = 0.55f)
                        )
                    }
                },
                onClick = {
                    open = false
                    onVoice(v.name)
                }
            )
        }
    }
}

private const val RATE_MIN = 0.5f
private const val RATE_MAX = 2f
