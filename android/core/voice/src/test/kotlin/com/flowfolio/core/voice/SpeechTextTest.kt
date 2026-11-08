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
    fun `joins words hyphenated across line breaks including PDFium and soft hyphens`() {
        assertEquals("trying to keep up", SpeechText.normalize("try-\ning to keep up"))
        assertEquals("password security", SpeechText.normalize("pass-\r\n   word secu\u0002\r\nrity"))
        assertEquals("organization", SpeechText.normalize("organi\u00AD\nzation"))
        assertEquals("implementation", SpeechText.normalize("imple- mentation"))
    }

    @Test
    fun `turns newlines into spaces and collapses whitespace`() {
        assertEquals("one two three", SpeechText.normalize("one  two\n\n  three"))
    }

    @Test
    fun `replaces dashes and ellipsis with speakable forms`() {
        assertEquals("a b, c...", SpeechText.normalize("a \u2014 b, c\u2026"))
    }

    @Test
    fun `cleans PDF replacement chars, parentheses, bullets and numbers for lexicon`() {
        val cleaned = SpeechText.cleanForLexicon("● (by pass\uFFFDphrase 149 - cybercrime)")
        assertEquals("by pass phrase one hundred forty nine cyber crime", cleaned)
    }

    @Test
    fun `assigns distinct pauses for title, subtitle, paragraph end, and mid-sentence page end`() {
        val page = """
            152 THE PHYSICAL SIDE OF CYBER SECURITY
            CHAPTER 4
            Protecting Personal Devices
            Security begins at the physical layer. Locks matter.

            Second paragraph starts here. And continues across the page without punctuation at the end
        """.trimIndent()
        val segments = SpeechText.segments(page)
        assertEquals(6, segments.size)
        assertEquals("CHAPTER four", segments[0].text)
        assertEquals(SpeechText.TITLE_PAUSE_MS, segments[0].pauseMs)
        assertEquals("Protecting Personal Devices", segments[1].text)
        assertEquals(SpeechText.SUBTITLE_PAUSE_MS, segments[1].pauseMs)
        assertEquals("Security begins at the physical layer.", segments[2].text)
        assertEquals(SpeechText.SENTENCE_PAUSE_MS, segments[2].pauseMs)
        assertEquals("Locks matter.", segments[3].text)
        assertEquals(SpeechText.PARAGRAPH_PAUSE_MS, segments[3].pauseMs)
        assertEquals("Second paragraph starts here.", segments[4].text)
        assertEquals(SpeechText.SENTENCE_PAUSE_MS, segments[4].pauseMs)
        assertEquals("And continues across the page without punctuation at the end", segments[5].text)
        assertEquals(SpeechText.MID_SENTENCE_PAGE_PAUSE_MS, segments[5].pauseMs)
    }

    @Test
    fun `stitches hyphenated word across page boundary`() {
        val (cur, next) = SpeechText.stitchCrossPage(
            "152 HEADER\nThey bypassed the pass-\n152",
            "HEADER 153\nword protections easily."
        )
        assertEquals("They bypassed the password", cur.trim())
        assertEquals("protections easily.", next?.trim())
    }
}
