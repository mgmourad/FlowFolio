package com.flowfolio.feature.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.flowfolio.core.pdf.PdfLink
import com.flowfolio.feature.reader.layout.ReaderLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageFramesTest {
    private val screen = IntSize(2000, 1000)
    private fun ready(layout: ReaderLayout, unit: Int) =
        ReaderState.Ready("t", pageCount = 10, layout = layout, unit = unit, unitCount = 6, anchorPage = 0)

    @Test
    fun spreadPagesHugTheSpine() {
        val frames = PageFrames.of(ready(ReaderLayout.SPREAD, unit = 2), screen) { IntSize(800, 900) }
        assertEquals(
            listOf(PageFrames.Frame(3, 200, 50, 800, 900), PageFrames.Frame(4, 1000, 50, 800, 900)),
            frames
        )
    }

    @Test
    fun singlePageIsCentredAndMissingImagesHaveNoFrame() {
        assertEquals(
            listOf(PageFrames.Frame(7, 600, 100, 800, 800)),
            PageFrames.of(ready(ReaderLayout.SINGLE, unit = 7), screen) { IntSize(800, 800) }
        )
        assertTrue(PageFrames.of(ready(ReaderLayout.SINGLE, unit = 7), screen) { null }.isEmpty())
    }

    @Test
    fun tapsFindTheLinkOnTheRightPage() {
        val frames = listOf(PageFrames.Frame(3, 200, 50, 800, 900), PageFrames.Frame(4, 1000, 50, 800, 900))
        val toc = PdfLink(0.1f, 0.1f, 0.5f, 0.2f, targetPage = 41, uri = null)
        val web = PdfLink(0.5f, 0.5f, 0.9f, 0.6f, targetPage = null, uri = "https://example.org")
        val links = mapOf(3 to listOf(toc), 4 to listOf(web))
        assertEquals(toc, PageFrames.linkAt(Offset(200 + 240f, 50 + 135f), frames, links))
        assertEquals(web, PageFrames.linkAt(Offset(1000 + 560f, 50 + 495f), frames, links))
        assertNull(PageFrames.linkAt(Offset(1000 + 100f, 50 + 800f), frames, links))
        // Just outside the box still counts (touch slack), far outside does not.
        assertEquals(toc, PageFrames.linkAt(Offset(200 + 410f, 50 + 135f), frames, links))
        assertNull(PageFrames.linkAt(Offset(200 + 600f, 50 + 135f), frames, links))
    }

    @Test
    fun onlyWebAndMailLinksAreOpenable() {
        assertTrue(PageFrames.isOpenable("https://openstax.org"))
        assertTrue(PageFrames.isOpenable("MAILTO:a@b.c"))
        assertFalse(PageFrames.isOpenable("javascript:alert(1)"))
        assertFalse(PageFrames.isOpenable("file:///sdcard/x"))
        assertFalse(PageFrames.isOpenable("intent://scan/#Intent;end"))
    }
}
