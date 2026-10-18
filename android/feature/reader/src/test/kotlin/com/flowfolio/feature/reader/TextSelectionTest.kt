package com.flowfolio.feature.reader

import androidx.compose.ui.unit.IntSize
import com.flowfolio.core.pdf.PageText
import com.flowfolio.feature.reader.layout.ReaderLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextSelectionTest {
    /** Two lines: "Drag force" then "is fast", with a generated line break (no box) between them. */
    private val page: PageText = run {
        val text = "Drag force\r\nis fast"
        val boxes = FloatArray(text.length * 4) { -1f }
        fun box(i: Int, x: Float, top: Float) {
            boxes[i * 4] = x
            boxes[i * 4 + 1] = top
            boxes[i * 4 + 2] = x + 0.02f
            boxes[i * 4 + 3] = top + 0.03f
        }
        "Drag force".indices.forEach { i -> box(i, 0.1f + i * 0.02f, 0.10f) }
        "is fast".indices.forEach { i -> box(12 + i, 0.1f + i * 0.02f, 0.15f) }
        PageText(text, boxes)
    }

    @Test
    fun findsTheCharacterUnderAPointOrNearbyOnItsLine() {
        assertEquals(2, TextSelection.charAt(page, 0.15f, 0.11f))
        assertEquals(9, TextSelection.charAt(page, 0.31f, 0.11f))
        assertNull(TextSelection.charAt(page, 0.15f, 0.5f))
    }

    @Test
    fun wordAroundACharacter() {
        assertEquals(5..9, TextSelection.wordAt(page, 7))
        assertEquals(4..4, TextSelection.wordAt(page, 4))
    }

    @Test
    fun oneRectanglePerLine() {
        val rects = TextSelection.rects(page, 5..15)
        assertEquals(2, rects.size)
        assertEquals(0.20f, rects[0].left, 1e-4f)
        assertEquals(0.15f, rects[1].top, 1e-4f)
    }

    @Test
    fun quoteJoinsLinesAndHyphenatedWords() {
        assertEquals("force is", TextSelection.quote(page, 5..13))
        val hyphen = PageText("termi-\r\nnal", FloatArray(11 * 4))
        assertEquals("terminal", TextSelection.quote(hyphen, 0..10))
    }

    @Test
    fun rightToLeftMirrorsPageFrames() {
        val ready = ReaderState.Ready("t", 10, ReaderLayout.SPREAD, 2, 6, 3)
        val size = IntSize(1000, 800)
        val ltr = PageFrames.of(ready, size) { IntSize(400, 600) }
        val rtl = PageFrames.of(ready, size, rightToLeft = true) { IntSize(400, 600) }
        // The spread's first page sits on the right when reading right to left.
        assertEquals(ltr.first().page, rtl.first().page)
        assertEquals(1000 - ltr.first().x - 400, rtl.first().x)
    }
}
