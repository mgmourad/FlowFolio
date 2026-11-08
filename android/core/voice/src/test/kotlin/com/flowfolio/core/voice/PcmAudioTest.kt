package com.flowfolio.core.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class PcmAudioTest {
    @Test
    fun `maps the full float range to signed 16-bit PCM`() {
        val pcm = PcmAudio.toPcm16(floatArrayOf(-1f, 0f, 1f))
        assertEquals(-32767, pcm[0].toInt())
        assertEquals(0, pcm[1].toInt())
        assertEquals(32767, pcm[2].toInt())
    }

    @Test
    fun `clips out-of-range samples to the full-scale limits`() {
        val pcm = PcmAudio.toPcm16(floatArrayOf(2f, -2f))
        assertEquals(32767, pcm[0].toInt())
        assertEquals(-32767, pcm[1].toInt())
    }
}
