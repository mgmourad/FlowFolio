package com.flowfolio.core.voice

import java.io.File
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/**
 * Extracts a `.tar.bz2` voice pack, keeping only the files in [wanted] and flattening each to its base
 * name. The sherpa-onnx release ships the model as a tar.bz2 that also carries `espeak-ng-data/` and
 * Chinese dicts/FSTs narration does not use, so those are skipped to save ~18 MB.
 */
object TarBz2Extract {
    fun extract(archive: File, dir: File, wanted: Set<String>) {
        dir.mkdirs()
        BZip2CompressorInputStream(archive.inputStream().buffered()).use { bz ->
            TarArchiveInputStream(bz).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    writeEntry(tar, entry, dir, wanted)
                    entry = tar.nextEntry
                }
            }
        }
    }

    private fun writeEntry(tar: TarArchiveInputStream, entry: TarArchiveEntry, dir: File, wanted: Set<String>) {
        val name = entry.name.substringAfterLast('/')
        if (entry.isFile && name in wanted) {
            File(dir, name).outputStream().use { tar.copyTo(it) }
        }
    }
}
