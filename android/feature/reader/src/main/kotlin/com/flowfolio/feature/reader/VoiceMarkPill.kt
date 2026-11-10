package com.flowfolio.feature.reader

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.Waveform
import com.flowfolio.core.designsystem.asClock
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.model.VoiceNote
import kotlinx.coroutines.delay

/**
 * A VoiceMark being recorded or played back (owner, M5): one pill over the page holding the record button, the
 * sound itself drawn as a waveform, and a bin.
 *
 * The record button pulses while it is listening, which is the only thing on the page that moves then, so there is
 * no mistaking a live microphone for a stopped one. The bin throws the recording away and closes the pill; the
 * highlight it was spoken against stays, since the words were still worth keeping.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod") // the wiring of one pill: recorder, player and permission
@Composable
internal fun BoxScope.VoiceMarkLayer(annotating: Annotating, annotationId: Long) {
    val p = FlowFolio.palette
    val vm = annotating.annotations
    val annotation = annotating.all.firstOrNull { it.id == annotationId }
    val levels by vm.voiceLevels.collectAsStateWithLifecycle()
    val playingPath by vm.voicePlaying.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    val recordedMillis by vm.recordedMillis.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(playingPath) {
        while (playingPath != null) {
            tick++
            delay(TICK_MS)
        }
    }
    val close = { annotating.voiceMarking = null }
    val start = { vm.startVoice(annotating.title, annotation?.page ?: 0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start()
    }
    SelectionCatcher {
        if (recording) vm.cancelVoice()
        close()
    }
    val saved = annotation?.voice
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 18.dp, vertical = 28.dp)
            .widthIn(max = PILL_WIDTH)
            .fillMaxWidth()
            .glass(RoundedCornerShape(30.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(ReaderTestTags.VOICEMARK_PILL)
    ) {
        VoiceMarkRow(
            recording = recording,
            levels = levels,
            saved = saved,
            // While recording the clock counts up; once stopped it shows how long the mark runs.
            millis = if (recording) recordedMillis else saved?.millis ?: 0L,
            progress = when {
                recording -> 1f

                saved != null && playingPath == saved.path && tick >= 0 ->
                    (vm.playbackMillis / saved.millis.toFloat()).coerceIn(0f, 1f)

                else -> 0f
            },
            playing = saved != null && playingPath == saved.path,
            onButton = {
                when {
                    recording -> vm.stopVoice(annotationId)

                    saved != null -> vm.playVoice(saved.path)

                    else -> {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) start() else ask.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            onDiscard = {
                if (recording) vm.cancelVoice() else vm.removeVoice(annotationId)
                close()
            }
        )
    }
}

/** What the pill holds: the button, the sound drawn, how long it runs, and the bin. */
@Composable
private fun VoiceMarkRow(
    recording: Boolean,
    levels: List<Float>,
    saved: VoiceNote?,
    millis: Long,
    progress: Float,
    playing: Boolean,
    onButton: () -> Unit,
    onDiscard: () -> Unit
) {
    val p = FlowFolio.palette
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RecordButton(recording, saved != null, playing, onButton)
        Waveform(
            levels = if (recording) levels else saved?.levels.orEmpty(),
            progress = progress,
            modifier = Modifier.weight(1f).height(WAVE_HEIGHT)
        )
        Text(millis.asClock(), style = FlowFolioType.rowDetail.merge(FlowFolio.hint))
        Box(
            Modifier
                .size(BIN)
                .clip(CircleShape)
                .clickable(onClick = onDiscard)
                .testTag(ReaderTestTags.VOICEMARK_DISCARD),
            contentAlignment = Alignment.Center
        ) {
            FlowFolioGlyph(
                Glyph.Trash,
                stringResource(R.string.reader_voicemark_discard),
                color = p.danger,
                size = 18.dp,
                glow = false
            )
        }
    }
}

/**
 * The pill's one button, which says what it will do (owner, M5 round): a microphone when there is nothing yet, a
 * square while it is listening, and play or pause once there is a VoiceMark to hear.
 */
@Composable
private fun RecordButton(recording: Boolean, hasMark: Boolean, playing: Boolean, onClick: () -> Unit) {
    val p = FlowFolio.palette
    val pulse = rememberInfiniteTransition(label = "voicemark")
    val beat by pulse.animateFloat(
        initialValue = 1f,
        targetValue = PULSE_TO,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_MS, easing = Motion.EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voicemarkBeat"
    )
    val scale = if (recording) beat else 1f
    Box(
        Modifier
            .size(BUTTON)
            .scale(scale)
            .clip(CircleShape)
            .background(if (recording) p.danger else p.ink, CircleShape)
            .clickable(onClick = onClick)
            .testTag(ReaderTestTags.VOICEMARK_RECORD),
        contentAlignment = Alignment.Center
    ) {
        val glyph = when {
            recording -> Glyph.Stop
            playing -> Glyph.Pause
            hasMark -> Glyph.Play
            else -> Glyph.Mic
        }
        val says = when {
            recording -> R.string.reader_voicemark_stop
            playing -> R.string.reader_voicemark_pause
            hasMark -> R.string.reader_voicemark_play
            else -> R.string.reader_voicemark_record
        }
        FlowFolioGlyph(glyph, stringResource(says), color = p.buttonInk, size = 20.dp, glow = false)
    }
}

private val PILL_WIDTH = 420.dp
private val BUTTON = 46.dp
private val BIN = 34.dp
private val WAVE_HEIGHT = 30.dp
private const val PULSE_MS = 620
private const val PULSE_TO = 1.14f
private const val TICK_MS = 120L
