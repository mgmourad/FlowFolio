package com.flowfolio.core.voice

/**
 * The voice packs Narrator Mode can download. One pack (Kokoro multi-lang int8) serves both default
 * voices — `af_heart` and `am_michael` are speakers of the same model, so a single download gives both.
 */
object VoicePacks {
    /**
     * `kokoro-multi-lang-v1_0` int8, served directly from sherpa-onnx's public `tts-models` release
     * (tar.bz2). Only the five files narration needs are extracted; the archive's `espeak-ng-data/` and
     * Chinese dicts/FSTs are skipped. The digest pins the archive.
     */
    val kokoroMultiLang = VoicePack(
        archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" +
            "kokoro-int8-multi-lang-v1_0.tar.bz2",
        archiveSha256 = "72c347230107ef8a31098ec4d468a605244beef5f1ed00db97efa13518800653",
        requiredFiles = listOf(
            "model.int8.onnx",
            "voices.bin",
            "tokens.txt",
            "lexicon-us-en.txt",
            "lexicon-zh.txt"
        ),
        archiveFileName = "voice-pack.tar.bz2",
        format = VoicePackFormat.TAR_BZ2
    )
}
