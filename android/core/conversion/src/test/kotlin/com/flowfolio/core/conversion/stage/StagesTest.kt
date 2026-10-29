package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.ir.Block
import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.ir.Document
import com.flowfolio.core.conversion.ir.Line
import com.flowfolio.core.conversion.ir.OutlineEntry
import com.flowfolio.core.conversion.ir.Page
import com.flowfolio.core.conversion.ir.PageKind
import com.flowfolio.core.conversion.ir.Span
import com.flowfolio.core.conversion.ir.StyleKey
import com.flowfolio.core.conversion.pdf.Box
import com.flowfolio.core.conversion.pdf.PathInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stage rules the corpus showed were needed (Phase 3, M2), each on a page built by hand. */
class StagesTest {
    private val body = StyleKey("Serif", 10f, bold = false, italic = false, mono = false)
    private val title = StyleKey("Sans", 22f, bold = false, italic = false, mono = false)

    @Test
    fun titlesMatchWhicheverSideTheChapterNumberIsOn() {
        assertTrue(Linking.matches("Welcome to Economics! 1", "Chapter 1 Welcome to Economics!"))
        assertTrue(Linking.matches("1 SAMPLING AND DATA", "Chapter 1 Sampling and Data"))
        assertTrue(Linking.matches("1.1 Physics: Definitions", "1.1 Physics: Definitions and Applications"))
        assertFalse(Linking.matches("Vectors", "Chapter 2 Kinematics"))
    }

    @Test
    fun aLineIsPartOfATitleOnlyWhenItHoldsAWordOfIt() {
        assertTrue(Linking.isPartOf("Before 1492", "Chapter 1 The Americas, Europe, and Africa Before 1492"))
        assertTrue(
            Linking.isPartOf(
                "The Americas, Europe, and Africa",
                "Chapter 1 The Americas, Europe, and Africa Before 1492"
            )
        )
        // A bare chapter number has no letters, so it is never taken for the title.
        assertFalse(Linking.isPartOf("1", "Chapter 1 Patent Basics"))
        assertFalse(Linking.isPartOf("Copyright", "Chapter 1 Patent Basics"))
    }

    @Test
    fun anOutlineEntryNamesOneHeadingOnly() {
        val page = page(0)
        page.blocks = listOf(
            heading("1.1 Definitions of Statistics, Probability, and Key Terms"),
            paragraph("The science of statistics deals with data."),
            heading("Probability")
        )
        Headings.apply(doc(page, OutlineEntry(1, "1.1 Definitions of Statistics, Probability, and Key Terms", 0)))
        assertEquals(listOf(2, 0, 3), page.blocks.map { it.level })
    }

    @Test
    fun aChapterTitleSplitByItsNumberBecomesOneHeading() {
        val page = page(0)
        page.blocks = listOf(heading("The Americas, Europe, and Africa"), paragraph("1"), heading("Before 1492"))
        Headings.apply(doc(page, OutlineEntry(0, "Chapter 1 The Americas, Europe, and Africa Before 1492", 0)))
        assertEquals(1, page.blocks.size)
        assertEquals("The Americas, Europe, and Africa Before 1492", page.blocks.single().text)
        assertEquals(1, page.blocks.single().level)
    }

    @Test
    fun aBarStandingAloneIsDecorationNotAnEquation() {
        val bar = PathInfo(Box(70f, 283f, 542f, 289f), filled = true)
        assertTrue(Shapes.place(page(0, paths = listOf(bar)), emptyList(), emptyList()).equations.isEmpty())
    }

    @Test
    fun glyphsDrawnAsShapesAreOneDisplayEquation() {
        // "x = y/2" set as paths: three marks on a row and a fraction drawn above and below a thin bar.
        val glyphs = listOf(
            Box(200f, 400f, 208f, 410f),
            Box(212f, 403f, 220f, 407f),
            Box(224f, 396f, 232f, 404f),
            Box(222f, 405f, 234f, 405.8f),
            Box(225f, 407f, 231f, 415f)
        ).map { PathInfo(it, filled = true) }
        val placed = Shapes.place(page(0, paths = glyphs), emptyList(), emptyList())
        assertEquals(1, placed.equations.size)
        assertEquals(BlockType.EQUATION, placed.equations.single().type)
    }

    @Test
    fun aChapterTitleOverItsOpeningPhotoStaysOutOfTheFigure() {
        val heading = line("SAMPLING AND DATA", Box(190f, 215f, 390f, 240f), title)
        val label = line("Figure label", Box(300f, 100f, 360f, 110f), body)
        val page = page(0, lines = listOf(heading, label), images = listOf(Box(0f, 60f, 612f, 320f)))
        val regions = Regions.find(page, page.lines) { it.style == title }
        val figure = regions.single { it.type == BlockType.FIGURE }
        assertEquals(listOf(label), figure.lines)
    }

