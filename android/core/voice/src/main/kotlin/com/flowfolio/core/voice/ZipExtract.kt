package com.flowfolio.core.voice

import java.io.File
import java.util.zip.ZipInputStream

/** Extracts a zip archive, flattening each entry to its file name (the voice pack is a flat zip). */
object ZipExtract {
    fun extract(zip: File, dir: File) {
        dir.mkdirs()
        ZipInputStream(zip.inputStream()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                // Flatten to the base name: the pack has no nested structure, and this never leaves [dir].
                val name = entry.name.substringAfterLast('/')
                if (name.isEmpty()) continue // a directory entry
                File(dir, name).outputStream().use { zin.copyTo(it) }
                zin.closeEntry()
            }
        }
    }
}
