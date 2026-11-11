package com.flowfolio.feature.reader

import com.flowfolio.core.model.NarratorSkip
import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.core.voice.SpeechSegment
import com.flowfolio.core.voice.SpeechText

/**
 * Where Narrator Mode's previous and next buttons go (owner, 2026-11-10), by the reader's choice of [NarratorSkip].
 * "Previous" works the way a music player's does: into a paragraph, chapter or section it goes back to the start of
 * that one, and from its start to the one before. A book without an outline skips chapters by page.
 */
internal object NarratorSkips {
    /** A place to read from: [segment] of [page]'s speech. */
    data class Place(val page: Int, val segment: Int)

    /** Indices of [segments] that begin a paragraph: the first, and any after a paragraph, title or heading break. */
    fun paragraphStarts(segments: List<SpeechSegment>): List<Int> = segments.indices.filter { i ->
        i == 0 || segments[i - 1].pauseMs >= SpeechText.PARAGRAPH_PAUSE_MS
    }

    fun next(how: NarratorSkip, at: Place, starts: List<Int>, outline: List<OutlineEntry>, pageCount: Int): Place? =
        when (how) {
            NarratorSkip.PARAGRAPH -> starts.firstOrNull { it > at.segment }?.let { Place(at.page, it) }
                ?: page(at.page + 1, pageCount)

            NarratorSkip.PAGE -> page(at.page + 1, pageCount)

            NarratorSkip.CHAPTER -> nextHeading(outline, at.page, topLevel = true, pageCount)

            NarratorSkip.SUBCHAPTER -> nextHeading(outline, at.page, topLevel = false, pageCount)
        }

    /** [previousStarts] gives the paragraph starts of the page before, read only when going back onto it. */
    fun previous(
        how: NarratorSkip,
        at: Place,
        starts: List<Int>,
        outline: List<OutlineEntry>,
        previousStarts: () -> List<Int>
    ): Place = when (how) {
        NarratorSkip.PARAGRAPH -> previousParagraph(at, starts, previousStarts)
        NarratorSkip.PAGE -> Place((at.page - 1).coerceAtLeast(0), 0)
        NarratorSkip.CHAPTER -> previousHeading(outline, at, topLevel = true)
        NarratorSkip.SUBCHAPTER -> previousHeading(outline, at, topLevel = false)
    }

    private fun previousParagraph(at: Place, starts: List<Int>, previousStarts: () -> List<Int>): Place {
        val current = starts.lastOrNull { it <= at.segment } ?: 0
        val earlier = starts.lastOrNull { it < current }
        return when {
            at.segment > current -> Place(at.page, current)
            earlier != null -> Place(at.page, earlier)
            at.page == 0 -> Place(0, 0)
            else -> Place(at.page - 1, previousStarts().lastOrNull() ?: 0)
        }
    }

    private fun headings(outline: List<OutlineEntry>, topLevel: Boolean): List<Int> = outline
        .filter { it.pageIndex >= 0 && (!topLevel || it.depth == 0) }
        .map { it.pageIndex }
        .distinct()
        .sorted()

    private fun nextHeading(outline: List<OutlineEntry>, page: Int, topLevel: Boolean, pageCount: Int): Place? {
        val starts = headings(outline, topLevel)
        if (starts.isEmpty()) return page(page + 1, pageCount)
        return starts.firstOrNull { it > page }?.let { Place(it, 0) }
    }

    private fun previousHeading(outline: List<OutlineEntry>, at: Place, topLevel: Boolean): Place {
        val starts = headings(outline, topLevel)
        if (starts.isEmpty()) return Place((at.page - 1).coerceAtLeast(0), 0)
        val current = starts.lastOrNull { it <= at.page }
        val intoIt = current != null && (at.page > current || at.segment > 0)
        val target = if (intoIt) current else starts.lastOrNull { it < at.page }
        return Place(target ?: 0, 0)
    }

    private fun page(page: Int, pageCount: Int): Place? = if (page < pageCount) Place(page, 0) else null
}
