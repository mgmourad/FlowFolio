package com.flowfolio.core.conversion.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which table pictures keep their own colour (the owner's DevOps workbook: icons and dark header rows). */
class InkTest {
    private val w = 200
    private val h = 100

    private fun picture(draw: (IntArray) -> Unit) = IntArray(w * h) { WHITE }.also(draw)

    private fun IntArray.fill(xs: IntRange, ys: IntRange, argb: Int) {
        for (y in ys) for (x in xs) this[y * w + x] = argb
    }

    @Test
    fun blackRulesAndTextAreInk() {
        val p = picture { p ->
            // Rules round the cells, and strokes of text two pixels wide with grey anti-aliasing beside them.
            for (y in listOf(0, 30, 60, 98)) p.fill(0 until w, y..y + 1, BLACK)
            for (x in listOf(0, 100, 198)) p.fill(x..x + 1, 0 until h, BLACK)
            for (x in 10 until 90 step 6) {
                p.fill(x..x + 1, 40 until 52, BLACK)
                p.fill(x + 2..x + 2, 40 until 52, GREY)
            }
        }
        assertFalse(Ink.coloured(p, w, h))
    }

    @Test
    fun aShadedHeaderRowHasColourOfItsOwn() {
        val p = picture { p -> p.fill(0 until w, 0 until 24, DARK_GREY) }
        assertTrue(Ink.coloured(p, w, h))
    }

    @Test
    fun colouredIconsHaveColourOfTheirOwn() {
        val p = picture { p -> for (y in 10 until 90 step 20) p.fill(180 until 188, y until y + 8, GREEN) }
        assertTrue(Ink.coloured(p, w, h))
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
        const val GREY = 0xFF888888.toInt()
        const val DARK_GREY = 0xFF262626.toInt()
        const val GREEN = 0xFF2EA043.toInt()
    }
}
