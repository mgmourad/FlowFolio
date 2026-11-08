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
 * Narrator Mode's neural voice (Phase 6 M3): Kokoro via sherpa-onnx. [speak] synthesizes the text on a
 * worker thread and plays it through an [AudioTrack], then calls [done] on the main thread when the audio
 * has finished playing. The engine is loaded once per reader visit; [release] frees it.
 *
 * A generation counter keeps a [stop] from reporting done for an utterance the reader has left, and a
 * [release] joins the worker before freeing the native engine so it is never freed mid-synthesis.
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
                val audio = engine.generate(text, speakerId, rate)
                if (gen == generation) play(audio.sampleRate, PcmAudio.toPcm16(audio.samples))
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

    private fun play(sampleRate: Int, pcm: ShortArray) {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(MIN_BUFFER_FALLBACK)
        val t = AudioTrack.Builder()
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
        track = t
        try {
            t.play()
            t.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            // WRITE_BLOCKING returns once the data is enqueued; let the buffered tail play out first.
            Thread.sleep(minBuffer * MILLIS_PER_SECOND / (BYTES_PER_SAMPLE * sampleRate))
        } finally {
            runCatching { t.stop() }
            t.release()
            if (track === t) track = null
        }
    }

    private companion object {
        const val TAG = "Narrator"
        const val MIN_BUFFER_FALLBACK = 4096
        const val BYTES_PER_SAMPLE = 2L
        const val MILLIS_PER_SECOND = 1000L

        val mainHandler = Handler(Looper.getMainLooper())
    }
}
