package com.flowfolio.core.data

import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.StorageMode
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeBooks(sort: LibrarySort): Flow<List<Book>>

    /** One book, or null once it is removed. */
    fun observeBook(bookId: String): Flow<Book?>

    /**
     * Imports the PDF at [uri]. [fromShare] marks files handed over by another app (share sheet, "Open with"):
     * Android does not let FlowFolio keep access to those, so they are always copied regardless of [mode].
     */
    suspend fun import(uri: String, mode: StorageMode, fromShare: Boolean = false): ImportResult

    suspend fun availability(book: Book): Availability

    /** Size of the file at [uri] if known, for showing in the storage choice before importing. */
    suspend fun sizeOf(uri: String): Long?

    /** Points a linked book at a new location. The file must be the same book (same SHA-256). */
    suspend fun relink(bookId: String, uri: String): RelinkResult

    /** Removes the book; deletes FlowFolio's copy and cover, and releases access to linked files. */
    suspend fun delete(bookId: String)

    suspend fun markOpened(bookId: String)
}

sealed interface ImportResult {
    data class Imported(val book: Book) : ImportResult

    /** The same file (by content) is already in the library. */
    data class Duplicate(val existing: Book) : ImportResult

    data object NotAPdf : ImportResult

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
