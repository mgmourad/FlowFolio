package com.flowfolio.core.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.core.net.toUri
import com.flowfolio.core.model.Ambient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the Reading Atmosphere's ambience behind the page (owner, M5).
 *
 * Built on [MediaPlayer] rather than a media library: one looping track with a volume is all this needs, and the
 * working agreement asks before a new dependency earns its place. Like the paper sounds (ADR-0032) it plays on the
 * media stream, so it follows the volume keys and ducks for calls and alarms rather than fighting them.
 *
 * [Ambient.SILENCE] releases the player instead of playing a silent track: an ambience nobody chose should cost no
 * battery. Looping uses `isLooping`, which leaves a short seam at the join; the recordings are long and even enough
 * that it does not read as a click, and a crossfading pair of players would cost twice the memory for it.
 */
@Singleton
class AmbientPlayer @Inject constructor(@ApplicationContext private val context: Context) {
    private var player: MediaPlayer? = null
    private var current: Ambient = Ambient.SILENCE

    /** What is playing, by ambience name or sound address, so the same sound is never restarted. */
    private var playingKey: String? = null

    /** What is playing now, so the reader and Settings agree without asking the player. */
    val playing: Ambient get() = current

    /**
     * Plays [ambient] at [volume] (0 to 1), looping. Asking for what is already playing only moves the volume, so a
     * slider does not restart the track under the reader's hands.
     *
     * Answers whether a sound is playing now, which is how an address of the reader's own is told to have worked:
     * a stream that cannot be reached leaves nothing playing (owner, M5 round).
     */
    @Synchronized
    @Suppress("ReturnCount") // the two early exits are guards; nesting them would read worse
    fun play(ambient: Ambient, volume: Float, soundUri: String? = null): Boolean {
        val wanted = soundUri ?: ambient.name
        if (soundUri == null && ambient == Ambient.SILENCE) {
            stop()
            return false
        }
        if (wanted == playingKey && player != null) {
            setVolume(volume)
            return true
        }
        release()
        player = runCatching {
            prepared(ambient, soundUri)?.apply {
                isLooping = true
                val level = volume.coerceIn(0f, 1f)
                setVolume(level, level)
                start()
            }
        }.getOrNull()
        current = if (player != null) ambient else Ambient.SILENCE
        playingKey = if (player != null) wanted else null
        return player != null
    }

    /**
     * The player for a bundled ambience or for a sound of the reader's own. A file they picked and an address of a
     * stream are both just data sources; a stream simply takes longer to prepare, which is why this is called off
     * the main thread by its caller's coroutine.
     */
    @Suppress("ReturnCount") // a sound of the reader's own, a bundled one, or none: three ways out
    private fun prepared(ambient: Ambient, soundUri: String?): MediaPlayer? {
        // Set before prepare, never after: MediaPlayer ignores attributes once a source is prepared, which left the
        // ambience off the media stream that ADR-0032 puts it on (found in the device log, M5 round 3).
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        if (soundUri != null) {
            return MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(context, soundUri.toUri())
                prepare()
            }
        }
        val resource = ambient.resource() ?: return null
        return MediaPlayer.create(context, resource, attributes, AudioManager.AUDIO_SESSION_ID_GENERATE)
    }

    /** Moves the ambience's loudness without restarting it. */
    @Synchronized
    fun setVolume(volume: Float) {
        val level = volume.coerceIn(0f, 1f)
        runCatching { player?.setVolume(level, level) }
    }

    /** Stops and lets the track go. */
    @Synchronized
    fun stop() {
        release()
        current = Ambient.SILENCE
    }

    private fun release() {
        runCatching {
            player?.apply {
                if (isPlaying) stop()
                release()
            }
        }
        player = null
        playingKey = null
    }
}

/** The bundled recording for an ambience; null for [Ambient.SILENCE], which has none. */
internal fun Ambient.resource(): Int? = when (this) {
    Ambient.SILENCE -> null
    Ambient.RAIN -> R.raw.ambient_rain
    Ambient.FIREPLACE -> R.raw.ambient_fireplace
    Ambient.OCEAN -> R.raw.ambient_ocean
    Ambient.FOREST -> R.raw.ambient_forest
    Ambient.STREAM -> R.raw.ambient_stream
    Ambient.NIGHT_GARDEN -> R.raw.ambient_night_garden
    Ambient.CAFE -> R.raw.ambient_cafe
    Ambient.BROWN_NOISE -> R.raw.ambient_brown_noise
}
