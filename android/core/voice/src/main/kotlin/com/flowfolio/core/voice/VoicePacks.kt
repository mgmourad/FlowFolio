package com.flowfolio.core.voice

/**
 * The voice packs Narrator Mode can download. Kokoro is one model serving both default voices (and 51
 * more); Piper is two opt-in per-voice models (Amy, Alan) that share one lexicon.
 */
object VoicePacks {
    private const val RESOURCES = "https://github.com/mgmourad/FlowFolio/releases/download/resources-v1/"

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
        ),
        // The same five files, unchanged, gzipped one by one on FlowFolio's `resources-v1` (2026-11-12): 117 MB
        // instead of the 132 MB archive, fetched in parallel, and no bzip2 to unpack on the phone.
        files = listOf(
            PackFile(
                "model.int8.onnx",
                RESOURCES + "kokoro-model.int8.onnx.gz",
                "4eb22f73a9e3d06bdfb18dcf90de3474c0985211191d33410053cbdb88f29564",
                89_200_497,
                gzipped = true
            ),
            PackFile(
                "voices.bin",
                RESOURCES + "kokoro-voices.bin.gz",
                "65ae6355c1027ebc8de667c5f7a450033a3432a4e1366be3afb42e3321219783",
                25_920_342,
                gzipped = true
            ),
            PackFile(
                "tokens.txt",
                RESOURCES + "kokoro-tokens.txt.gz",
                "ff76c412ffd19a90f5dc5dca46b248a940347cb1a93f1001f9e6204a5f833b7e",
                487,
                gzipped = true
            ),
            PackFile(
                "lexicon-us-en.txt",
                RESOURCES + "kokoro-lexicon-us-en.txt.gz",
                "515062d0e8daef713935dd725562db122a2127b9bedc1403446b14bd1a2fde71",
                1_405_568,
                gzipped = true
            ),
            PackFile(
                "lexicon-zh.txt",
                RESOURCES + "kokoro-lexicon-zh.txt.gz",
                "51f3ac93a040eae0594265368eae883a1a23197b2888d6ba2687f8ffb6067f63",
                615_369,
                gzipped = true
            )
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
