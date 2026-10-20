package com.flowfolio.core.data

import com.flowfolio.core.model.BookFormat
import java.io.File

/** App-private locations for copied books and covers. Everything lives under [root] (Context.filesDir in the app). */
class BookFiles(private val root: File) {
    private val books get() = File(root, "books").apply { mkdirs() }
    private val covers get() = File(root, "covers").apply { mkdirs() }
    private val temp get() = File(root, "import-tmp").apply { mkdirs() }

    fun book(id: String, format: BookFormat = BookFormat.PDF) = File(books, "$id.${format.name.lowercase()}")

    fun cover(id: String) = File(covers, "$id.jpg")

    fun newTemp(): File = File.createTempFile("import-", ".part", temp)

    /** Free space for new books. A missing directory reports 0 bytes, so make sure it exists first. */
    fun usableBytes(): Long = root.apply { mkdirs() }.usableSpace

    /** Removes leftovers from imports interrupted by a crash or process death. */
    fun clearTemp() {
        temp.listFiles()?.forEach { it.delete() }
    }
}
