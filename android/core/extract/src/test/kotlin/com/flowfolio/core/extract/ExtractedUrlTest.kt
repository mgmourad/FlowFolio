package com.flowfolio.core.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractedUrlTest {
    @Test
    fun `an address comes through`() {
        assertEquals("https://h/a.m4a", extractedUrl("https://h/a.m4a"))
        assertEquals("https://h/a.m4a", extractedUrl("  https://h/a.m4a  "))
    }

    /** Python's None prints as "None"; passed on it would reach the player as an address. */
    @Test
    fun `nothing found gives nothing`() {
        assertNull(extractedUrl(null))
        assertNull(extractedUrl("None"))
        assertNull(extractedUrl(""))
        assertNull(extractedUrl("   "))
    }

    /** Anything that is not an address is a mistake somewhere, not something to hand to the player. */
    @Test
    fun `only http addresses are kept`() {
        assertNull(extractedUrl("ERROR: unsupported URL"))
        assertNull(extractedUrl("/data/local/tmp/x.m4a"))
    }
}
