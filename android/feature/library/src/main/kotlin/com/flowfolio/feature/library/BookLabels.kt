package com.flowfolio.feature.library

import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.BookStorage

/** "PDF" or "EPUB", for pills and rows. */
internal fun Book.formatLabel(): Int = if (format == BookFormat.EPUB) R.string.format_epub else R.string.format_pdf

internal fun storageLabel(state: DetailsUiState, book: Book): Int = when {
    state.availability == Availability.MISSING -> R.string.info_storage_missing
    book.storage is BookStorage.Linked -> R.string.info_storage_link
    else -> R.string.info_storage_copy
}

/** PDF covers are page 1; an EPUB's is the image the book names as its cover. */
internal fun coverLabel(book: Book): Int =
    if (book.format == BookFormat.EPUB) R.string.info_cover_epub else R.string.info_cover_page1
