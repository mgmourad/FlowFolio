package com.flowfolio.core.voice

import java.io.File

/** How a voice pack's archive is compressed. */
enum class VoicePackFormat { ZIP, TAR_BZ2 }

/** A voice pack's archive file: its local name and how it is compressed. */
data class VoiceArchive(val fileName: String, val format: VoicePackFormat)

/** A second download some packs need (Piper's shared English lexicon), pinned by its SHA-256. */
data class VoiceLexicon(val url: String, val sha256: String, val fileName: String = "lexicon-en-us.txt")

/**
 * A downloadable voice pack: an archive of the model files, its SHA-256, and the files that must exist
 * (non-empty) on disk for the pack to be usable. A [VoiceLexicon] is an optional second download. The URLs
 * and digests are fixed per release.
 */
@Suppress("LongParameterList") // a pack is its pinned facts, named at each use
class VoicePack(
    val archiveUrl: String,
    val archiveSha256: String,
    val archive: VoiceArchive,
    val requiredFiles: List<String>,
    val lexicon: VoiceLexicon? = null,
    val modelName: String = "model.int8.onnx",
    /**
     * The required files mirrored one by one (gzipped) on FlowFolio's own releases: fetched in parallel through
     * [PackFetch], with nothing to unpack afterwards. When set, the archive is not used.
     */
    val files: List<PackFile>? = null
) {
    /** True when every required file is present and non-empty in [dir]. */
    fun isReady(dir: File): Boolean = requiredFiles.all { File(dir, it).length() > 0L }

    /** True when [file] exists and its SHA-256 matches this pack's. */
    fun hasValidChecksum(file: File): Boolean = file.isFile && Sha256.of(file) == archiveSha256

    /** True when the pack needs no lexicon, or its lexicon is already present and verified in [dir]. */
    fun lexiconReady(dir: File): Boolean {
        val lex = lexicon ?: return true
        val file = File(dir, lex.fileName)
        return file.isFile && Sha256.of(file) == lex.sha256
    }
}
