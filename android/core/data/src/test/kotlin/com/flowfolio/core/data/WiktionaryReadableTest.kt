package com.flowfolio.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a Wiktionary definition looks like by the time a reader sees it (M5 round 4).
 *
 * Wiktionary ships a stylesheet inside some entries. Stripping tags left its *contents* behind as text, so looking
 * up "of" showed ".mw-parser-output .defdate{font-size:smaller}" under the word.
 */
class WiktionaryReadableTest {
    @Test
    fun `a style block goes, contents and all`() {
        val raw = "Expressing distance or motion. From (a place); off." +
            "<style>.mw-parser-output .defdate{font-size:smaller}</style>"
        assertEquals("Expressing distance or motion. From (a place); off", WiktionaryStore.readable(raw))
    }

    @Test
    fun `css that lost its tags goes too`() {
        val raw = "A word. .mw-parser-output .defdate{font-size:smaller} More of it."
        assertEquals("A word. More of it", WiktionaryStore.readable(raw))
    }

    @Test
    fun `markup is removed and spaces settled`() {
        assertEquals("A bold claim", WiktionaryStore.readable("A <b>bold</b> claim"))
        assertEquals("Spaced out", WiktionaryStore.readable("Spaced   out"))
    }

    @Test
    fun `an ordinary definition is left alone`() {
        val raw = "A strip of wood forming the bottom of a doorway"
        assertEquals(raw, WiktionaryStore.readable(raw))
    }
}
