package com.flowfolio.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChangelogTest {
    private val md = """
        # Changelog

        ## [Unreleased]

        > A quote that is dropped.

        ### Added
        - **Search** in the book, with a [link](https://example.com)
          that wraps.
          - A nested `detail`.

        ### Fixed
        - A crash.

        ## [0.6.0-canary.1] - 2026-11-08

        ### Changed
        - Something.
    """.trimIndent()

    @Test
    fun readsVersionsSectionsAndBullets() {
        val all = Changelog.parse(md)
        assertEquals(listOf("Unreleased", "0.6.0-canary.1"), all.map { it.version })
        assertNull(all[0].date)
        assertEquals("2026-11-08", all[1].date)
        val added = all[0].sections[0]
        assertEquals("New", added.heading)
        assertEquals("**Search** in the book, with a link that wraps.", added.lines[0].text)
        assertEquals(1, added.lines[1].depth)
        assertEquals("A nested detail.", added.lines[1].text)
        assertEquals("Fixed", all[0].sections[1].heading)
    }

    @Test
    fun currentFallsBackToTheNewest() {
        val all = Changelog.parse(md)
        assertEquals("0.6.0-canary.1", Changelog.current(all, "0.6.0-canary.1")?.version)
        assertEquals("Unreleased", Changelog.current(all, "9.9.9")?.version)
    }
}
