package com.flowfolio.core.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechTextTest {
    @Test
    fun `straightens curly apostrophes in contractions`() {
        assertEquals("She's fine", SpeechText.normalize("She\u2019s fine"))
    }

    @Test
    fun `strips quotes that are never spoken`() {
        assertEquals("smart and done", SpeechText.normalize("\u201Csmart\u201D and \u201Cdone\u201D"))
    }

    @Test
    fun `joins a word hyphenated across a line break`() {
        assertEquals("trying to keep up", SpeechText.normalize("try-\ning to keep up"))
    }

    @Test
    fun `turns newlines into spaces and collapses whitespace`() {
        assertEquals("one two three", SpeechText.normalize("one  two\n\n  three"))
    }

    @Test
    fun `replaces dashes and ellipsis with speakable forms`() {
        assertEquals("a b, c...", SpeechText.normalize("a \u2014 b, c\u2026"))
    }
}
