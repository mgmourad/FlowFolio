package com.flowfolio.core.data

import com.flowfolio.core.model.Book
import com.flowfolio.core.model.StorageMode
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What a folder sync found: the books it added, by the folder each sits in ("Books/Physics"). */
data class FolderSyncReport(val added: List<Book>)

/**
 * Keeps imported folders in step with the phone (TM2, owner 2026-11-12). Every imported folder is remembered with how
 * it was imported and the files already seen in it; a sync lists each folder again and imports only what is new. A
 * folder read in place syncs whenever the library opens ([sync] with `inPlaceOnly`); one copied into the app syncs when
 * the reader pulls the library or shelves down to refresh. Books gone from the folder stay in the library.
 *
 * Remembering what was seen keeps a refresh quick: each new file is read and hashed once, not every file every time.
 * Each folder is one small text file: its mode and address, then the files seen, one per line ([DefaultFolderSync]).
 */
interface FolderSync {
    /** A folder was imported in [mode]: remember it, and that [files] are in the library already. */
    suspend fun remember(treeUri: String, mode: StorageMode, files: List<String>)

    /** The folders remembered, by address. */
    suspend fun folders(): Set<String>

    /** Imports what is new in the remembered folders (only those read in place when [inPlaceOnly]). */
    suspend fun sync(inPlaceOnly: Boolean): FolderSyncReport

    /** No folders: for tests, and fakes that don't need them. */
    object None : FolderSync {
        override suspend fun remember(treeUri: String, mode: StorageMode, files: List<String>) = Unit

        override suspend fun folders(): Set<String> = emptySet()

        override suspend fun sync(inPlaceOnly: Boolean) = FolderSyncReport(emptyList())
    }
}

/** [FolderSync] kept as one small text file per folder in [dir]. */
class DefaultFolderSync(
    private val dir: File,
    private val library: LibraryRepository,
    /** Where its files are read and written: never the main thread (a sync at launch stalled it, TM2). */
    private val io: CoroutineDispatcher = Dispatchers.IO
) : FolderSync {
    private val lock = Mutex()

    override suspend fun remember(treeUri: String, mode: StorageMode, files: List<String>) = withContext(io) {
        lock.withLock { rememberLocked(treeUri, mode, files) }
    }

    private fun rememberLocked(treeUri: String, mode: StorageMode, files: List<String>) {
        val known = read(fileOf(treeUri))
        write(Folder(treeUri, mode, (known?.seen.orEmpty() + files).toSet()))
    }

    override suspend fun folders(): Set<String> = withContext(io) { lock.withLock { all().map { it.uri }.toSet() } }

    /**
     * Lists every remembered folder (only those read in place when [inPlaceOnly]) and imports the files not seen
     * before, each with the folder it sits in. A folder that cannot be read now (access withdrawn, card removed) is
     * left as it is.
     */
    override suspend fun sync(inPlaceOnly: Boolean): FolderSyncReport =
        withContext(io) { lock.withLock { syncLocked(inPlaceOnly) } }

    private suspend fun syncLocked(inPlaceOnly: Boolean): FolderSyncReport {
        val added = ArrayList<Book>()
        for (folder in all().filter { !inPlaceOnly || it.mode == StorageMode.LINK }) {
            val fresh = library.listFolder(folder.uri).orEmpty().filter { it.uri !in folder.seen }
            if (fresh.isEmpty()) continue
            for (file in fresh) {
                val result = library.import(
                    file.uri,
                    folder.mode,
                    folder = file.folder.ifEmpty { null },
                    accessHeld = true
                )
                if (result is ImportResult.Imported) added += result.book
            }
            write(folder.copy(seen = folder.seen + fresh.map { it.uri }))
        }
        return FolderSyncReport(added)
    }

    private data class Folder(val uri: String, val mode: StorageMode, val seen: Set<String>)

    private fun all(): List<Folder> = dir.listFiles { f -> f.name.endsWith(SUFFIX) }.orEmpty().mapNotNull(::read)

    private fun read(file: File): Folder? = runCatching {
        val lines = file.readLines()
        val (mode, uri) = lines.first().split('\t', limit = 2)
        Folder(uri, StorageMode.valueOf(mode), lines.drop(1).filter { it.isNotEmpty() }.toSet())
    }.getOrNull()

    private fun write(folder: Folder) {
        dir.mkdirs()
        val target = fileOf(folder.uri)
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(
            buildString {
                append(folder.mode.name).append('\t').append(folder.uri).append('\n')
                folder.seen.forEach { append(it).append('\n') }
            }
        )
        tmp.renameTo(target)
    }

    private fun fileOf(treeUri: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(treeUri.toByteArray())
        return File(dir, digest.take(NAME_BYTES).joinToString("") { "%02x".format(it) } + SUFFIX)
    }

    private companion object {
        const val SUFFIX = ".folder"
        const val NAME_BYTES = 12
    }
}
