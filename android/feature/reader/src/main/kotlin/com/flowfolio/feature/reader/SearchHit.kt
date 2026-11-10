package com.flowfolio.feature.reader

/** One in-book search result: where it is, what it says, and enough to jump to it and highlight it. */
internal data class SearchHit(
    /** The screen page to jump to. */
    val page: Int,
    /** An EPUB's `chapter:offset` for the match, or null for a PDF (whose highlights stay by page). */
    val locator: String?,
    /** Chapter offsets for an EPUB, character indices on the page for a PDF. */
    val start: Int,
    val end: Int,
    /** The matched text. */
    val text: String,
    /** A line or two around the match, for the results list. */
    val snippet: String,
    /** The chapter or section it is in, for the results list. */
    val chapter: String?
)

/** Case-insensitive occurrences of [query] in [text], as index ranges; empty when the query is blank. */
internal fun findMatches(text: String, query: String): List<IntRange> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val out = ArrayList<IntRange>()
    var i = text.indexOf(q, 0, ignoreCase = true)
    while (i >= 0) {
        out += i until (i + q.length)
        i = text.indexOf(q, i + q.length, ignoreCase = true)
    }
    return out
}

/**
 * A short excerpt of [text] around [range], newlines flattened, ellipses where it was cut. With [offsets] (an EPUB
 * chapter's), a space goes wherever two blocks meet with nothing between them, so "Brief ContentsTitle Page" reads
 * "Brief Contents Title Page" (ADR-0052).
 */
internal fun snippet(text: String, range: IntRange, offsets: IntArray? = null, radius: Int = SNIPPET_RADIUS): String {
    val start = (range.first - radius).coerceAtLeast(0)
    val end = (range.last + radius + 1).coerceAtMost(text.length)
    val raw = buildString {
        for (i in start until end) {
            val c = text[i]
            if (blocksMeet(text, offsets, i, start)) append(' ')
            append(if (c == '\n') ' ' else c)
        }
    }.trim()
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < text.length) "…" else ""
    return prefix + raw + suffix
}

private const val SNIPPET_RADIUS = 60

/** Whether two blocks meet at [i] with no space between them: the chapter offsets jump there. */
private fun blocksMeet(text: String, offsets: IntArray?, i: Int, start: Int): Boolean {
    if (offsets == null || i <= start || i >= offsets.size) return false
    val jump = offsets[i] - offsets[i - 1] > 1
    return jump && !text[i].isWhitespace() && !text[i - 1].isWhitespace()
}
