package com.flowfolio.core.reflow

import org.junit.Assert.assertEquals
import org.junit.Test

/** Island markup kept packed in memory (M3 EPUB memory). */
class PackedTextTest {
    @Test
    fun unpacksExactlyWhatWasPacked() {
        val math = "<math xmlns=\"http://www.w3.org/1998/Math/MathML\"><mi>∑</mi><mo>≤</mo>" +
            "<mn>3</mn></math>".repeat(200)
        val text = PackedText(math)
        assertEquals(math, text.text())
        text.pack()
        assertEquals(math, text.text())
        text.pack() // twice is harmless
        assertEquals(math, text.text())
        assertEquals("", PackedText("").apply { pack() }.text())
    }
}
