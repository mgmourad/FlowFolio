package com.flowfolio.core.data.flow

import com.flowfolio.core.conversion.SidecarBuilder
import java.io.File

/**
 * A PDF's Dynamic Flow bundle (ADR-0045): `files/flow/<bookId>/book.epub` and `flowfolio.json`. Private to the app,
 * never listed as a book, deleted with the book. The EPUB is written under a temporary name and renamed when complete,
 * so a bundle that exists is whole.
 */
class FlowFiles(private val root: File) {
    fun dir(bookId: String) = File(File(root, "flow"), bookId)

    fun epub(bookId: String) = File(dir(bookId), EPUB)

    fun sidecar(bookId: String) = File(dir(bookId), SIDECAR)

    /** Where the converter writes before the bundle is complete. */
    fun partial(bookId: String) = File(dir(bookId), "$EPUB.part")

    /** A whole bundle made by this version of the converter. */
    fun ready(bookId: String): Boolean = epub(bookId).isFile && !outdated(bookId)

    /**
     * A bundle made by an earlier converter: it is converted again on the book's next opening, so improvements reach
     * books converted before them.
     */
    fun outdated(bookId: String): Boolean {
        val sidecar = sidecar(bookId)
        if (!sidecar.isFile) return epub(bookId).isFile
        val stamp = sidecar.lastModified()
        return versions[bookId]?.takeIf { it.first == stamp }?.second
            ?: (engineOf(sidecar) != SidecarBuilder.ENGINE_VERSION).also { versions[bookId] = stamp to it }
    }

    private fun engineOf(sidecar: File) =
        runCatching { ENGINE_VERSION_IN.find(sidecar.readText())?.groupValues?.get(1) }.getOrNull()

    /** Whether each bundle is outdated, by its sidecar's time: the library asks often, the answer rarely changes. */
    private val versions = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Boolean>>()

    /** Removes the bundle (the book was deleted, or a conversion failed). */
    fun delete(bookId: String) {
        dir(bookId).deleteRecursively()
    }

    private companion object {
        const val EPUB = "book.epub"
        const val SIDECAR = "flowfolio.json"

        /** The converter's version in a sidecar's `recipe.engine` (the sidecar is a few hundred kB: no full parse). */
        val ENGINE_VERSION_IN = Regex("\"engine\"\\s*:\\s*\\{[^}]*\"version\"\\s*:\\s*\"([^\"]+)\"")
    }
}