    @Test
    fun aBandHoldingOnlyTheChapterTitleIsNotASidebar() {
        val heading = line("Introduction to Psychology", Box(72f, 47f, 341f, 72f), title)
        val band = PathInfo(Box(0f, 0f, 612f, 100f), filled = true)
        val page = page(0, lines = listOf(heading), paths = listOf(band))
        assertTrue(Regions.find(page, page.lines) { it === heading }.none { it.type == BlockType.SIDEBAR })
    }

    @Test
    fun aTitlesYearsOnTheirOwnLineArePartOfIt() {
        assertTrue(Linking.isPartOf("1660–1763", "Chapter 4 Rule Britannia! The English Empire, 1660–1763"))
        val page = page(0)
        page.blocks = listOf(heading("Rule Britannia! The English Empire,"), paragraph("4"), paragraph("1660–1763"))
        Headings.apply(doc(page, OutlineEntry(0, "Chapter 4 Rule Britannia! The English Empire, 1660–1763", 0)))
        assertEquals("Rule Britannia! The English Empire, 1660–1763", page.blocks.single().text)
    }

    @Test
    fun aLinksUnderlineIsNotAnInlineDrawing() {
        val text = line("in Equation 2.4 and", Box(72f, 408f, 344f, 420f), body)
        val underline = PathInfo(Box(140f, 418f, 190f, 419.1f), filled = true)
        val placed = Shapes.place(page(0, lines = listOf(text), paths = listOf(underline)), listOf(text), emptyList())
        assertTrue(placed.lines.single().inlines.isEmpty())
        assertTrue(placed.equations.isEmpty())
    }

    @Test
    fun anEquationTakesItsNumberAndItsPiecesAlongTheRow() {
        // "D = A + B" drawn as shapes, the D 9 pt apart from the rest, and "2.7" as text at the end of the row.
        val glyphs = listOf(
            Box(141f, 474f, 149f, 485f),
            Box(158f, 475f, 166f, 486f),
            Box(170f, 478f, 178f, 483f),
            Box(182f, 475f, 190f, 486f),
            Box(194f, 478f, 202f, 483f),
            Box(206f, 475f, 214f, 486f)
        ).map { PathInfo(it, filled = true) }
        val number = line("2.7", Box(521f, 471f, 536f, 482f), body)
        val page = page(0, lines = listOf(number), paths = glyphs)
        val placed = Shapes.place(page, page.lines, emptyList())
        assertEquals(1, placed.equations.size)
        assertEquals(listOf(number), placed.equations.single().lines)
        assertTrue(placed.lines.isEmpty())
    }

    @Test
    fun anEquationOfGlyphShapesIsNotAFigure() {
        // Three rows of glyph-sized shapes, dense enough to pass for a drawing by count and area alone.
        val glyphs = (0 until 3).flatMap { row ->
            (0 until 14).map { col ->
                val x = 160f + col * 22f
                val y = 470f + row * 18f
                PathInfo(Box(x, y, x + 10f, y + 11f), filled = true)
            }
        }
        val page = page(0, paths = glyphs)
        assertTrue(Regions.find(page, emptyList()).none { it.type == BlockType.FIGURE })
        assertEquals(1, Shapes.place(page, emptyList(), emptyList()).equations.size)
    }

    @Test
    fun superscriptsAndSubscriptsAreMarked() {
        // "10⁷ m and v₀": body text at 9 pt, its baseline at 100; the small digits raised and lowered.
        val small = StyleKey("Serif", 6f, bold = false, italic = false, mono = false)
        fun span(text: String, left: Float, bottom: Float, style: StyleKey) =
            Span(text, Box(left, bottom - style.size * 1.1f, left + 10f, bottom), style)
        val base = 100f + 9f * 0.21f
        val l = Line(
            listOf(
                span("10", 72f, base, body),
                span("7", 82f, base - 3.5f, small),
                span(" m and v", 88f, base, body),
                span("0", 130f, base + 2.5f, small)
            ),
            Box(72f, 88f, 140f, 103f)
        )
        assertEquals("107 m and v0", l.marked)
        assertEquals("107 m and v0", com.flowfolio.core.conversion.ir.plain(l.marked))
    }

    private fun page(
        index: Int,
        lines: List<Line> = emptyList(),
        images: List<Box> = emptyList(),
        paths: List<PathInfo> = emptyList()
    ) = Page(index, PAGE_W, PAGE_H, PageKind.BORN_DIGITAL, lines, images, paths, lines.sumOf { it.text.length })

    private fun doc(page: Page, vararg outline: OutlineEntry) = Document("Book", null, listOf(page), outline.toList())

    private fun heading(text: String) = Block(BlockType.HEADING, 0, Box(72f, 72f, 540f, 90f), text)

    private fun paragraph(text: String) = Block(BlockType.PARAGRAPH, 0, Box(72f, 100f, 540f, 140f), text)

    private fun line(text: String, box: Box, style: StyleKey) = Line(listOf(Span(text, box, style)), box)

    private companion object {
        const val PAGE_W = 612f
        const val PAGE_H = 792f
    }
}
