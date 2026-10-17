package com.flowfolio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSizeTest {
    private val letter = PageSize(612f, 792f)

    @Test
    fun `portrait page in tall viewport is width-limited`() {
        // Fold cover screen
        assertEquals(PixelSize(1080, 1397), letter.fitInside(1080, 2520))
    }

    @Test
    fun `portrait page in square-ish viewport is height-limited`() {
        // Fold inner screen, single page: fills the height, not the width
        assertEquals(PixelSize(1934, 2504), letter.fitInside(2256, 2504))
    }

    @Test
    fun `half of inner screen for a spread`() {
        assertEquals(PixelSize(1128, 1459), letter.fitInside(1128, 2504))
    }

    @Test
    fun `result never exceeds the viewport`() {
        for (w in listOf(1, 7, 333, 1080, 2256)) {
            for (h in listOf(1, 9, 500, 2504)) {
                val s = PageSize(595.3f, 841.9f).fitInside(w, h)
                assertTrue("$s in $w x $h", s.width in 1..w && s.height in 1..h)
            }
        }
    }

    @Test
    fun `zoom scales from the fitted size`() {
        assertEquals(PixelSize(3240, 4191), letter.atZoom(PixelSize(1080, 1397), 3f))
    }

    @Test
    fun `argb bytes`() {
        assertEquals(1934L * 2504 * 4, PixelSize(1934, 2504).argbBytes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero size is rejected`() {
        PageSize(0f, 10f)
    }
}
