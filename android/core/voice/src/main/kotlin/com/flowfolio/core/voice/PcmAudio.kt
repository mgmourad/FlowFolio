package com.flowfolio.core.voice

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.tan

/** PCM helpers for playing [VoiceEngine] output through an `AudioTrack`. */
object PcmAudio {
    /**
     * Converts normalized `[-1, 1]` float samples (what sherpa-onnx returns) to signed 16-bit PCM,
     * clipping any out-of-range sample to the full-scale limits.
     */
    fun toPcm16(samples: FloatArray): ShortArray = ShortArray(samples.size) { i ->
        (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
    }

    /**
     * Trims excess leading and trailing silence from [samples], filters high-frequency vocoder quantization hiss,
     * applies a smooth cosine fade at both edges, and appends [trailingPauseMs] of silence so consecutive segments
     * play seamlessly without artificial gaps or background hiss.
     */
    fun toTrimmedPcm16(samples: FloatArray, sampleRate: Int, trailingPauseMs: Int): ShortArray {
        val bounds = voicedBounds(samples, sampleRate) ?: return ShortArray(0)
        val first = bounds.first
        val last = bounds.last
        val voicedCount = last - first + 1
        val conditioned = if (sampleRate >= MIN_SPEECH_FILTER_RATE_HZ) {
            cleanVocoderBand(samples, first, voicedCount, sampleRate)
        } else {
            FloatArray(voicedCount) { i -> samples[first + i] }
        }
        val pauseSamples = (sampleRate * trailingPauseMs / MILLIS_PER_SECOND).coerceAtLeast(0)
        val fadeSamples = (sampleRate * FADE_MS / MILLIS_PER_SECOND)
            .coerceIn(1, (voicedCount / FADE_FRACTION_DIVISOR).coerceAtLeast(1))
        val out = ShortArray(voicedCount + pauseSamples)
        for (i in 0 until voicedCount) {
            val gain = edgeGain(i, voicedCount, fadeSamples)
            val sample = (conditioned[i] * gain).coerceIn(-1f, 1f)
            out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /**
     * Removes the high-frequency quantization hiss (> 8.2 kHz) and quiet-tail noise floor produced by 8-bit/medium
     * neural vocoders (Kokoro ISTFTNet and Piper VITS) using a 2-pole Butterworth low-pass filter and a soft
     * downward expander on near-silent regions.
     */
    private fun cleanVocoderBand(samples: FloatArray, offset: Int, count: Int, sampleRate: Int): FloatArray {
        val cutoff = LOWPASS_CUTOFF_HZ.coerceAtMost(sampleRate * NYQUIST_SAFETY_RATIO)
        val k = tan(PI * cutoff / sampleRate)
        val norm = 1.0 / (1.0 + BUTTERWORTH_Q * k + k * k)
        val b0 = (k * k * norm).toFloat()
        val b1 = (2.0 * b0).toFloat()
        val b2 = b0
        val a1 = (2.0 * (k * k - 1.0) * norm).toFloat()
        val a2 = ((1.0 - BUTTERWORTH_Q * k + k * k) * norm).toFloat()

        val out = FloatArray(count)
        var x1 = if (count > 0) samples[offset] else 0f
        var x2 = x1
        var y1 = x1
        var y2 = x1
        var envelope = 0f
        for (i in 0 until count) {
            val x0 = samples[offset + i]
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
            val level = abs(y0)
            envelope = if (level > envelope) {
                envelope + ENVELOPE_ATTACK * (level - envelope)
            } else {
                envelope + ENVELOPE_RELEASE * (level - envelope)
            }
            val expanderGain = if (envelope < NOISE_FLOOR_THRESHOLD) {
                (envelope / NOISE_FLOOR_THRESHOLD).let { it * it }
            } else {
                1f
            }
            out[i] = y0 * expanderGain
        }
        return out
    }

    private fun voicedBounds(samples: FloatArray, sampleRate: Int): IntRange? {
        var start = 0
        while (start < samples.size && abs(samples[start]) < SILENCE_THRESHOLD) start++
        if (start >= samples.size) return null
        var end = samples.lastIndex
        while (end > start && abs(samples[end]) < SILENCE_THRESHOLD) end--
        val margin = (sampleRate * EDGE_MARGIN_MS / MILLIS_PER_SECOND).coerceAtLeast(0)
        val first = (start - margin).coerceAtLeast(0)
        val last = (end + margin).coerceAtMost(samples.lastIndex)
        return first..last
    }

    private fun edgeGain(index: Int, total: Int, fade: Int): Float = when {
        index < fade -> HALF * (1f - cos(PI.toFloat() * index / fade))
        index >= total - fade -> HALF * (1f - cos(PI.toFloat() * (total - 1 - index) / fade))
        else -> 1f
    }

    private const val HALF = 0.5f
    private const val SILENCE_THRESHOLD = 0.0035f
    private const val NOISE_FLOOR_THRESHOLD = 0.009f
    private const val ENVELOPE_ATTACK = 0.35f
    private const val ENVELOPE_RELEASE = 0.015f
    private const val LOWPASS_CUTOFF_HZ = 8200.0
    private const val NYQUIST_SAFETY_RATIO = 0.42
    private const val BUTTERWORTH_Q = 1.41421356237
    private const val MIN_SPEECH_FILTER_RATE_HZ = 16_000
    private const val EDGE_MARGIN_MS = 12
    private const val FADE_MS = 6
    private const val FADE_FRACTION_DIVISOR = 4
    private const val MILLIS_PER_SECOND = 1000
}
