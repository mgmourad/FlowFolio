package com.flowfolio.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-book search snippets (ADR-0052): blocks keep a space between them, and one letter finds nothing. */
class SearchSnippetTest {
    @Test
    fun blocksThatMeetGetASpace() {
        // "Brief Contents" and "Title Page" are separate blocks: their chapter offsets jump between them.
        val text = "Brief ContentsTitle Page"
        val offsets = IntArray(text.length) { if (it < 14) it else it + 1 }
        val range = text.indexOf("Title")..(text.indexOf("Title") + 4)
        assertEquals("Brief Contents Title Page", snippet(text, range, offsets))
    }

    @Test
    fun textWithinOneBlockIsUntouched() {
        val text = "the whale, the whale"
        val offsets = IntArray(text.length) { it }
        assertEquals("the whale, the whale", snippet(text, 4..8, offsets))
    }

    @Test
    fun matchesAreCaseBlindAndBlankFindsNothing() {
        assertEquals(2, findMatches("Whale whale", "whale").size)
        assertTrue(findMatches("anything", "  ").isEmpty())
    }
}
