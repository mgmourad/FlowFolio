package com.flowfolio.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSearchTest {
    @Test
    fun findMatchesIsCaseInsensitiveAndReturnsEveryOccurrence() {
        assertEquals(listOf(0..4, 6..10, 12..16), findMatches("Hello hello hello", "hello"))
    }

    @Test
    fun findMatchesReturnsEmptyForABlankQuery() {
        assertTrue(findMatches("some text", "   ").isEmpty())
    }

    @Test
    fun findMatchesFindsPhrasesAtTheEdges() {
        assertEquals(listOf(0..3), findMatches("four score", "four"))
        assertEquals(listOf(5..9), findMatches("four score", "score"))
    }

    @Test
    fun snippetKeepsTheMatchAndFlattensNewlines() {
        val text = "first line\nsecond line with the needle here\nthird line"
        val s = snippet(text, text.indexOf("needle")..text.indexOf("needle") + "needle".length - 1)
        assertTrue(s.contains("needle"))
        assertTrue(s.none { it == '\n' })
    }
}
