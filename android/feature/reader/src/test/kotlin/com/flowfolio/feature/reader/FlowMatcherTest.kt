package com.flowfolio.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Finding a highlight's words in the other view's text (ADR-0045): spacing, line-end hyphens and case may differ. */
class FlowMatcherTest {
    @Test
    fun spacingAndCaseDoNotMatter() {
        val text = "Physicists are  currently\r\ntrying to UNIFY the two theories."
        val found = FlowCarrier.Matcher(text).find("physicists are currently trying to unify", 0)
        assertEquals(0, found?.first)
        // The range ends on the last letter in the original text, whatever spacing came before.
        assertEquals(text.indexOf("UNIFY") + 4, found?.last)
    }

    @Test
    fun softHyphensAndPicturesAreSkipped() {
        // A soft hyphen split "rela-tivity" at a line end; a picture (one object character) sits between two words.
        val text = "In summary, rela­tivity says￼that time is relative."
        val found = FlowCarrier.Matcher(text).find("relativity says that", 0)
        assertEquals(text.indexOf("rela"), found?.first)
        assertEquals(text.indexOf("that") + 3, found?.last)
    }

    @Test
    fun searchStartsWhereItIsToldTo() {
        val text = "the force of gravity, and again the force of gravity"
        val second = text.lastIndexOf("the force")
        assertEquals(0, FlowCarrier.Matcher(text).find("the force of", 0)?.first)
        assertEquals(second, FlowCarrier.Matcher(text).find("the force of", 5)?.first)
    }

    @Test
    fun missingOrTooShortQuotesAreNotPlaced() {
        val matcher = FlowCarrier.Matcher("Quantum mechanics deals with the very small.")
        assertNull(matcher.find("string theory", 0))
        // Two letters would match almost anywhere.
        assertNull(matcher.find("th", 0))
        assertNull(matcher.find("  ", 0))
    }
}
