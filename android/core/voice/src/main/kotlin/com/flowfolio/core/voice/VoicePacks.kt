package com.flowfolio.core.voice

/**
 * The voice packs Narrator Mode can download. One pack (Kokoro multi-lang int8) serves both default
 * voices — `af_heart` and `am_michael` are speakers of the same model, so a single download gives both.
 */
object VoicePacks {
    /**
     * `kokoro-multi-lang-v1_0` int8, trimmed to the five files narration needs (the tarball's
     * espeak-ng-data, Chinese FSTs and jieba dict are unused and dropped). Re-hosted as a flat zip; the
     * URL is the FlowFolio GitHub release it is uploaded to (owner, 2026-11-08), and the digest pins it.
     */
    val kokoroMultiLang = VoicePack(
        archiveUrl = "https://github.com/mgmourad/FlowFolio/releases/download/voice-pack-v1/" +
            "kokoro-multi-lang-v1_0-int8.zip",
        archiveSha256 = "217f6fa5e02140963bbe8102d68015385d4355a8ccbfd57993bf0d0bd1bb2086",
        requiredFiles = listOf(
            "model.int8.onnx",
            "voices.bin",
            "tokens.txt",
            "lexicon-us-en.txt",
            "lexicon-zh.txt"
        )
    )
}
