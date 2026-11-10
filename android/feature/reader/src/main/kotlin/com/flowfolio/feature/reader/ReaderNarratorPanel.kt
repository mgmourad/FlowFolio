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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.voice.Voice
import com.flowfolio.core.voice.VoicePackState
import com.flowfolio.core.voice.VoiceSource
import kotlinx.coroutines.delay

/** Narrator Mode's controls (Phase 6 M3): play/pause, speed, page turns, and the Kokoro or Piper voice. */
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
                stringResource(R.string.reader_narrator_downloading, (packState.progress * PERCENT_SCALE).toInt()),
                style = FlowFolioType.rowLabel.merge(FlowFolio.item),
                modifier = Modifier.padding(top = 12.dp)
            )
            LinearProgressIndicator(
                progress = { packState.progress },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }

        is VoicePackState.Installing -> InstallingStatus(packState.progress)

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

@Composable
private fun InstallingStatus(progress: Float) {
    val label = if (progress >= 1f) {
        stringResource(R.string.reader_narrator_loading)
    } else {
        stringResource(R.string.reader_narrator_installing, (progress * PERCENT_SCALE).toInt())
    }
    Text(
        label,
        style = FlowFolioType.rowLabel.merge(FlowFolio.item),
        modifier = Modifier.padding(top = 12.dp)
    )
    if (progress >= 1f) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
    } else {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
    }
}

/**
 * A floating, non-modal pill at the foot of the page while the narrator's voice is downloading or installing
 * (owner, 2026-11-08): the reader can keep reading, and a brief in-app notice announces when the voice is ready.
 */
@Composable
internal fun BoxScope.VoiceDownloadOverlay(state: VoicePackState) {
    var sawActiveInstall by remember { mutableStateOf(false) }
    var transientNotice by remember { mutableStateOf<String?>(null) }
    val readyText = stringResource(R.string.reader_narrator_ready_notice)
    val failedMessage = (state as? VoicePackState.Failed)?.let {
        stringResource(R.string.reader_narrator_download_failed, it.message)
    }
    LaunchedEffect(state, readyText, failedMessage) {
        when (state) {
            is VoicePackState.Downloading, is VoicePackState.Installing -> {
                sawActiveInstall = true
                transientNotice = null
            }

            VoicePackState.Ready -> if (sawActiveInstall) {
                sawActiveInstall = false
                transientNotice = readyText
                delay(NOTICE_DURATION_MS)
                transientNotice = null
            }

            is VoicePackState.Failed -> {
                sawActiveInstall = false
                transientNotice = failedMessage
                delay(ERROR_NOTICE_DURATION_MS)
                transientNotice = null
            }

            VoicePackState.Idle -> Unit
        }
    }
    when (state) {
        is VoicePackState.Downloading -> VoiceProgressPill(
            title = stringResource(R.string.reader_narrator_downloading_title),
            detail = stringResource(R.string.reader_narrator_downloading, (state.progress * PERCENT_SCALE).toInt()),
            progress = state.progress
        )

        is VoicePackState.Installing -> VoiceProgressPill(
            title = stringResource(R.string.reader_narrator_installing_title),
            detail = if (state.progress >= 1f) {
                stringResource(R.string.reader_narrator_loading)
            } else {
                stringResource(R.string.reader_narrator_installing, (state.progress * PERCENT_SCALE).toInt())
            },
            progress = state.progress.takeIf { it < 1f }
        )

        else -> transientNotice?.let { notice -> VoiceNoticePill(notice) }
    }
}

@Composable
private fun BoxScope.VoiceProgressPill(title: String, detail: String, progress: Float?) {
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(start = 40.dp, end = 40.dp, bottom = 108.dp)
            .glass(RoundedCornerShape(24.dp), tint = FlowFolio.palette.tabBar)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            title,
            style = FlowFolioType.rowLabel.merge(FlowFolio.title)
        )
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }
        Text(
            detail,
            style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun BoxScope.VoiceNoticePill(message: String) {
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(start = 40.dp, end = 40.dp, bottom = 108.dp)
            .glass(RoundedCornerShape(24.dp), tint = FlowFolio.palette.tabBar)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            message,
            style = FlowFolioType.rowLabel.merge(FlowFolio.title)
        )
    }
}

@Composable
private fun VoicePicker(voices: List<Voice>, current: String?, onVoice: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = voices.firstOrNull { it.name == current }
    val label = selected?.let { v ->
        val engineName = if (v.source == VoiceSource.PIPER) "Piper" else "Kokoro"
        if (v.label.contains(engineName, ignoreCase = true)) v.label else "${v.label} ($engineName)"
    } ?: stringResource(R.string.reader_narrator_voice_default)
    FlowFolioGhostButton(text = label, onClick = { open = true })
    FlowFolioMenu(expanded = open, onDismissRequest = { open = false }) {
        voices.forEach { v ->
            val engineLabel = if (v.source == VoiceSource.PIPER) "Piper · sherpa-onnx" else "Kokoro · sherpa-onnx"
            DropdownMenuItem(
                text = {
                    Column {
                        Text(v.label)
                        Text(
                            "${v.name} · $engineLabel",
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

/** The narrator's speeds (ADR-0052, the mock's `.mini` row). */
internal val SPEEDS = listOf(0.8f, 1f, 1.25f, 1.5f, 2f)

/** "0.8×", "1×", "1.25×": no trailing zeros. */
internal fun speedLabel(rate: Float): String =
    java.math.BigDecimal(rate.toString()).stripTrailingZeros().toPlainString() + "×"
private const val PERCENT_SCALE = 100
private const val NOTICE_DURATION_MS = 2_500L
private const val ERROR_NOTICE_DURATION_MS = 3_500L
