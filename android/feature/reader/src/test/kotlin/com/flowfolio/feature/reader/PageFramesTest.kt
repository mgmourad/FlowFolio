package com.flowfolio.feature.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
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
        val toc = ReaderLink(0.1f, 0.1f, 0.5f, 0.2f, LinkTarget.Page(41))
        val web = ReaderLink(0.5f, 0.5f, 0.9f, 0.6f, LinkTarget.Web("https://example.org"))
        val links = mapOf(3 to listOf(toc), 4 to listOf(web))
        assertEquals(toc, PageFrames.linkAt(Offset(200 + 240f, 50 + 135f), frames, links))
        assertEquals(web, PageFrames.linkAt(Offset(1000 + 560f, 50 + 495f), frames, links))
        assertNull(PageFrames.linkAt(Offset(1000 + 100f, 50 + 800f), frames, links))
        // Just outside the box still counts (touch slack), far outside does not.
        assertEquals(toc, PageFrames.linkAt(Offset(200 + 410f, 50 + 135f), frames, links))
        assertNull(PageFrames.linkAt(Offset(200 + 600f, 50 + 135f), frames, links))
    }

    @Test
    fun linksJumpOpenNotesOrOfferTheWebAndOthersFallThrough() {
        val size = IntSize(1000, 1000)
        val frames = listOf(PageFrames.Frame(0, 0, 0, 1000, 1000))
        fun tapOn(target: LinkTarget, x: Float = 150f) = PageFrames.route(
            Offset(x, 150f),
            size,
            frames,
            mapOf(0 to listOf(ReaderLink(0.1f, 0.1f, 0.2f, 0.2f, target)))
        )
        assertEquals(PageFrames.TapTarget.Jump(7), tapOn(LinkTarget.Page(7)))
        assertEquals(
            PageFrames.TapTarget.Note("c1.xhtml#fn1", Offset(150f, 150f)),
            tapOn(LinkTarget.Note("c1.xhtml#fn1"))
        )
        assertEquals(PageFrames.TapTarget.Web("https://a.org"), tapOn(LinkTarget.Web("https://a.org")))
        // A link the reader won't open is just a tap on the page (here at its edge: a page turn).
        assertEquals(PageFrames.TapTarget.Page, tapOn(LinkTarget.Web("javascript:alert(1)")))
    }

    @Test
    fun pdfLinksBecomeJumpsOrWebLinks() {
        val pdf = com.flowfolio.core.pdf.PdfLink(0f, 0f, 1f, 1f, targetPage = 3, uri = null)
        assertEquals(LinkTarget.Page(3), pdf.toReaderLink()?.target)
        assertEquals(
            LinkTarget.Web("https://a.org"),
            pdf.copy(targetPage = null, uri = "https://a.org").toReaderLink()?.target
        )
        assertNull(pdf.copy(targetPage = null).toReaderLink())
    }

    @Test
    fun theMiddleFortyPercentIsForTapsTheSidesTurnPages() {
        val size = IntSize(1000, 2000)
        assertTrue(isCentre(Offset(301f, 10f), size)) // full height, not just the middle
        assertTrue(isCentre(Offset(699f, 1990f), size))
        assertFalse(isCentre(Offset(299f, 1000f), size))
        assertFalse(isCentre(Offset(701f, 1000f), size))
        val noLinks = emptyMap<Int, List<ReaderLink>>()
        assertEquals(PageFrames.TapTarget.Centre, PageFrames.route(Offset(500f, 100f), size, emptyList(), noLinks))
        assertEquals(PageFrames.TapTarget.Page, PageFrames.route(Offset(100f, 1000f), size, emptyList(), noLinks))
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
