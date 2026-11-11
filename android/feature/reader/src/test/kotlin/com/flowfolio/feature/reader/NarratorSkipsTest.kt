package com.flowfolio.feature.reader

import com.flowfolio.core.model.NarratorSkip
import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.core.voice.SpeechSegment
import com.flowfolio.core.voice.SpeechText
import com.flowfolio.feature.reader.NarratorSkips.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The narrator's previous and next buttons, by paragraph, page, chapter and subchapter (owner, 2026-11-10). */
class NarratorSkipsTest {
    // Two paragraphs of two sentences each, then a heading and a third paragraph: starts at 0, 2, 4, 5.
    private val segments = listOf(
        SpeechSegment("One.", SpeechText.SENTENCE_PAUSE_MS),
        SpeechSegment("Two.", SpeechText.PARAGRAPH_PAUSE_MS),
        SpeechSegment("Three.", SpeechText.SENTENCE_PAUSE_MS),
        SpeechSegment("Four.", SpeechText.PARAGRAPH_PAUSE_MS),
        SpeechSegment("Heading", SpeechText.SUBTITLE_PAUSE_MS),
        SpeechSegment("Five.", SpeechText.PARAGRAPH_PAUSE_MS)
    )
    private val starts = NarratorSkips.paragraphStarts(segments)
    private val outline = listOf(
        OutlineEntry(0, 0, "Front"),
        OutlineEntry(0, 10, "Chapter 1"),
        OutlineEntry(1, 14, "1.1"),
        OutlineEntry(1, 18, "1.2"),
        OutlineEntry(0, 25, "Chapter 2")
    )

    private fun next(how: NarratorSkip, page: Int, segment: Int) =
        NarratorSkips.next(how, Place(page, segment), starts, outline, PAGES)

    private fun previous(how: NarratorSkip, page: Int, segment: Int) =
        NarratorSkips.previous(how, Place(page, segment), starts, outline) { listOf(0, 3, 7) }

    @Test
    fun paragraphsStartAfterParagraphAndHeadingBreaks() {
        assertEquals(listOf(0, 2, 4, 5), starts)
    }

    @Test
    fun nextParagraphIsTheNextStartElseTheNextPage() {
        assertEquals(Place(12, 2), next(NarratorSkip.PARAGRAPH, 12, 0))
        assertEquals(Place(12, 4), next(NarratorSkip.PARAGRAPH, 12, 3))
        assertEquals(Place(13, 0), next(NarratorSkip.PARAGRAPH, 12, 5))
        assertNull(next(NarratorSkip.PARAGRAPH, PAGES - 1, 5))
    }

    @Test
    fun previousParagraphRestartsTheCurrentOneThenGoesBack() {
        assertEquals(Place(12, 2), previous(NarratorSkip.PARAGRAPH, 12, 3))
        assertEquals(Place(12, 0), previous(NarratorSkip.PARAGRAPH, 12, 2))
        // From the first paragraph of a page: the last paragraph of the page before.
        assertEquals(Place(11, 7), previous(NarratorSkip.PARAGRAPH, 12, 0))
        assertEquals(Place(0, 0), previous(NarratorSkip.PARAGRAPH, 0, 0))
    }

    @Test
    fun pages() {
        assertEquals(Place(13, 0), next(NarratorSkip.PAGE, 12, 3))
        assertEquals(Place(11, 0), previous(NarratorSkip.PAGE, 12, 3))
        assertEquals(Place(0, 0), previous(NarratorSkip.PAGE, 0, 3))
    }

    @Test
    fun chaptersUseTopLevelEntriesOnly() {
        assertEquals(Place(25, 0), next(NarratorSkip.CHAPTER, 14, 0))
        assertNull(next(NarratorSkip.CHAPTER, 26, 0))
        // Inside chapter 1: back to its start; at its start: chapter before.
        assertEquals(Place(10, 0), previous(NarratorSkip.CHAPTER, 16, 0))
        assertEquals(Place(0, 0), previous(NarratorSkip.CHAPTER, 10, 0))
    }

    @Test
    fun subchaptersUseEveryEntry() {
        assertEquals(Place(14, 0), next(NarratorSkip.SUBCHAPTER, 10, 2))
        assertEquals(Place(18, 0), next(NarratorSkip.SUBCHAPTER, 14, 0))
        assertEquals(Place(14, 0), previous(NarratorSkip.SUBCHAPTER, 15, 0))
        assertEquals(Place(14, 0), previous(NarratorSkip.SUBCHAPTER, 14, 3))
        assertEquals(Place(10, 0), previous(NarratorSkip.SUBCHAPTER, 14, 0))
    }

    @Test
    fun aBookWithoutAnOutlineSkipsChaptersByPage() {
        assertEquals(Place(13, 0), NarratorSkips.next(NarratorSkip.CHAPTER, Place(12, 1), starts, emptyList(), PAGES))
        assertEquals(
            Place(11, 0),
            NarratorSkips.previous(NarratorSkip.CHAPTER, Place(12, 1), starts, emptyList()) {
                emptyList()
            }
        )
    }

    private companion object {
        const val PAGES = 40
    }
}
