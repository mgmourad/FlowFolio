package com.flowfolio.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.model.UserSettings
import com.flowfolio.feature.reader.riffle.RiffleFeedback
import com.flowfolio.feature.reader.riffle.RiffleSounds

/**
 * The reader's paper sounds (ADR-0025), each with its own setting and loudness: the riffle while the scrubber's ribbon
 * drags, and a page flip for every turn (curl or slide, by tap or swipe) and every landing. Scroll has no turns, so no
 * flips.
 */
internal class ReaderSounds(private val sounds: RiffleSounds) {
    /** The latest settings; updated after each composition, so reading them never recomposes anything. */
    var settings = UserSettings()
        set(value) {
            field = value
            sounds.riffleVolume = value.riffleVolume
            sounds.flipVolume = value.turnVolume
        }

    fun riffle(feedback: RiffleFeedback) {
        if (settings.riffleSound) sounds.play(feedback)
    }

    fun stopRiffle() = sounds.stopRiffle()

    /** A page turning toward the end of the book ([forward]) or back toward its start. */
    fun turn(forward: Boolean) {
        if (settings.turnSound) sounds.flip(forward)
    }

    /** The flip at the turn sound's loudness, whatever its switch says: lets its volume slider be heard. */
    fun previewTurn() = sounds.flip(forward = true)

    /** A moment of the riffle at its loudness, for its volume slider. */
    fun previewRiffle() = sounds.previewRiffle()

    fun release() = sounds.release()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The open book's sounds, for the pages, the bars and the "Aa" panel; null outside the reader. */
internal val LocalReaderSounds = staticCompositionLocalOf<ReaderSounds?> { null }

@Composable
internal fun rememberReaderSounds(viewModel: ReaderViewModel): ReaderSounds {
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sounds = remember { ReaderSounds(RiffleSounds(context)) }
    SideEffect { sounds.settings = settings }
    DisposableEffect(sounds) { onDispose { sounds.release() } }
    // In the reader the volume keys set the page sounds' volume (the media stream, ADR-0032), not the ringer's.
    DisposableEffect(context) {
        val activity = context.findActivity()
        val before = activity?.volumeControlStream
        activity?.volumeControlStream = AudioManager.STREAM_MUSIC
        onDispose { if (activity != null && before != null) activity.volumeControlStream = before }
    }
    return sounds
}
