package com.flowfolio.feature.library

import com.flowfolio.core.data.FolderFile
import com.flowfolio.core.data.FolderSync
import com.flowfolio.core.data.ImportResult
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.StorageMode

/**
 * Imports several files, or a whole folder and every folder inside it, one book after another (owner, 2026-10-26).
 * Books in a folder remember where they sat ([Book.folder]), for the library's Folders view. Reading a folder in place
 * keeps access to the folder once, not to each file.
 */
internal class BatchImport(
    private val library: LibraryRepository,
    /** Books done and books in all while it runs; null when it ends. */
    private val onProgress: (Pair<Int, Int>?) -> Unit,
    private val onDone: suspend (Outcome) -> Unit,
    /** Remembers imported folders, so books added to them later come in on a refresh (TM2). */
    private val folders: FolderSync = FolderSync.None
) {
    /**
     * How a batch went. [linkRefused] holds what could not be read in place and can be copied instead; [unreadable]
     * means the folder itself could not be read, [empty] that it held no books.
     */
    data class Outcome(
        val imported: List<Book> = emptyList(),
        val already: Int = 0,
        val failed: Int = 0,
        val linkRefused: ImportRequest? = null,
        val unreadable: Boolean = false,
        val empty: Boolean = false
    )

    suspend fun run(request: ImportRequest, mode: StorageMode) {
        val files = when (request) {
            is ImportRequest.Files -> request.uris.map { FolderFile(it, "") }
            is ImportRequest.Folder -> library.listFolder(request.treeUri)
        }
        // Reading a folder in place: access to the whole folder, kept once.
        val inPlace = request is ImportRequest.Folder && mode == StorageMode.LINK
        val outcome = when {
            files == null -> Outcome(unreadable = true)

            files.isEmpty() -> Outcome(empty = true)

            inPlace && !library.persistFolderAccess((request as ImportRequest.Folder).treeUri) ->
                Outcome(linkRefused = request)

            else -> importAll(files, mode, accessHeld = inPlace).also { remember(request, mode, files) }
        }
        onDone(outcome)
    }

    /**
     * A folder is remembered for syncing (TM2). One copied into the app keeps read access to the source folder too,
     * so a refresh can find what is added there; when the provider refuses, it simply is not synced.
     */
    private suspend fun remember(request: ImportRequest, mode: StorageMode, files: List<FolderFile>) {
        if (request !is ImportRequest.Folder) return
        val held = mode == StorageMode.LINK || library.persistFolderAccess(request.treeUri)
        if (held) folders.remember(request.treeUri, mode, files.map { it.uri })
    }

    private suspend fun importAll(files: List<FolderFile>, mode: StorageMode, accessHeld: Boolean): Outcome {
        val imported = ArrayList<Book>()
        val refused = ArrayList<String>()
        var already = 0
        var failed = 0
        try {
            files.forEachIndexed { i, file ->
                onProgress(i to files.size)
                val folder = file.folder.ifEmpty { null }
                when (val r = library.import(file.uri, mode, folder = folder, accessHeld = accessHeld)) {
                    is ImportResult.Imported -> imported += r.book
                    is ImportResult.Duplicate -> already++
                    ImportResult.LinkNotPossible -> refused += file.uri
                    else -> failed++
                }
            }
        } finally {
            onProgress(null)
        }
        return Outcome(imported, already, failed, refused.takeIf { it.isNotEmpty() }?.let { ImportRequest.Files(it) })
    }
}
