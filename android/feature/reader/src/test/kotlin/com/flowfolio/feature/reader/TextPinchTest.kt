package com.flowfolio.feature.reader

import com.flowfolio.core.model.ReaderText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pinching an EPUB page sets the text size (M3). */
class TextPinchTest {
    @Test
    fun aPinchScalesTheSizeWithinItsLimits() {
        assertEquals(18, TextPinch.sizeFor(18, 1f))
        assertEquals(27, TextPinch.sizeFor(18, 1.5f))
        assertEquals(ReaderText.MAX_SIZE_SP, TextPinch.sizeFor(18, 5f))
        assertEquals(ReaderText.MIN_SIZE_SP, TextPinch.sizeFor(18, 0.2f))
    }

    @Test
    fun theSizeIsSetOnceWhenTheFingersLiftAndOnlyIfItChanged() {
        val set = ArrayList<Int>()
        val pinch = TextPinch { set += it }
        pinch.begin(18)
        pinch.pinch(1.2f)
        pinch.begin(30) // already pinching: ignored
        pinch.pinch(1.1f)
        assertEquals(24, pinch.preview)
        pinch.end()
        assertNull(pinch.preview)
        assertEquals(listOf(24), set)
        pinch.begin(24)
        pinch.pinch(1.01f)
        pinch.end()
        assertEquals("a pinch too small to change the size sets nothing", listOf(24), set)
    }
}
