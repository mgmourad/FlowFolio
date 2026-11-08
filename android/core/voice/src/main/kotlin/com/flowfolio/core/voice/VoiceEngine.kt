package com.flowfolio.core.voice

import java.io.File

/** One synthesized utterance: normalized `[-1, 1]` float samples at [sampleRate] Hz. */
data class VoiceAudio(val samples: FloatArray, val sampleRate: Int)

/**
 * Explicit [Function1] implementation for sherpa-onnx's JNI callback in `OfflineTts.generateWithCallbackImpl`.
 *
 * `libsherpa-onnx-jni.so` looks up `invoke([F)Ljava/lang/Integer;` via `GetMethodID`. Kotlin 2.x compiles
 * trailing lambdas with `invokedynamic` (`LambdaMetafactory`), which D8 desugars into a class that only has
 * `invoke(Ljava/lang/Object;)Ljava/lang/Object;`, triggering a fatal JNI `NoSuchMethodError` at runtime.
 * Returning nullable [Int?] forces `kotlinc` to emit the specialized `public final Integer invoke(float[])`
 * method alongside the generic bridge.
 */
internal class ChunkCallback(private val onChunk: (FloatArray) -> Boolean) : Function1<FloatArray, Int?> {
    override fun invoke(samples: FloatArray): Int? = if (onChunk(samples)) 1 else 0
}

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
    @Suppress("UNCHECKED_CAST")
    fun stream(text: String, speakerId: Int, speed: Float, onChunk: (FloatArray) -> Boolean) {
        val callback = ChunkCallback(onChunk) as (FloatArray) -> Int
        tts.generateWithCallback(text, sid = speakerId, speed = speed, callback = callback)
    }

    fun release() = tts.release()

    companion object {
        private const val MIN_THREADS = 2
        private const val MAX_THREADS = 6
        private const val SILENCE_SCALE = 0.12f
        private const val PIPER_NOISE_SCALE = 0.36f
        private const val PIPER_NOISE_SCALE_W = 0.58f
        private const val MIN_STEM_LENGTH = 3
        private val SIBILANT_ENDINGS = setOf("s", "z", "ʃ", "ʒ", "ʧ", "ʤ")
        private val VOICELESS_ENDINGS = setOf("p", "t", "k", "f", "θ")
        private val ALVEOLAR_STOP_ENDINGS = setOf("t", "d")
        private val PAST_VOICELESS_ENDINGS = setOf("p", "k", "f", "θ", "s", "ʃ", "ʧ")

        /**
         * Loads the `kokoro-multi-lang-v1_0` int8 model from [modelDir]. The multi-lang model needs both
         * lexicons, so they are passed by their absolute paths (the helper leaves comma lists untouched).
         */
        fun load(modelDir: String): VoiceEngine {
            val inflected = ensureInflectedLexicon(File(modelDir))
            val lexicons = buildString {
                append("$modelDir/lexicon-us-en.txt")
                if (inflected != null) append(",${inflected.absolutePath}")
                append(",$modelDir/lexicon-zh.txt")
            }
            val config = com.k2fsa.sherpa.onnx.getOfflineTtsConfig(
                modelDir = modelDir,
                modelName = "model.int8.onnx",
                voices = "voices.bin",
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(MIN_THREADS, MAX_THREADS),
                lexicon = lexicons
            )
            config.silenceScale = SILENCE_SCALE
            return VoiceEngine(com.k2fsa.sherpa.onnx.OfflineTts(assetManager = null, config = config))
        }

        /** Loads a Piper (VITS) voice: a per-voice model + tokens + the shared English lexicon, all in [modelDir]. */
        fun loadPiper(modelDir: String, modelName: String): VoiceEngine {
            val lexiconName = ensurePiperCompleteLexicon(File(modelDir))
            val config = com.k2fsa.sherpa.onnx.getOfflineTtsConfig(
                modelDir = modelDir,
                modelName = modelName,
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(MIN_THREADS, MAX_THREADS),
                lexicon = lexiconName
            )
            config.model.vits.noiseScale = PIPER_NOISE_SCALE
            config.model.vits.noiseScaleW = PIPER_NOISE_SCALE_W
            config.silenceScale = SILENCE_SCALE
            return VoiceEngine(com.k2fsa.sherpa.onnx.OfflineTts(assetManager = null, config = config))
        }

        private fun ensurePiperCompleteLexicon(dir: File): String = runCatching {
            val baseFile = File(dir, "lexicon-en-us.txt")
            if (!baseFile.isFile) return "lexicon-en-us.txt"
            val completeFile = File(dir, "lexicon-en-us-complete.txt")
            if (!completeFile.isFile || completeFile.length() <= baseFile.length()) {
                val tmpFile = File(dir, "lexicon-en-us-complete.tmp")
                baseFile.copyTo(tmpFile, overwrite = true)
                tmpFile.appendText("\nor || ɔː ɹ\n")
                tmpFile.renameTo(completeFile)
            }
            completeFile.name
        }.getOrDefault("lexicon-en-us.txt")

        /**
         * `kokoro-int8-multi-lang-v1_0`'s `lexicon-us-en.txt` omits regular `-s`/`-es` inflections for many
         * base nouns and verbs (`devices`, `benefits`, `individuals`, `passwords`, `reasons`, `elements`, etc.).
         * Generates a supplemental lexicon once on disk from `lexicon-us-en.txt` for any word whose plural is missing.
         */
        internal fun ensureInflectedLexicon(dir: File): File? = runCatching {
            val baseFile = File(dir, "lexicon-us-en.txt")
            if (!baseFile.isFile) return null
            val outFile = File(dir, "lexicon-us-en-inflected-v2.txt")
            if (outFile.isFile && outFile.length() > 0L) return outFile
            val entries = readBaseLexicon(baseFile)
            val tmpFile = File(dir, "lexicon-us-en-inflected-v2.tmp")
            tmpFile.bufferedWriter().use { writer ->
                writeInflectedEntries(entries, writer)
            }
            tmpFile.renameTo(outFile)
            outFile
        }.getOrNull()

        private fun readBaseLexicon(baseFile: File): LinkedHashMap<String, String> {
            val entries = LinkedHashMap<String, String>()
            baseFile.useLines { lines ->
                for (line in lines) {
                    val space = line.indexOf(' ')
                    if (space > 0) {
                        val word = line.substring(0, space).lowercase()
                        val phonemes = line.substring(space + 1).trim()
                        entries.putIfAbsent(word, phonemes)
                    }
                }
            }
            return entries
        }

        private fun writeInflectedEntries(entries: Map<String, String>, writer: java.io.Writer) {
            for ((word, phonemes) in entries) {
                if (!isInflectableStem(word)) continue
                val lastToken = phonemes.substringAfterLast(' ')
                val pluralSuffix = when (lastToken) {
                    in SIBILANT_ENDINGS -> "ᵻ z"
                    in VOICELESS_ENDINGS -> "s"
                    else -> "z"
                }
                for (plural in pluralCandidates(word)) {
                    if (plural !in entries) {
                        writer.write("$plural $phonemes $pluralSuffix\n")
                    }
                }
                val pastSuffix = when (lastToken) {
                    in ALVEOLAR_STOP_ENDINGS -> "ᵻ d"
                    in PAST_VOICELESS_ENDINGS -> "t"
                    else -> "d"
                }
                for (past in pastCandidates(word)) {
                    if (past !in entries) {
                        writer.write("$past $phonemes $pastSuffix\n")
                    }
                }
            }
        }

        private fun isInflectableStem(word: String): Boolean =
            word.length >= MIN_STEM_LENGTH && word.all { it in 'a'..'z' } && !word.endsWith('s')

        private fun pluralCandidates(word: String): List<String> = when {
            word.endsWith('e') -> listOf("${word}s")

            word.endsWith("ch") || word.endsWith("sh") || word.endsWith('x') || word.endsWith('z') ->
                listOf("${word}es")

            word.endsWith('y') && word[word.length - 2] !in "aeiou" ->
                listOf("${word.dropLast(1)}ies", "${word}s")

            else -> listOf("${word}s")
        }

        private fun pastCandidates(word: String): List<String> = when {
            word.endsWith('e') -> listOf("${word}d")
            word.endsWith('y') && word[word.length - 2] !in "aeiou" -> listOf("${word.dropLast(1)}ied")
            else -> listOf("${word}ed")
        }
    }
}
