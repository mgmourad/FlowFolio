package com.flowfolio.feature.reader

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.flowfolio.core.voice.PcmAudio
import com.flowfolio.core.voice.VoiceEngine
import com.flowfolio.core.voice.VoiceSpeakers

/**
 * Narrator Mode's neural voice (Phase 6 M3): Kokoro via sherpa-onnx. [speak] streams the synthesis so the
 * first word plays as soon as it is ready and the next page starts the moment the previous one ends; [stop]
 * interrupts a generation in flight, so pause takes effect at once. The engine is loaded once per reader
 * visit; [release] frees it.
 */
internal class Narrator(private val engine: VoiceEngine) {
    private var speakerId: Int = VoiceSpeakers.defaults.first().id
    private var rate: Float = 1f

    @Volatile
    private var generation = 0

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    private var worker: Thread? = null

    fun setRate(value: Float) {
        rate = value
    }

    fun setVoice(name: String) {
        VoiceSpeakers.idOf(name)?.let { speakerId = it }
    }

    @Suppress("TooGenericExceptionCaught") // a synthesis/playback failure must never crash the reader
    fun speak(text: String, done: () -> Unit) {
        val gen = ++generation
        val thread = Thread {
            try {
                if (gen == generation) stream(text, gen)
            } catch (t: Throwable) {
                Log.e(TAG, "narrator synthesis failed", t)
            } finally {
                // Report completion on the main thread; advanceOrStop touches the reader's layout state.
                if (gen == generation) mainHandler.post(done)
            }
        }
        worker = thread
        thread.start()
    }

    fun stop() {
        generation++
        runCatching { track?.pause() }
        runCatching { track?.flush() }
    }

    fun release() {
        stop()
        worker?.join()
        engine.release()
    }

    private fun stream(text: String, gen: Int) {
        val minBuffer = AudioTrack.getMinBufferSize(
            engine.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(MIN_BUFFER_FALLBACK)
        val t = buildTrack(engine.sampleRate, minBuffer)
        track = t
        try {
            t.play()
            engine.stream(text, speakerId, rate) { samples ->
                if (gen != generation) {
                    false // stop generating; the reader has paused or left
                } else {
                    val pcm = PcmAudio.toPcm16(samples)
                    t.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                    gen == generation
                }
            }
            // WRITE_BLOCKING returns once a chunk is enqueued; let the buffered tail play out first.
            Thread.sleep(minBuffer * MILLIS_PER_SECOND / (BYTES_PER_SAMPLE * engine.sampleRate))
        } finally {
            runCatching { t.stop() }
            t.release()
            if (track === t) track = null
        }
    }

    private fun buildTrack(sampleRate: Int, minBuffer: Int): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(minBuffer)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    private companion object {
        const val TAG = "Narrator"
        const val MIN_BUFFER_FALLBACK = 4096
        const val BYTES_PER_SAMPLE = 2L
        const val MILLIS_PER_SECOND = 1000L

        val mainHandler = Handler(Looper.getMainLooper())
    }
}
