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
    val lastPage: Int? = null,
    /** The PDF's Author metadata (or what the reader typed), or null when unknown. */
    val author: String? = null,
    /** Subtitle, edition, publisher, description and the rest, as entered by the reader. */
    val details: BookDetails = BookDetails(),
    /** Pages run right to left (Arabic and other right-to-left books); set per book in the reader. */
    val rightToLeft: Boolean = false,
    /**
     * The book's printed page numbers ([PageLabels] runs): from the PDF, or read from its page margins. Null until the
     * reader has looked; "" when the book prints none (pages count from the cover).
     */
    val pageLabels: String? = null,
    val format: BookFormat = BookFormat.PDF,
    /** An EPUB's reading position as a reflow locator; [lastPage] and [pageCount] are at the size it was last read. */
    val lastLocator: String? = null
) {
    /** How far through the book the reader is, 0..1 (0 when not started). */
    val progress: Float
        get() = lastPage?.let { if (pageCount > 1) it.toFloat() / (pageCount - 1) else 1f }?.coerceIn(0f, 1f) ?: 0f
}

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

/** What kind of file a book is. EPUBs are reflowable: their page count depends on the text size (Phase 2). */
enum class BookFormat { PDF, EPUB }

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
    /** The page colour in the reader (the Aa sheet's swatches); images always keep their printed colours. */
    val paperStyle: PaperStyle = PaperStyle.AUTO,
    /** How pages turn in the reader. */
    val pageTurn: PageTurn = PageTurn.CURL,
    /** The mock's "Neon glow": the soft light on text and icons in dark mode (and soft shadows in light mode). */
    val neonGlow: Boolean = true,
    /** Look book details up online (Open Library, ADR-0017). Off until the reader agrees. */
    val onlineLookup: Boolean = false,
    /** Whether the first import has asked about the online lookup yet. */
    val onlineLookupAsked: Boolean = false,
    /** The language passages are translated into last (BCP-47); null means the phone's language. */
    val translateTo: String? = null
)

/**
 * The reader's paper (the mock's swatches). [AUTO] follows the app: pages as printed in light mode, Night in dark
 * mode. The others tint text and line art; photos are never changed.
 */
enum class PaperStyle { AUTO, PAPER, SEPIA, NIGHT, BLACK }

/** Page turns: the 3D curl (default), sliding sideways, or one continuous vertical scroll. */
enum class PageTurn { CURL, SLIDE, SCROLL }
