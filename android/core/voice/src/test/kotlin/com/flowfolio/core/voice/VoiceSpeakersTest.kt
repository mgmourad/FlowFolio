package com.flowfolio.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceSpeakersTest {
    @Test
    fun `two default voices are af_heart and am_michael`() {
        assertEquals(listOf("af_heart", "am_michael"), VoiceSpeakers.defaults.map { it.name })
    }

    @Test
    fun `maps Kokoro speaker names to their speaker ids`() {
        assertEquals(3, VoiceSpeakers.idOf("af_heart"))
        assertEquals(16, VoiceSpeakers.idOf("am_michael"))
    }

    @Test
    fun `unknown speaker name resolves to null`() {
        assertNull(VoiceSpeakers.idOf("does_not_exist"))
    }

    @Test
    fun `offers Piper voices as a second source`() {
        val amy = VoiceSpeakers.voiceOf("piper-amy")
        assertEquals(VoiceSource.PIPER, amy?.source)
        assertEquals("amy", amy?.modelKey)
        assertEquals("Alan (Piper · male)", VoiceSpeakers.voiceOf("piper-alan")?.label)
    }
}
