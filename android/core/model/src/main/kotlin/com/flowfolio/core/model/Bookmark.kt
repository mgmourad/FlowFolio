package com.flowfolio.core.model

/** A ribbon bookmark on [page] (0-based) of a book, in one of the app's colours. */
data class Bookmark(
    val id: Long,
    val bookId: String,
    val page: Int,
    val color: BookmarkColor,
    val createdAtMillis: Long
)

/**
 * A bookmark's colour, as one of the app's colour roles rather than a fixed colour, so it follows dark and light mode:
 * sub-item (red, the mock's ribbon), title (orange / brown), item (cyan / teal), accent (magenta / indigo), hint
 * (grey).
 */
enum class BookmarkColor { SUB_ITEM, TITLE, ITEM, ACCENT, HINT }
