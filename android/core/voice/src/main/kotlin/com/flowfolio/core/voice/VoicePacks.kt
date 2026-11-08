package com.flowfolio.core.voice

/**
 * The voice packs Narrator Mode can download. Kokoro is one model serving both default voices (and 51
 * more); Piper is two opt-in per-voice models (Amy, Alan) that share one lexicon.
 */
object VoicePacks {
    /** `kokoro-multi-lang-v1_0` int8, served from sherpa-onnx's public `tts-models` release. */
    val kokoroMultiLang = VoicePack(
        archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" +
            "kokoro-int8-multi-lang-v1_0.tar.bz2",
        archiveSha256 = "72c347230107ef8a31098ec4d468a605244beef5f1ed00db97efa13518800653",
        archive = VoiceArchive("voice-pack.tar.bz2", VoicePackFormat.TAR_BZ2),
        requiredFiles = listOf(
            "model.int8.onnx",
            "voices.bin",
            "tokens.txt",
            "lexicon-us-en.txt",
            "lexicon-zh.txt"
        )
    )

    private val PIPER_LEXICON = VoiceLexicon(
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/lexicon-en-us.txt",
        sha256 = "8b67ae549a465eee78f92ecb74644ad90c3c58ce65f19c68baf85878a10ef73a"
    )

    /** The Piper voices (VITS), keyed by [Voice.modelKey]; each downloads its own int8 model + the shared lexicon. */
    val piper: Map<String, VoicePack> = mapOf(
        "amy" to VoicePack(
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" +
                "vits-piper-en_US-amy-medium-int8.tar.bz2",
            archiveSha256 = "bd23c0aa629eb3719448582f45ede49e8fa6a679061fed5eab16a6a6fd8e7e82",
            archive = VoiceArchive("piper-amy.tar.bz2", VoicePackFormat.TAR_BZ2),
            requiredFiles = listOf("en_US-amy-medium.onnx", "tokens.txt"),
            lexicon = PIPER_LEXICON,
            modelName = "en_US-amy-medium.onnx"
        ),
        "alan" to VoicePack(
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" +
                "vits-piper-en_GB-alan-medium-int8.tar.bz2",
            archiveSha256 = "f0b2526c9600657141ccbbe412f6159dceedab5bab4ba4e8857d9443acb50de4",
            archive = VoiceArchive("piper-alan.tar.bz2", VoicePackFormat.TAR_BZ2),
            requiredFiles = listOf("en_GB-alan-medium.onnx", "tokens.txt"),
            lexicon = PIPER_LEXICON,
            modelName = "en_GB-alan-medium.onnx"
        )
    )
}
