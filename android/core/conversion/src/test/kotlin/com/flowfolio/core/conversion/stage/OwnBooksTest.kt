package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.ir.BOLD_CLOSE
import com.flowfolio.core.conversion.ir.BOLD_OPEN
import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.ir.CODE_CLOSE
import com.flowfolio.core.conversion.ir.CODE_OPEN
import com.flowfolio.core.conversion.ir.Document
import com.flowfolio.core.conversion.ir.Line
import com.flowfolio.core.conversion.ir.Mark
import com.flowfolio.core.conversion.ir.Page
import com.flowfolio.core.conversion.ir.PageKind
import com.flowfolio.core.conversion.ir.Role
import com.flowfolio.core.conversion.ir.Span
import com.flowfolio.core.conversion.ir.Style
import com.flowfolio.core.conversion.ir.StyleKey
import com.flowfolio.core.conversion.pdf.Box
import com.flowfolio.core.conversion.pdf.PathInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules the owner's own books showed were needed (Phase 3, Dynamic Flow round on the phone): notes exported from
 * Word, a book of notes typed in a code font, an O'Reilly title. Each on a page built by hand.
 */
class OwnBooksTest {
    private val calibri = StyleKey("Calibri", 14f, bold = false, italic = false, mono = false)
    private val calibriBold = StyleKey("Calibri", 14f, bold = true, italic = false, mono = false)
    private val courier = StyleKey("CourierNewPSMT", 14f, bold = false, italic = false, mono = true)
    private val styles = listOf(
        Style(calibri, Role.BODY, 0, 10_000),
        Style(calibriBold, Role.HEADING, 3, 500),
        Style(courier, Role.CODE, 0, 100, advance = 8.4f)
    )

    @Test
    fun paragraphsBreakWhereTheLineStepWidensNotWhereBoxesLeaveGaps() {
        // Word's boxes here are as tall as the leading (17 pt boxes, 18.6 pt apart): the gap between paragraphs
        // (9 pt) is small for the box, but the step (26 pt) is wider than the page's usual one.
        val tops = listOf(252f, 270.6f, 289.2f, 307.8f, 334f, 352.6f)
        val lines = tops.mapIndexed { i, top -> line("Line $i of the text.", Box(72f, top, 500f, top + 17f), calibri) }
        val blocks = Blocks(styles).of(page(lines), lines.map { Item.Text(it) })
        assertEquals(listOf(4, 2), blocks.map { b -> lines.count { b.text.contains(it.text) } })
    }

    @Test
    fun tightBoxesAtTheUsualStepStayOneParagraph() {
        // Boxes tight to the glyphs (7 pt) leave gaps wider than three quarters of a line; the step is the usual one.
        val lines = (0 until 5).map { i ->
            line(
                "Line $i of the text.",
                Box(
                    72f,
                    100f + i * 13.4f,
                    500f,
                    107f + i * 13.4f
                ),
                calibri
            )
        }
        val blocks = Blocks(styles).of(page(lines), lines.map { Item.Text(it) })
        assertEquals(1, blocks.size)
    }

    @Test
    fun emojiBulletsAreKeptAsTheItemsMarkers() {
        val lines = listOf("✅ Git – Version Control", "✅ Docker – Containers", "🔹 Quick & Easy Access")
            .mapIndexed { i, t -> line(t, Box(72f, 100f + i * 18.6f, 400f, 117f + i * 18.6f), calibri) }
        val list = Blocks(styles).of(page(lines), lines.map { Item.Text(it) }).filter { it.type == BlockType.LIST }
        val items = list.flatMap { it.listItems }
        assertEquals(listOf("✅", "✅", "🔹"), items.map { it.marker })
        assertEquals("Quick & Easy Access", items.last().text)
    }

    @Test
    fun wordsSecondLevelBulletIsAnOInCourierNew() {
        val first = line("After choosing the OS, choose:", Box(90f, 100f, 400f, 117f), calibri)
        val o = line("o", Box(126f, 119f, 133f, 136f), courier)
        val item = line("Name: up to 15 characters", Box(144f, 119f, 400f, 136f), calibri)
        val bullet = line("• ", Box(72f, 100f, 80f, 117f), calibri)
        val lines = listOf(Line(bullet.spans + first.spans, bullet.box.union(first.box)), o, item)
        val blocks = Blocks(styles).of(page(lines), lines.map { Item.Text(it) })
        val list = blocks.single { it.type == BlockType.LIST }
        assertEquals(listOf(0, 1), list.listItems.map { it.level })
        assertEquals("Name: up to 15 characters", list.listItems.last().text)
        assertTrue(blocks.none { it.type == BlockType.CODE })
    }

    @Test
    fun wordsBrokenAtALineEndAreJoinedAgain() {
        assertTrue(Blocks.brokenWord("magni-", "tude"))
        assertFalse(Blocks.brokenWord("Azure-", "AD"))
        assertTrue(Blocks.brokenWord("Java‐", "Script"))
        assertTrue(Blocks.brokenWord("distribu­", "tion"))
        assertFalse(Blocks.brokenWord("1-", "2"))
    }

