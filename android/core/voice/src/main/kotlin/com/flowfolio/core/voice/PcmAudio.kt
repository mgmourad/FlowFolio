package com.flowfolio.core.voice

/** PCM helpers for playing [VoiceEngine] output through an `AudioTrack`. */
object PcmAudio {
    /**
     * Converts normalized `[-1, 1]` float samples (what sherpa-onnx returns) to signed 16-bit PCM,
     * clipping any out-of-range sample to the full-scale limits.
     */
    fun toPcm16(samples: FloatArray): ShortArray = ShortArray(samples.size) { i ->
        (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
    }
}
