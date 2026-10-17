package com.flowfolio.feature.reader

import com.flowfolio.core.model.OutlineEntry

/** What the reader knows for moving around a book: its outline and printed page labels (null where none). */
data class BookNavigation(val outline: List<OutlineEntry> = emptyList(), val labels: List<String?> = emptyList())

/** Pure helpers behind Go to page, the contents list and the scrubber. */
internal object PageLookup {
    /**
     * The page (0-based) the reader typed: a printed label first ("xiv", "214"), as books are cited by their printed
     * numbers; otherwise a plain number counted from the first page (1-based). Null when nothing matches.
     */
    fun resolve(input: String, labels: List<String?>, pageCount: Int): Int? {
        val text = input.trim()
        val byLabel = labels.indexOfFirst { it != null && it.equals(text, ignoreCase = true) }
        return when {
            text.isEmpty() -> null
            byLabel in 0 until pageCount -> byLabel
            else -> text.toIntOrNull()?.takeIf { it in 1..pageCount }?.minus(1)
        }
    }

    /**
     * Index of the outline entry the page belongs to: the one starting closest before or on it (the later entry on a
     * tie, so a section beats its chapter), or -1. Going by page rather than list order matters because outlines are
     * not always sorted (OpenStax ends with a bookmark to page 1 named after the file).
     */
    fun chapterAt(outline: List<OutlineEntry>, page: Int): Int {
        var best = -1
        outline.forEachIndexed { i, entry ->
            if (entry.pageIndex in 0..page && (best < 0 || entry.pageIndex >= outline[best].pageIndex)) best = i
        }
        return best
    }

    /** The page's printed label, or its 1-based number when the PDF has none. */
    fun labelOf(page: Int, labels: List<String?>): String = labels.getOrNull(page) ?: (page + 1).toString()
}
