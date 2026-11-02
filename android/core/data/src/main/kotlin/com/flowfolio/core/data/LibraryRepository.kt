package com.flowfolio.core.data

import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookDetails
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.StorageMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Suppress("TooManyFunctions") // one call per thing the library does
interface LibraryRepository {
    fun observeBooks(sort: LibrarySort): Flow<List<Book>>

    /** One book, or null once it is removed. */
    fun observeBook(bookId: String): Flow<Book?>

    /**
     * Imports the PDF at [uri]. [fromShare] marks files handed over by another app (share sheet, "Open with"):
     * Android does not let FlowFolio keep access to those, so they are always copied regardless of [mode]. [folder]
     * is where the file sits in an imported folder ("Books/Physics"); [accessHeld] says FlowFolio already keeps access
     * to it through that folder, so read-in-place needs no permission of its own.
     */
    suspend fun import(
        uri: String,
        mode: StorageMode,
        fromShare: Boolean = false,
        folder: String? = null,
        accessHeld: Boolean = false
    ): ImportResult

    /** The PDFs and EPUBs anywhere under a picked folder, with where each sits; null when it cannot be read. */
    suspend fun listFolder(treeUri: String): List<FolderFile>?

    /** Keeps access to a picked folder for reading its books in place; false when the provider does not allow it. */
    suspend fun persistFolderAccess(treeUri: String): Boolean

    suspend fun availability(book: Book): Availability

    /** Size of the file at [uri] if known, for showing in the storage choice before importing. */
    suspend fun sizeOf(uri: String): Long?

    /** Points a linked book at a new location. The file must be the same book (same SHA-256). */
    suspend fun relink(bookId: String, uri: String): RelinkResult

    /** Returns the number of bookmarks for a book. */
    fun observeBookmarkCount(bookId: String): Flow<Int> = flowOf(0)

    /** Returns the number of annotations for a book. */
    fun observeAnnotationCount(bookId: String): Flow<Int> = flowOf(0)

    /** Returns the number of dictionary items for a book. */
    fun observeDictionaryCount(bookId: String): Flow<Int> = flowOf(0)

    /** Checks if a book has any bookmarks. */
    fun hasBookmarks(bookId: String): Flow<Boolean> = flowOf(false)

    /** Returns the IDs of all books that have bookmarks. */
    fun observeBookIdsWithBookmarks(): Flow<Set<String>> = flowOf(emptySet())

    /** Removes the book; deletes FlowFolio's copy and cover, and releases access to linked files. */
    suspend fun delete(bookId: String)

    suspend fun markOpened(bookId: String)

    /** Gives the book a new title in the library ([title] trimmed, not blank). The file is not changed. */
    suspend fun rename(bookId: String, title: String)

    /** Saves what the reader edited: the title (not blank), the author and the details. Blank fields become unknown. */
    suspend fun updateDetails(bookId: String, title: String, author: String?, details: BookDetails)

    /** Marks the book a favourite, or not (owner, M3T2). */
    suspend fun setFavorite(bookId: String, on: Boolean) = Unit

    /** Marks the book finished now, or not finished (owner, M3T2). */
    suspend fun setFinished(bookId: String, on: Boolean) = Unit
}

sealed interface ImportResult {
    data class Imported(val book: Book) : ImportResult

    /** The same file (by content) is already in the library. */
    data class Duplicate(val existing: Book) : ImportResult

    /** Not a PDF or EPUB FlowFolio can open. */
    data object NotABook : ImportResult

    /** An EPUB whose content is encrypted (DRM). */
    data object DrmProtected : ImportResult

    /** The file could not be read at all. */
    data object Unreadable : ImportResult

    data class NotEnoughSpace(val neededBytes: Long) : ImportResult

    /** "Read in place" was chosen but the provider does not allow keeping access; the UI offers copying instead. */
    data object LinkNotPossible : ImportResult
}

sealed interface RelinkResult {
    data object Relinked : RelinkResult

    /** The chosen file is a different book (content differs). */
    data object DifferentFile : RelinkResult

    data object Unreadable : RelinkResult

    data object LinkNotPossible : RelinkResult
}
