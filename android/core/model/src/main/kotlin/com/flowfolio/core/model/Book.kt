package com.flowfolio.core.model

/** A book in the library. [id] is the SHA-256 of the file's bytes (hex), shared with the converter's bookId. */
data class Book(
    val id: String,
    val title: String,
    val pageCount: Int,
    val storage: BookStorage,
    val sizeBytes: Long,
    val addedAtMillis: Long,
    val lastOpenedAtMillis: Long?,
    val coverPath: String?,
    val passwordProtected: Boolean,
    /** Where the reader left off (0-based page), or null if the book has not been read yet. */
    val lastPage: Int? = null
)

/** Where the book's bytes live (ADR-0012 import storage setting). */
sealed interface BookStorage {
    val mode: StorageMode

    /** FlowFolio's own copy in app-private storage. */
    data class Copied(val path: String) : BookStorage {
        override val mode get() = StorageMode.COPY
    }

    /** Read in place through a persisted Storage Access Framework URI. */
    data class Linked(val uri: String) : BookStorage {
        override val mode get() = StorageMode.LINK
    }
}

enum class StorageMode { COPY, LINK }

/** Whether a book's file can currently be opened. Only linked books can go missing. */
enum class Availability { AVAILABLE, MISSING }

enum class LibrarySort { RECENT, TITLE }

enum class ThemePreference { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val storageMode: StorageMode = StorageMode.COPY,
    val askStorageEachImport: Boolean = false,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val storageNoticeSeen: Boolean = false,
    val librarySort: LibrarySort = LibrarySort.RECENT,
    /** Two pages side by side on wide windows (the unfolded Fold); off means always one page. */
    val readerSpreads: Boolean = true,
    /** In a spread, show the cover on its own (print style) rather than pairing from the first page. */
    val spreadCoverAlone: Boolean = true,
    /** In dark mode, show pages light-on-dark (images keep their colours). Off: pages as printed. */
    val nightPages: Boolean = false
)
