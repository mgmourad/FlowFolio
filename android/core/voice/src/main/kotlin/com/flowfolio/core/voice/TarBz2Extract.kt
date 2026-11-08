package com.flowfolio.core.voice

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/**
 * Extracts a `.tar.bz2` voice pack, keeping only the files in [wanted] and flattening each to its base
 * name. The sherpa-onnx release ships the model as a tar.bz2 that also carries `espeak-ng-data/` and
 * Chinese dicts/FSTs narration does not use, so those are skipped to save ~18 MB.
 */
object TarBz2Extract {
    fun extract(archive: File, dir: File, wanted: Set<String>, onProgress: (Float) -> Unit = {}) {
        dir.mkdirs()
        val tracked = ProgressInputStream(archive.inputStream().buffered(), archive.length(), onProgress)
        BZip2CompressorInputStream(tracked).use { bz ->
            TarArchiveInputStream(bz).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    writeEntry(tar, entry, dir, wanted)
                    entry = tar.nextEntry
                }
            }
        }
        onProgress(1f)
    }

    private fun writeEntry(tar: TarArchiveInputStream, entry: TarArchiveEntry, dir: File, wanted: Set<String>) {
        val name = entry.name.substringAfterLast('/')
        if (entry.isFile && name in wanted) {
            File(dir, name).outputStream().use { tar.copyTo(it) }
        }
    }
}

internal class ProgressInputStream(
    delegate: InputStream,
    private val total: Long,
    private val onProgress: (Float) -> Unit
) : FilterInputStream(delegate) {
    private var bytesRead = 0L

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) advance(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) advance(n.toLong())
        return n
    }

    private fun advance(delta: Long) {
        bytesRead += delta
        if (total > 0L) onProgress((bytesRead.toFloat() / total).coerceIn(0f, 1f))
    }
}
