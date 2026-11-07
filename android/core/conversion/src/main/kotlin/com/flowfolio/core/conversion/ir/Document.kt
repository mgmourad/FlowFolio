package com.flowfolio.core.conversion.ir

import com.flowfolio.core.conversion.pdf.Box

/**
 * The converter's typed intermediate representation (`docs/architecture.md`): each stage reads it and adds to it.
 * Stage B fills [pages] with [Line]s; C sets [styles] and marks artifacts; D groups lines into [Block]s in reading
 * order; F cuts [chapters]; I writes the EPUB from it.
 */
class Document(val title: String, val author: String?, val pages: List<Page>, val outline: List<OutlineEntry>) {
    /** The book's text styles, most used first (Stage C); [Style.role] says what each one is for. */
    var styles: List<Style> = emptyList()

    /** Chapters in reading order (Stage F). */
    var chapters: List<Chapter> = emptyList()
}

/** An outline entry: [depth] from 0, its [title], and the 0-based [page] it points at (-1 when it points nowhere). */
data class OutlineEntry(val depth: Int, val title: String, val page: Int)

/** What kind of page this is (Stage A). Scanned pages are kept as images until Phase 9's OCR. */
enum class PageKind { BORN_DIGITAL, SCANNED, EMPTY }

// Plain data from the PDF and the stages; grouping fields into more classes would add nothing.
@Suppress("LongParameterList")
class Page(
    /** 0-based index in the PDF. */
    val index: Int,
    val width: Float,
    val height: Float,
    val kind: PageKind,
    /** The text lines on the page, in PDFium's order (Stage B). */
    val lines: List<Line>,
    /** Images placed on the page (less the logos and bands repeated in the margins, Stage C). */
    var images: List<Box>,
    /** Vector paths: rules, boxes and drawings. */
    val paths: List<com.flowfolio.core.conversion.pdf.PathInfo>,
    /** Characters on the page that carry text (not generated, not white space): what coverage is measured against. */
    val sourceChars: Int
) {
    /** The printed page number (the PDF's label, else the folio found in the margin by Stage C), or null. */
    var label: String? = null

    /** The page's blocks in reading order (Stage D). */
    var blocks: List<Block> = emptyList()

    /** Share of the page's text found in [blocks] (Stage I's check); 1 when the page has no text. */
    var coverage: Float = 1f

    /**
     * Share of the page's text in lines whose glyph geometry agrees with their declared size (Phase 4,
     * [com.flowfolio.core.conversion.stage.GeometricVerify]); 1 when the page has no text. The pixel analogue of
     * [coverage]: it catches text that is present but laid out from a misread font size (the Packt bug).
     */
    var geometricCoverage: Float = 1f

    /** The whole page went out as an image: its text could not be accounted for, or it is scanned. */
    var fallback: Boolean = false

    /** A cover: a full-page picture with a little text on it, kept as one image rather than converted (Phase 4 M3). */
    var cover: Boolean = false

    /** Drawings shown as images in the text or as display equations ([com.flowfolio.core.conversion.stage.Shapes]). */
    var drawings: Int = 0
}

/**
 * A line of text: its [spans] left to right, and its box. [inlines] are drawings within the line (an equation set as
 * vector shapes), each standing at an [OBJECT] character in [text], in order.
 */
class Line(val spans: List<Span>, val box: Box, val inlines: List<Inline> = emptyList()) {
    val text: String = spans.joinToString("") { it.text }

    /** The style most of the line's characters are set in. */
    val style: StyleKey = spans.groupBy { it.style }.maxBy { (_, s) -> s.sumOf { it.text.length } }.key

    /** A running header, footer or page number (Stage C): recorded, not emitted. */
    var artifact = false

    /**
     * [text] with its superscripts and subscripts marked ([SUP_OPEN] ... [SUP_CLOSE], [SUB_OPEN] ... [SUB_CLOSE]):
     * spans set clearly smaller than the line and raised or lowered from its baseline (10⁷, m², H₂O). The baseline is
     * taken from the line's own style, so a subscript can't pull it down.
     */
    val marked: String by lazy { marked { emptySet() } }

