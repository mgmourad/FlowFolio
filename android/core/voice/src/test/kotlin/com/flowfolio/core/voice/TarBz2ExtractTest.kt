package com.flowfolio.core.voice

import java.io.File
import java.io.FileOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TarBz2ExtractTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `extracts only the wanted files, flattening the archive root`() {
        val archive = tmp.newFile("pack.tar.bz2")
        TarArchiveOutputStream(BZip2CompressorOutputStream(FileOutputStream(archive))).use { out ->
            out.putArchiveEntry(TarArchiveEntry("kokoro/model.int8.onnx").apply { size = 5 })
            out.write("model".toByteArray())
            out.closeArchiveEntry()
            out.putArchiveEntry(TarArchiveEntry("kokoro/voices.bin").apply { size = 6 })
            out.write("voices".toByteArray())
            out.closeArchiveEntry()
            out.putArchiveEntry(TarArchiveEntry("kokoro/espeak-ng-data/en_dict").apply { size = 4 })
            out.write("skip".toByteArray())
            out.closeArchiveEntry()
        }
        val dir = tmp.newFolder("out")
        TarBz2Extract.extract(archive, dir, setOf("model.int8.onnx", "voices.bin"))

        assertEquals("model", File(dir, "model.int8.onnx").readText())
        assertEquals("voices", File(dir, "voices.bin").readText())
        // The unwanted file and the root directory are not extracted.
        assertFalse(File(dir, "espeak-ng-data").exists())
        assertFalse(File(dir, "kokoro").exists())
    }
}
