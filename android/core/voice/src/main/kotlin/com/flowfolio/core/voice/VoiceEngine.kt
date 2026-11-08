package com.flowfolio.core.voice

/** One synthesized utterance: normalized `[-1, 1]` float samples at [sampleRate] Hz. */
data class VoiceAudio(val samples: FloatArray, val sampleRate: Int)

/**
 * Offline Kokoro text-to-speech, wrapping sherpa-onnx's `OfflineTts`. The model files
 * (`model.int8.onnx`, `voices.bin`, `tokens.txt`, the lexicons) are loaded from [modelDir] on disk, so
 * they can be downloaded to app storage at runtime rather than bundled. Not thread-safe: one engine per
 * reader visit, used from a single worker.
 */
class VoiceEngine private constructor(private val tts: com.k2fsa.sherpa.onnx.OfflineTts) {
    val sampleRate: Int get() = tts.sampleRate()

    val numSpeakers: Int get() = tts.numSpeakers()

    /** Synthesizes [text] in the given speaker, at [speed] (1.0 = natural). */
    fun generate(text: String, speakerId: Int, speed: Float): VoiceAudio {
        val audio = tts.generate(text, sid = speakerId, speed = speed)
        return VoiceAudio(audio.samples, audio.sampleRate)
    }

    /**
     * Streams [text]'s synthesis in chunks to [onChunk] instead of returning it whole, so playback can start
     * before the whole page is generated (and a page can stop mid-generation). [onChunk] answers true to keep
     * going and false to stop.
     */
    fun stream(text: String, speakerId: Int, speed: Float, onChunk: (FloatArray) -> Boolean) {
        tts.generateWithCallback(text, sid = speakerId, speed = speed) { samples ->
            if (onChunk(samples)) 1 else 0
        }
    }

    fun release() = tts.release()

    companion object {
        /**
         * Loads the `kokoro-multi-lang-v1_0` int8 model from [modelDir]. The multi-lang model needs both
         * lexicons, so they are passed by their absolute paths (the helper leaves comma lists untouched).
         */
        fun load(modelDir: String): VoiceEngine {
            val config = com.k2fsa.sherpa.onnx.getOfflineTtsConfig(
                modelDir = modelDir,
                modelName = "model.int8.onnx",
                voices = "voices.bin",
                lexicon = "$modelDir/lexicon-us-en.txt,$modelDir/lexicon-zh.txt"
            )
            return VoiceEngine(com.k2fsa.sherpa.onnx.OfflineTts(assetManager = null, config = config))
        }
    }
}
