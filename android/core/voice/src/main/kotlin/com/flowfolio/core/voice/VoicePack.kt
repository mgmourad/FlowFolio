package com.flowfolio.core.voice

import java.io.File

/**
 * A downloadable voice pack: a zip of the Kokoro model files, its SHA-256, and the files that must
 * exist (non-empty) on disk for the pack to be usable. The URL and digest are fixed per release.
 */
class VoicePack(val archiveUrl: String, val archiveSha256: String, val requiredFiles: List<String>) {
    /** True when every required file is present and non-empty in [dir]. */
    fun isReady(dir: File): Boolean = requiredFiles.all { File(dir, it).length() > 0L }

    /** True when [archive] exists and its SHA-256 matches this pack's. */
    fun hasValidChecksum(archive: File): Boolean = archive.isFile && Sha256.of(archive) == archiveSha256
}
