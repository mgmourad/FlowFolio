package com.flowfolio.feature.library

import com.flowfolio.core.data.FolderSync
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.ShelfRepository
import com.flowfolio.core.model.StorageMode
import kotlinx.coroutines.flow.first

/**
 * Brings imported folders up to date (TM2), for the Library's and Shelves' pull-to-refresh and the quiet check when
 * the library opens: new books come in, and stand on the shelf already made from their folder.
 */
internal class FolderRefresh(
    private val library: LibraryRepository,
    private val folders: FolderSync,
    private val shelves: ShelfRepository
) {
    /** Checks the remembered folders (only those read in place when [inPlaceOnly]); returns how many books came in. */
    suspend fun run(inPlaceOnly: Boolean): Int {
        rememberHeldFolders()
        val added = folders.sync(inPlaceOnly).added
        if (added.isNotEmpty()) {
            val existing = shelves.observeShelves().first().associate { it.name to it.id }
            FoldersToShelves.plan(added).forEach { (name, books) ->
                existing[name]?.let { id -> shelves.addBooks(id, books.map { it.id }) }
            }
        }
        return added.size
    }

    /** Folders read in place before folder sync existed: remembered with what they hold now. */
    private suspend fun rememberHeldFolders() {
        val known = folders.folders()
        for (uri in library.heldFolders() - known) {
            val files = library.listFolder(uri) ?: continue
            folders.remember(uri, StorageMode.LINK, files.map { it.uri })
        }
    }
}