    /**
     * [marked], with the runs [marks] names for their style marked too: code in running text, words set in bold or
     * italics. Runs nest, close before the spaces after them, and spaces between two spans of a run stay in it.
     */
    fun marked(marks: (StyleKey) -> Set<Mark>): String {
        val main = style.size.coerceAtLeast(1f)
        val own = spans.filter { it.style == style && it.text.isNotBlank() }
        val baseline = own.map { it.box.bottom - it.style.size * DESCENT }.sorted().getOrNull(own.size / 2)
        val open = ArrayList<Mark>()
        return buildString {
            for (s in spans) {
                if (s.text.isNotBlank()) change(open, marks(s.style))
                append(scripted(s, main, baseline))
            }
            change(open, emptySet())
        }
    }

    /** Closes the runs not [wanted] (and those opened inside them) before trailing spaces, then opens new ones. */
    private fun StringBuilder.change(open: MutableList<Mark>, wanted: Set<Mark>) {
        if (open.toSet() == wanted) return
        val spaces = length - trimEnd().length
        val tail = substring(length - spaces)
        setLength(length - spaces)
        val kept = open.takeWhile { it in wanted }.size
        while (open.size > kept) append(open.removeAt(open.lastIndex).close)
        append(tail)
        for (m in Mark.entries) {
            if (m in wanted && m !in open) {
                append(m.open)
                open += m
            }
        }
    }

    /** [s]'s text, marked as a superscript or subscript when it is set smaller and raised or lowered. */
    private fun scripted(s: Span, main: Float, baseline: Float?): String {
        val small = s.style.size <= main * SMALL && s.text.isNotBlank() && OBJECT !in s.text
        if (baseline == null || !small) return s.text
        return when {
            s.box.bottom < baseline - main * RAISED -> "$SUP_OPEN${s.text}$SUP_CLOSE"
            s.box.bottom > baseline + main * LOWERED -> "$SUB_OPEN${s.text}$SUB_CLOSE"
            else -> s.text
        }
    }

    private companion object {
        /** Loose character boxes end about a fifth of an em below the baseline. */
        const val DESCENT = 0.21f
        const val SMALL = 0.85f
        const val RAISED = 0.08f
        const val LOWERED = 0.25f
    }
}

/**
 * A drawing within a line of text, shown as an image. Its size is in ems of the line's text, so it scales with the
 * reader's text: [ems] high, [widthEms] wide, its bottom [dropEms] below the text's baseline.
 */
class Inline(val box: Box, val ems: Float, val widthEms: Float, val dropEms: Float) {
    /** The image file, set by Stage I. */
    var image: String? = null
}

/** Stands in the text where an [Inline] is drawn. */
const val OBJECT = '\uFFFC'

/** Mark superscripts and subscripts in a block's text ([Line.marked]); Stage I writes them as `<sup>` and `<sub>`. */
const val SUP_OPEN = '\uE001'
const val SUP_CLOSE = '\uE002'
const val SUB_OPEN = '\uE003'
const val SUB_CLOSE = '\uE004'

/** Mark a run of code in a block's text ([Line.marked]); Stage I writes it as `<code>`. */
const val CODE_OPEN = '\uE005'
const val CODE_CLOSE = '\uE006'

/** Mark words set in bold or italics within a block's text; Stage I writes them as `<strong>` and `<em>`. */
const val BOLD_OPEN = '\uE007'
const val BOLD_CLOSE = '\uE008'
const val ITALIC_OPEN = '\uE009'
const val ITALIC_CLOSE = '\uE00A'

/** A run marked in a block's text ([Line.marked]), by the characters that open and close it. */
enum class Mark(val open: Char, val close: Char) {
    CODE(CODE_OPEN, CODE_CLOSE),
    BOLD(BOLD_OPEN, BOLD_CLOSE),
    ITALIC(ITALIC_OPEN, ITALIC_CLOSE)
}

/** [text] without its marks (superscripts, code, bold...): for alt text and anything compared as plain text. */
fun plain(text: String): String = text.filter { it !in SUP_OPEN..ITALIC_CLOSE }

