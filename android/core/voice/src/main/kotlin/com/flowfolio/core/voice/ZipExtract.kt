package com.flowfolio.core.voice

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/** Extracts a zip archive, flattening each entry to its file name (the voice pack is a flat zip). */
object ZipExtract {
    fun extract(zip: File, dir: File) {
        dir.mkdirs()
        ZipInputStream(zip.inputStream()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                writeEntry(zin, entry, dir)
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
    }

    private fun writeEntry(zin: ZipInputStream, entry: ZipEntry, dir: File) {
        // Flatten to the base name: the pack has no nested structure, and this never leaves [dir].
        val name = entry.name.substringAfterLast('/')
        if (name.isNotEmpty()) {
            File(dir, name).outputStream().use { zin.copyTo(it) }
        }
    }
}
