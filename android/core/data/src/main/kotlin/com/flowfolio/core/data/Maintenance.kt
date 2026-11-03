package com.flowfolio.core.data

import android.content.Context
import com.flowfolio.core.model.LibrarySort
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Clearing the cache, and starting over (owner, M5 round 4).
 *
 * The two are deliberately very different things, and the app says so before doing either: the cache is work the app
 * can always do again, while a reset is the reader's library and everything they did in it.
 *
 * **Nothing here ever deletes a file the reader keeps on their phone.** A book read in place is removed from the
 * library and FlowFolio lets go of its access; the file itself is left exactly where it is. Only FlowFolio's own
 * copies, in its own private storage, are deleted -- which is what [LibraryRepository.delete] already does per book,
 * and why a reset is built out of it rather than out of a hammer on the database.
 */
@Singleton
class Maintenance @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibraryRepository,
    private val shelves: ShelfRepository,
    private val dictionary: DictionaryRepository,
    private val translations: TranslationRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    /**
     * How much the cache is holding, in bytes.
     *
     * This is drawn and redrawn work -- an EPUB's equations and tables as pictures (ADR-0021), page thumbnails,
     * part-finished conversions -- so the number is worth showing beside the button that frees it.
     */
    suspend fun cacheBytes(): Long = withContext(io) { cacheDirs().sumOf { it.sizeOnDisk() } }

    /**
     * Empties the cache and answers how much that freed.
     *
     * Everything in it is rebuilt on demand: islands are drawn again when a chapter is read, thumbnails when the
     * riffle bar is opened. A conversion running at this moment loses its working files and starts again.
     */
    suspend fun clearCache(): Long = withContext(io) {
        val before = cacheDirs().sumOf { it.sizeOnDisk() }
        cacheDirs().forEach { dir -> dir.listFiles()?.forEach { it.deleteRecursively() } }
        before - cacheDirs().sumOf { it.sizeOnDisk() }
    }

    /**
     * Puts the app back to how it started: every book out of the library, every shelf, every highlight, ribbon,
     * note, VoiceMark, looked-up word and reading position with them, and the cache emptied.
     *
     * Settings are kept -- the theme, the fonts, the atmospheres made. They are not progress, and a reader who
     * wanted a clean library rarely wanted their reading set up from scratch as well.
     */
    suspend fun reset() = withContext(io) {
        // Book by book, so each one's storage is honoured: a copy is deleted, a linked file only let go of.
        library.observeBooks(LibrarySort.RECENT).first().forEach { library.delete(it.id) }
        shelves.observeShelves().first().forEach { shelves.delete(it.id) }
        // Annotations, ribbons, sessions and a book's definitions go with their book (the rows cascade); what is
        // left is what was never tied to one.
        dictionary.removeAll()
        translations.clearHistory()
        clearCache()
        Unit
    }

    /** The app's own cache directories; the external one is absent on a phone with no shared storage mounted. */
    private fun cacheDirs(): List<File> = listOfNotNull(context.cacheDir, context.externalCacheDir)

    private fun File.sizeOnDisk(): Long = if (isDirectory) listFiles()?.sumOf { it.sizeOnDisk() } ?: 0L else length()
}
