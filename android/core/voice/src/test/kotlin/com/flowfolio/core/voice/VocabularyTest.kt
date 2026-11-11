package com.flowfolio.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The packed word list answers exactly as a set of the same words would. */
class VocabularyTest {
    @Test
    fun knowsEveryWordAndNothingElse() {
        val words = listOf("b", "ab", "a", "abc", "Zebra", "kubernetes", "abcd", "ábaco")
        val vocab = Vocabulary(words + "ab")
        assertEquals(words.size, vocab.size)
        for (w in words) assertTrue(w, vocab.knows(w))
        for (w in listOf("", "c", "abce", "zebr", "zebras", "aa", "kubernete", "abacо")) assertFalse(w, vocab.knows(w))
    }

    @Test
    fun ignoresCase() {
        assertTrue(Vocabulary(listOf("nist")).knows("NIST"))
    }

    @Test
    fun anEmptyListKnowsNothing() {
        assertFalse(Vocabulary(emptyList()).knows("a"))
    }
}
