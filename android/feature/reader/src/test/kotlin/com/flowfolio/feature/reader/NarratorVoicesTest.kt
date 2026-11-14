package com.flowfolio.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class NarratorVoicesTest {
    @Test
    fun `an engine's voice name gives its code`() {
        assertEquals("aub", NarratorVoices.codeOf("en-au-x-aub-network"))
        assertEquals("", NarratorVoices.codeOf("en-AU-language"))
    }
}
