package com.flowfolio.core.voice

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZipExtractTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `extracts every entry, flattening the archive root`() {
        val zip = tmp.newFile("pack.zip")
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("kokoro/model.int8.onnx"))
            out.write("model".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("kokoro/voices.bin"))
            out.write("voices".toByteArray())
            out.closeEntry()
        }
        val dir = tmp.newFolder("out")
        ZipExtract.extract(zip, dir)

        assertEquals("model", File(dir, "model.int8.onnx").readText())
        assertEquals("voices", File(dir, "voices.bin").readText())
        // The root directory is not created.
        assertFalse(File(dir, "kokoro").exists())
    }
}
