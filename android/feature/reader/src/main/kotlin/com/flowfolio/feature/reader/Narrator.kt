package com.flowfolio.feature.reader

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.flowfolio.core.voice.PcmAudio
import com.flowfolio.core.voice.VoiceEngine
import com.flowfolio.core.voice.VoiceSpeakers

/**
 * Narrator Mode's neural voice (Phase 6 M3): Kokoro via sherpa-onnx. [speak] synthesizes the text on a
 * worker thread and plays it through an [AudioTrack], calling [done] when the audio finishes. The engine
 * is loaded once per reader visit; [release] frees it.
 *
 * [stop] advances a generation counter so a synthesis already in flight finishes silently instead of
 * reporting done and turning a page the reader has already left.
 */
internal class Narrator(private val engine: VoiceEngine) {
    private var speakerId: Int = VoiceSpeakers.defaults.first().id
    private var rate: Float = 1f
    private var generation = 0

    @Volatile
    private var track: AudioTrack? = null

    fun setRate(value: Float) {
        rate = value
    }

    fun setVoice(name: String) {
        VoiceSpeakers.idOf(name)?.let { speakerId = it }
    }

    fun speak(text: String, done: () -> Unit) {
        val gen = ++generation
        Thread {
            try {
                val audio = engine.generate(text, speakerId, rate)
                if (gen == generation) play(audio.sampleRate, PcmAudio.toPcm16(audio.samples))
            } finally {
                if (gen == generation) done()
            }
        }.start()
    }

    fun stop() {
        generation++
        track?.pause()
        track?.flush()
    }

    fun release() {
        stop()
        engine.release()
    }

    private fun play(sampleRate: Int, pcm: ShortArray) {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
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
        } finally {
            t.stop()
            t.release()
            track = null
        }
    }
}