/** A run of characters in one style. */
class Span(
    val text: String,
    val box: Box,
    val style: StyleKey,
    /** Where each character of [text] starts (x, points); a space the converter added starts where the last ended. */
    val lefts: FloatArray = FloatArray(0)
) {
    /** The index of the first character starting at or right of [x], or [text]'s length. */
    fun indexAt(x: Float): Int = lefts.indexOfFirst { it >= x }.let { if (it < 0) text.length else it }

    /** This span cut at character [at]: the part before and the part from it. */
    fun cut(at: Int): Pair<Span, Span> {
        val mid = lefts.getOrElse(at) { box.right }
        return Span(text.substring(0, at), Box(box.left, box.top, mid, box.bottom), style, lefts.copyOfRange(0, at)) to
            Span(text.substring(at), Box(mid, box.top, box.right, box.bottom), style, lefts.copyOfRange(at, lefts.size))
    }
}

/**
 * A text style as the PDF sets it: font family (without its subset prefix), size to the half point, weight and slant.
 * Two spans with equal keys look the same on the page.
 */
data class StyleKey(val family: String, val size: Float, val bold: Boolean, val italic: Boolean, val mono: Boolean)

/** What a style is used for in this book (Stage C). */
enum class Role { BODY, HEADING, CAPTION, SMALL, CODE, OTHER }

/**
 * A [key] with its role and, for headings, its level (1 the largest). [advance] is a code style's character width in
 * points (its columns), 0 for others.
 */
data class Style(val key: StyleKey, val role: Role, val level: Int, val chars: Int, val advance: Float = 0f)

/** A block of the page in reading order (Stage D). */
// Plain data from the PDF and the stages; grouping fields into more classes would add nothing.
@Suppress("LongParameterList")
class Block(
    val type: BlockType,
    /** 0-based page index. */
    val page: Int,
    val box: Box,
    /** The block's text (a list's items are [items]); for figures and fallbacks, the text inside them, for alt. */
    var text: String,
    var level: Int = 0,
    val items: List<String> = emptyList(),
    /** For figures and fallbacks: the region of the page drawn as the image. */
    val region: Box? = null,
    /** Characters of the page the block accounts for. */
    val chars: Int = 0,
    val confidence: Float = 1f,
    val method: String = "geometry",
    /** Drawings within the text, one per [OBJECT] character in [text], in order. */
    val inlines: List<Inline> = emptyList(),
    /** A list's items with their markers and depth ([items] holds their text alone). */
    val listItems: List<ListItem> = emptyList()
) {
    /** `p{page:04d}-b{index:03d}`, set once the page's blocks are in order. */
    var id: String = ""

    /** The block on an earlier page this one continues (a paragraph broken by a page turn), or null. */
    var continuesFrom: String? = null

    /** The image file the block shows (figures and fallbacks), set by Stage I. */
    var image: String? = null

    /** Its image has colour of its own (icons, shaded cells), set by Stage I: a table so is not drawn in ink. */
    var coloured: Boolean = false
}

/**
 * A list item: its [text], its [marker] as printed ("•", "3.", "b)"; bullets drawn as glyphs or shapes become "•") and
 * how deep it sits ([level], 0 the outermost list).
 */
class ListItem(val text: String, val marker: String, val level: Int) {
    /** A numbered or lettered item ("3.", "b)", "iv."), not a bullet. */
    val ordered: Boolean get() = ORDERED.matches(marker)

    private companion object {
        val ORDERED = Regex("^\\(?([0-9]{1,3}|[a-zA-Z]|[ivxlcIVXLC]{1,6})[.)]$")
    }
}

/** Block types, as `docs/conversion-spec.md` names them in `flowfolio.json`. */
enum class BlockType(val json: String) {
    HEADING("heading"),
    PARAGRAPH("paragraph"),
    LIST("list"),
    FIGURE("figure"),
    CAPTION("caption"),
    TABLE("table"),
    EQUATION("equation"),
    SIDEBAR("sidebar"),
    CODE("code"),
    FALLBACK("fallback")
}

/** A chapter: its title and the blocks it holds, by page and index, in reading order. */
class Chapter(val title: String, val blocks: List<Block>, val file: String) {
    /** Pages whose start falls in this chapter, for the page-break markers and the page list. */
    var pages: List<Int> = emptyList()
}
