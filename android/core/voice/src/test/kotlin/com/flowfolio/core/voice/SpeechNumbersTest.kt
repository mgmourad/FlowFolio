package com.flowfolio.core.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/** Numbers, symbols and abbreviations are read for what they mean, not dropped or read as stray digits. */
class SpeechNumbersTest {
    private fun clean(text: String) = SpeechText.cleanForLexicon(text)

    @Test
    fun cardinals() {
        assertEquals("zero", SpeechNumbers.cardinal(0))
        assertEquals("one hundred forty nine", SpeechNumbers.cardinal(149))
        assertEquals("ten thousand", SpeechNumbers.cardinal(10_000))
        assertEquals("two million five hundred thousand", SpeechNumbers.cardinal(2_500_000))
        assertEquals("one billion", SpeechNumbers.cardinal(1_000_000_000))
    }

    @Test
    fun thousandsSeparatorsAreOneNumber() {
        assertEquals("ten thousand servers", clean("10,000 servers"))
    }

    @Test
    fun currencyAndPercent() {
        assertEquals("ten thousand dollars rose forty five percent", clean("$10,000 rose 45%"))
        assertEquals("two million dollars", clean("$2 million"))
    }

    @Test
    fun yearsAreReadAsYears() {
        assertEquals("in nineteen ninety nine and twenty twenty three", clean("in 1999 and 2023"))
        assertEquals("in two thousand five", clean("in 2005"))
    }

    @Test
    fun decimalsAndVersions() {
        assertEquals("two point five", clean("2.5"))
        assertEquals("version two point zero point one", clean("version 2.0.1"))
    }

    @Test
    fun ordinals() {
        assertEquals("first second third twenty first", clean("1st 2nd 3rd 21st"))
    }

    @Test
    fun rangesSayTo() {
        assertEquals("nineteen ninety to two thousand", clean("1990-2000"))
    }

    @Test
    fun symbolsBecomeWords() {
        assertEquals("R and D in C plus plus", clean("R&D in C++"))
    }

    @Test
    fun linksAreReadAsTheirAddress() {
        assertEquals("learn dot hashicorp dot com", clean("https://learn.hashicorp.com/tutorials/packer"))
    }

    @Test
    fun abbreviationsAreExpandedAndDoNotEndASentence() {
        val spoken = SpeechText.segments("Use tools, e.g. Docker, etc. and ask Dr. Smith about it.")
            .joinToString(" ") { it.text }
        assertEquals("Use tools, for example Docker, et cetera and ask Doctor Smith about it.", spoken)
    }

    @Test
    fun initialsDoNotEndASentence() {
        val segments = SpeechText.segments("Written by J. R. R. Tolkien in England.")
        assertEquals(1, segments.size)
    }
}