    @Test
    fun aNameInCodeBrokenOverTwoLinesIsOneName() {
        val code: (StyleKey) -> Set<Mark> = { if (it == courier) setOf(Mark.CODE) else emptySet() }
        val a = Line(
            listOf(span("a ", 72f, calibri), span("Deploy", 90f, courier)),
            Box(72f, 100f, 150f, 117f)
        )
        val b = Line(listOf(span("ment", 72f, courier), span(" object", 110f, calibri)), Box(72f, 119f, 160f, 136f))
        assertEquals("a ${CODE_OPEN}Deployment$CODE_CLOSE object", Blocks.join(listOf(a, b), code))
        // Commands stay two words.
        val c = Line(listOf(span("kubectl", 72f, courier)), Box(72f, 100f, 130f, 117f))
        val d = Line(listOf(span("apply", 72f, courier)), Box(72f, 119f, 120f, 136f))
        assertEquals("${CODE_OPEN}kubectl$CODE_CLOSE ${CODE_OPEN}apply$CODE_CLOSE", Blocks.join(listOf(c, d), code))
    }

    @Test
    fun boldWordsInProseAreMarked() {
        val l = Line(
            listOf(
                span("Welcome to the ", 72f, calibri),
                span("Workbook", 160f, calibriBold),
                span(" guide", 230f, calibri)
            ),
            Box(72f, 100f, 300f, 117f)
        )
        val marks: (StyleKey) -> Set<Mark> = { if (it.bold) setOf(Mark.BOLD) else emptySet() }
        assertEquals("Welcome to the ${BOLD_OPEN}Workbook$BOLD_CLOSE guide", l.marked(marks))
    }

    @Test
    fun twoTablesOneUnderTheOtherStayApart() {
        // Word draws each cell's borders. Two tables of two columns, a bullet line between them: the rules line up,
        // but the side borders stop between the tables.
        fun table(top: Float): List<PathInfo> {
            val rows = listOf(top, top + 14f, top + 41f)
            val rules = rows.flatMap { y -> listOf(Box(108f, y, 322f, y + 0.5f), Box(323f, y, 540f, y + 0.5f)) }
            val sides = listOf(108f, 322f, 540f).flatMap { x ->
                rows.zipWithNext().map { (a, b) -> Box(x, a, x + 0.5f, b) }
            }
            return (rules + sides).map { PathInfo(it, filled = true) }
        }
        val cells = listOf(200f, 257f).flatMap { top ->
            listOf(
                line("Pros", Box(114f, top + 2f, 133f, top + 13f), calibri),
                line("Cons", Box(328f, top + 2f, 350f, top + 13f), calibri),
                line("more control", Box(150f, top + 16f, 247f, top + 27f), calibri),
                line("maintenance", Box(364f, top + 16f, 462f, top + 27f), calibri)
            )
        }
        val paas = line("PaaS", Box(108f, 244f, 129f, 255f), calibri)
        val lines = cells + paas
        val page = Page(0, 612f, 792f, PageKind.BORN_DIGITAL, lines, emptyList(), table(200f) + table(257f), 100)
        val tables = Regions.find(page, lines).filter { it.type == BlockType.TABLE }
        assertEquals(2, tables.size)
        assertTrue(tables.none { paas in it.lines })
    }

    @Test
    fun aBookOfNotesInACodeFontHasItsProseAsBody() {
        // More characters of code (Menlo, on shaded panels) than of prose (JetBrains Mono): the prose is the body.
        val prose = StyleKey("JetBrainsMonoNFM", 14f, bold = false, italic = false, mono = true)
        val menlo = StyleKey("Menlo", 12f, bold = false, italic = false, mono = true)
        val pages = (0 until 3).map { n ->
            val text = (0 until 4).map { i ->
                line(
                    "Prose about pipelines, line $i.",
                    Box(
                        52f,
                        400f + i * 18f,
                        500f,
                        414f + i * 18f
                    ),
                    prose
                )
            }
            val code = (0 until 8).map { i ->
                line(
                    "Invoke-ScriptAnalyzer -Path .\\script.ps1 $i",
                    Box(64f, 200f + i * 15f, 520f, 211f + i * 15f),
                    menlo
                )
            }
            val panel = PathInfo(Box(52f, 190f, 540f, 330f), filled = true)
            Page(n, 595f, 842f, PageKind.BORN_DIGITAL, text + code, emptyList(), listOf(panel), 1000)
        }
        val doc = Document("Notes", null, pages, emptyList())
        DesignSystem.apply(doc)
        val roles = doc.styles.associate { it.key to it.role }
        assertEquals(Role.BODY, roles[prose])
        assertEquals(Role.CODE, roles[menlo])
    }

    private fun page(lines: List<Line>) =
        Page(0, 595f, 842f, PageKind.BORN_DIGITAL, lines, emptyList(), emptyList(), 100)

    private fun line(text: String, box: Box, style: StyleKey) = Line(listOf(Span(text, box, style)), box)

    private fun span(text: String, left: Float, style: StyleKey) =
        Span(text, Box(left, 100f, left + text.length * 7f, 117f), style)
}
