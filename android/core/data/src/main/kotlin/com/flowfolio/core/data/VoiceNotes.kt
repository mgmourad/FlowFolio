package com.flowfolio.core.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import com.flowfolio.core.model.VoiceNote
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Records a VoiceMark. An interface because the thing it drives is a microphone: tests have none, so they stand in
 * their own (owner, M5 round 3 -- this is how the pill's clock is checked without one).
 */
interface VoiceRecorder {
    /** The loudnesses heard so far, 0 to 1, for drawing the live waveform. */
    val levels: StateFlow<List<Float>>

    /** Whether a recording is running. */
    val recording: Boolean

    /** Starts recording a note for [bookTitle] on [page]. Returns false when the recorder could not start. */
    fun start(bookTitle: String, page: Int): Boolean

    /** Takes one loudness reading, to be called while the recording runs. */
    fun sample()

    /** Stops and keeps the recording; null when it was too short to be meant. */
    fun stop(): VoiceNote?

    /** Stops and throws the recording away. */
    fun cancel()
}

/**
 * Records a spoken note against a highlight (owner, M5).
 *
 * The recording is AAC in an MPEG-4 container, saved as `.m4a`. The owner asked for `.mp3`; Android's
 * [MediaRecorder] has no MP3 encoder, and adding one would mean shipping a native encoder for a file nobody leaves
 * the app with. The name is otherwise exactly as asked: `<book title>-page-<page>-<when>.m4a`.
 *
 * Loudness is sampled as the recording runs ([levels]) and kept with the note, because reading the levels back out
 * of the file later would mean decoding all of it to draw one small waveform.
 */
@Singleton
class AndroidVoiceRecorder @Inject constructor(@ApplicationContext private val context: Context) : VoiceRecorder {
    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private var startedAt: Long = 0L
    private val _levels = MutableStateFlow<List<Float>>(emptyList())

    override val levels: StateFlow<List<Float>> = _levels.asStateFlow()

    override val recording: Boolean get() = recorder != null

    @Synchronized
    @Suppress("ReturnCount") // guards: already recording, and a recorder that would not start
    override fun start(bookTitle: String, page: Int): Boolean {
        if (recorder != null) return false
        val dir = File(context.filesDir, VOICE_DIR).apply { mkdirs() }
        val file = File(dir, fileName(bookTitle, page))
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        val ok = runCatching {
            device.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(SAMPLE_RATE)
                setAudioEncodingBitRate(BIT_RATE)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        }.isSuccess
        if (!ok) {
            runCatching { device.release() }
            runCatching { file.delete() }
            return false
        }
        recorder = device
        target = file
        startedAt = System.currentTimeMillis()
        _levels.value = emptyList()
        return true
    }

    /**
     * Takes one loudness reading. The caller ticks this while recording, so the waveform grows at whatever rate
     * suits the screen rather than one this class invents.
     */
    @Synchronized
    override fun sample() {
        val device = recorder ?: return
        val amplitude = runCatching { device.maxAmplitude }.getOrDefault(0)
        val level = min(1f, amplitude / MAX_AMPLITUDE)
        _levels.value = (_levels.value + level).takeLast(MAX_SAMPLES)
    }

    /** Stops and keeps the recording, or returns null when nothing usable was captured. */
    @Synchronized
    @Suppress("ReturnCount") // guards: nothing recording, and nothing worth keeping
    override fun stop(): VoiceNote? {
        val device = recorder ?: return null
        val file = target
        val kept = runCatching { device.stop() }.isSuccess
        runCatching { device.release() }
        recorder = null
        target = null
        val millis = System.currentTimeMillis() - startedAt
        val worthKeeping = kept && file != null && file.exists() && millis >= MIN_MILLIS
        if (!worthKeeping) {
            runCatching { file?.delete() }
            return null
        }
        return VoiceNote(path = file.absolutePath, millis = millis, levels = _levels.value)
    }

    /** Stops and throws the recording away. */
    @Synchronized
    override fun cancel() {
        val device = recorder ?: return
        runCatching { device.stop() }
        runCatching { device.release() }
        runCatching { target?.delete() }
        recorder = null
        target = null
        _levels.value = emptyList()
    }

    /** `<book title>-page-<page>-<yyyyMMdd-HHmmss>.m4a`, with anything a file system dislikes taken out. */
    private fun fileName(bookTitle: String, page: Int): String {
        val safe = bookTitle.map { if (it.isLetterOrDigit() || it == ' ') it else '-' }
            .joinToString("")
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(MAX_TITLE)
            .ifEmpty { "book" }
        val stamp = java.text.SimpleDateFormat(STAMP, Locale.US).format(java.util.Date())
        return "$safe-page-$page-$stamp.m4a"
    }

    private companion object {
        const val VOICE_DIR = "voice"
        const val SAMPLE_RATE = 44_100
        const val BIT_RATE = 64_000
        const val MAX_AMPLITUDE = 32_767f
        const val MAX_SAMPLES = 600
        const val MIN_MILLIS = 400L
        const val MAX_TITLE = 60
        const val STAMP = "yyyyMMdd-HHmmss"
    }
}

/**
 * Plays a voice note back (owner, M5): one note at a time, so starting another stops the one running.
 *
 * It reports where it has got to through [positionMillis], which the playback bar follows to light its waveform.
 */
@Singleton
class VoicePlayer @Inject constructor() {
    private var player: MediaPlayer? = null
    private val _playing = MutableStateFlow<String?>(null)

    /** The path of the note now playing, or null when nothing is. */
    val playing: StateFlow<String?> = _playing.asStateFlow()

    /** How far into the note playback has got, in milliseconds. */
    val positionMillis: Int get() = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)

    /** Starts [path] from the beginning, stopping whatever was playing. */
    @Synchronized
    fun play(path: String, onEnd: () -> Unit = {}) {
        stop()
        val device = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    stop()
                    onEnd()
                }
                start()
            }
        }.getOrNull()
        player = device
        _playing.value = if (device != null) path else null
    }

    /** Pauses without losing the place. */
    @Synchronized
    fun pause() {
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
        _playing.value = null
    }

    /** Stops and lets the note go. */
    @Synchronized
    fun stop() {
        runCatching {
            player?.apply {
                if (isPlaying) stop()
                release()
            }
        }
        player = null
        _playing.value = null
    }
}
