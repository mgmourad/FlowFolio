package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.ir.Block
import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.ir.CODE_CLOSE
import com.flowfolio.core.conversion.ir.CODE_OPEN
import com.flowfolio.core.conversion.ir.ITALIC_CLOSE
import com.flowfolio.core.conversion.ir.Line
import com.flowfolio.core.conversion.ir.ListItem
import com.flowfolio.core.conversion.ir.Mark
import com.flowfolio.core.conversion.ir.OBJECT
import com.flowfolio.core.conversion.ir.Page
import com.flowfolio.core.conversion.ir.Role
import com.flowfolio.core.conversion.ir.SUP_OPEN
import com.flowfolio.core.conversion.ir.Span
import com.flowfolio.core.conversion.ir.Style
import com.flowfolio.core.conversion.ir.StyleKey
import com.flowfolio.core.conversion.ir.plain
import com.flowfolio.core.conversion.pdf.Box
import com.flowfolio.core.conversion.stage.Regions.containsCentre
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Stage D, part three: blocks. Lines in reading order join into one block while they keep the same role and level,
 * follow at the usual line spacing and line up on the left; a list marker, a first-line indent or a gap starts a new
 * one. A list item's lines that hang under its text continue it, and consecutive items become one list, nested by how
 * far their markers sit in. Code keeps its lines: each character goes back to its column, so indentation and text
 * drawn with characters (a chart in a code block) survive. Lines set mostly in math fonts become an equation, shown as
 * an image of the page this phase (Phase 5 converts them). Regions become figures, tables and sidebars.
 */
// One stage: its rules belong together.
@Suppress("TooManyFunctions")
class Blocks(styles: List<Style>) {
    private val byKey = styles.associateBy { it.key }

    /** The body text's size: a heading set at it is a heading by weight alone, so it must read like one. */
    private val bodySize = styles.filter { it.role == Role.BODY }.maxByOrNull { it.chars }?.key?.size ?: 0f

    /** Where each list item's marker was, for nesting (one page at a time). */
    private val markers = IdentityHashMap<Block, Marker>()

    /** The page being read. */
    private var page: Page? = null

    /** Where the page's text column ends on the right (its longest body lines). */
    private var rightEdge = 0f
    private var pitch: Map<Float, Float> = emptyMap()
    private var leftEdge = 0f

    fun of(page: Page, items: List<Item>): List<Block> {
        val out = ArrayList<Block>()
        val run = ArrayList<Line>()
        markers.clear()
        this.page = page
        rightEdge =
            page.lines.filter { !it.artifact && byKey[it.style]?.role == Role.BODY }.maxOfOrNull { it.box.right }
                ?: page.width
        pitch = pitches(page)
        leftEdge = page.lines.filter { !it.artifact && byKey[it.style]?.role == Role.BODY }.minOfOrNull { it.box.left }
            ?: 0f
        fun flush() {
            if (run.isNotEmpty()) out += textBlocks(page, run.toList())
            run.clear()
        }
        for (item in withMarkersJoined(withTables(page, items))) {
            when (item) {
                is Item.Area -> {
                    flush()
                    out += regionBlock(page, item.region)
                }

                is Item.Text -> {
                    if (run.isNotEmpty() && breaks(run.first(), run.last(), item.line)) flush()
                    run += item.line
                }
            }
        }
        flush()
        return lists(out)
    }

    /**
     * A line's role: math, code when nearly all its characters are in a code font (a line of prose holding a long
     * command is prose, the command marked as code in it), else its main style's.
     */
    private fun kindOf(line: Line): Pair<Role, Int> = when {
        isMath(line) -> Role.OTHER to 0

        isCode(line) -> Role.CODE to 0

        else -> byKey[line.style]?.let { s ->
            // A line of prose with a few words in bold is prose, the words marked bold in it; so is a line set
            // mostly in bold at the body's size that runs across the column, inside a paragraph.
            val prose = s.role == Role.CODE ||
                (s.role == Role.HEADING && (headingShare(line) < HEADING_LINE_SHARE || boldProse(line)))
            if (prose) Role.BODY to 0 else s.role to s.level
        } ?: (Role.BODY to 0)
    }

    /** [line] is set at the body's size and runs most of the way across the column: no heading is that long. */
    private fun boldProse(line: Line): Boolean {
        val size = body?.size ?: return false
        return line.style.size <= size * SAME_SIZE && rightEdge > leftEdge &&
            line.box.width >= (rightEdge - leftEdge) * PROSE_WIDTH
    }

    /** The share of [line]'s characters set in a heading style. */
    private fun headingShare(line: Line): Float {
        val all = line.spans.sumOf { s -> s.text.count { !it.isWhitespace() } }
        // Code in a heading ("The in operator") is part of it.
        val heading = line.spans.filter { byKey[it.style]?.role.let { r -> r == Role.HEADING || r == Role.CODE } }
            .sumOf { s -> s.text.count { !it.isWhitespace() } }
        return if (all == 0) 0f else heading.toFloat() / all
    }

    private fun isCode(line: Line): Boolean {
        var code = 0
        var all = 0
        for (s in line.spans) {
            // An emoji in a listing ("Result: ✅ PASS") comes from an emoji font: it counts neither way.
            val n = s.text.count { !it.isWhitespace() && !it.isSurrogate() && it.code !in EMOJI_BMP }
            all += n
            if (isCodeStyle(s.style)) code += n
        }
        return all > 0 && code >= all * CODE_LINE_SHARE
    }

    private fun isCodeStyle(key: StyleKey) = byKey[key]?.role == Role.CODE

    /** Lines as one text, with code, bold and italics in them marked. */
    private fun join(lines: List<Line>) = join(lines, ::marksOf)

    /** The book's body text style: bold and italics are marked where they differ from it. */
    private val body: StyleKey? = styles.filter { it.role == Role.BODY }.maxByOrNull { it.chars }?.key

    /** What a span in [key] is marked as: code; else bold or italic where the body text isn't. */
    private fun marksOf(key: StyleKey): Set<Mark> = when {
        isCodeStyle(key) -> setOf(Mark.CODE)

        else -> buildSet {
            if (key.bold && body?.bold == false) add(Mark.BOLD)
            if (key.italic && body?.italic == false) add(Mark.ITALIC)
        }
    }

    /** [next] starts a new block after [prev], in a block that started with [first]. */
    // Page geometry: each condition is one case of the layout, kept together to read as one rule.
    @Suppress("ReturnCount", "CyclomaticComplexMethod", "ComplexCondition")
    private fun breaks(first: Line, prev: Line, next: Line): Boolean {
        val height = prev.box.height.coerceAtLeast(1f)
        val gap = next.box.top - prev.box.bottom
        // Code runs on whatever its indentation, through blank lines; a wide gap ends a listing.
        if (isCode(prev) && isCode(next)) return endsListing(prev, next)
        val em = prev.style.size.coerceAtLeast(1f)
        val spaced = spacedApart(prev, next, gap, height, em)
        val nextMarker = markerOf(next) != null
        // A list item's further lines hang under its text, past its marker: they continue it.
        val item = markerOf(first)
        if (item != null && !nextMarker && kindOf(next) == kindOf(prev) && gap >= -height && !spaced &&
            abs(next.box.left - item.textLeft) <= em * HANG_SLACK_EMS
        ) {
            return false
        }
        val body = kindOf(next).first == Role.BODY
        val indented = next.box.left > prev.box.left + em * INDENT_EMS
        val outdented = next.box.left < prev.box.left - em * INDENT_EMS * 2
        // A hanging indent (a glossary entry, a reference): the block's first line runs to the column's edge and the
        // next is set in under it. A paragraph ending before a new indented one stops short of the edge.
        if (body && indented && !nextMarker && prev === first && kindOf(prev) == kindOf(next) && !spaced &&
            prev.box.right >= rightEdge - em * FULL_LINE_EMS
        ) {
            return false
        }
        // A heading ends where its style does: a section title and the label under it are two headings.
        val heading = kindOf(next).first == Role.HEADING
        return kindOf(prev) != kindOf(next) ||
            (heading && prev.style != next.style) ||
            spaced || gap < -height * 2 ||
            (body && (indented || outdented)) ||
            (body && endsParagraph(prev, next, em)) ||
            nextMarker
    }

    /**
     * [next] is set apart from [prev] by more than a line's leading: the step from line to line is wider than the
     * page's usual one at that size. Line boxes are no guide to the leading (Word's are as tall as the leading, or
     * tight to the glyphs), so the gap against the line's height decides only on a page with no usual step, or
     * between sizes, or where the usual step is too wide to be a line's (a page of one-line paragraphs).
     */
    private fun spacedApart(prev: Line, next: Line, gap: Float, height: Float, em: Float): Boolean {
        val usual = pitch[prev.style.size]?.takeIf { it <= em * MAX_PITCH_EMS }
        if (usual == null || prev.style.size != next.style.size) return gap > height * MAX_LINE_GAP
        return step(prev, next) > usual * PARAGRAPH_STEP
    }

    /** From [a] down to [b]: the smaller of the steps between their tops and their bottoms, so a raised or lowered
     * mark (a superscript) in one line doesn't widen it. */
    private fun step(a: Line, b: Line) = minOf(b.box.top - a.box.top, b.box.bottom - a.box.bottom)

    /**
     * A paragraph set with no space or indent after it: [prev] ends a sentence well short of [next], which starts a
     * new one and runs to the column's edge.
     */
    private fun endsParagraph(prev: Line, next: Line, em: Float): Boolean {
        val aligned = kindOf(prev) == kindOf(next) && abs(next.box.left - prev.box.left) <= em * INDENT_EMS
        val short = prev.box.right <= next.box.right - em * SHORT_LINE_EMS
        val full = next.box.right >= rightEdge - em * FULL_LINE_EMS
        val start = plain(next.text).trimStart().firstOrNull()
        val opens = start != null && (start.isUpperCase() || start.isDigit())
        return aligned && short && full && opens && SENTENCE_END.containsMatchIn(plain(prev.text).trimEnd())
    }

    /** The usual [step] from one line to the next on this page, for each text size: the lower quartile, the leading
     * inside paragraphs, with the steps between paragraphs above it. */
    private fun pitches(page: Page): Map<Float, Float> = page.lines.filter { !it.artifact }.zipWithNext()
        .filter { (a, b) ->
            a.style.size == b.style.size && step(a, b) > 0 && step(a, b) < a.style.size * MAX_PITCH_EMS * 2
        }
        .groupBy({ it.first.style.size }, { step(it.first, it.second) })
        .filterValues { it.size >= MIN_PITCH_STEPS }
        .mapValues { (_, steps) -> steps.sorted()[steps.size / QUARTILE] }

    /** A wide gap, or a shaded box around one of two lines of code only, ends a listing; its blank lines don't. */
    private fun endsListing(prev: Line, next: Line): Boolean {
        val height = prev.box.height.coerceAtLeast(1f)
        val gap = next.box.top - prev.box.bottom
        return gap > height * CODE_GAP || gap < -height * 2 || boxedApart(prev, next)
    }

    /** A filled box (a listing's shading) holds one of the two lines and not the other: two listings. */
    private fun boxedApart(a: Line, b: Line): Boolean = page?.paths.orEmpty().any {
        it.filled && it.bounds.height >= a.box.height * 2 &&
            it.bounds.containsCentre(a.box) != it.bounds.containsCentre(b.box)
    }

    private fun textBlocks(page: Page, lines: List<Line>): List<Block> {
        val (role, level) = kindOf(lines.first())
        val box = lines.map { it.box }.reduce(Box::union)
        val chars = lines.sumOf { l -> l.text.count { !it.isWhitespace() } }
        if (role == Role.CODE) {
            val inlines = lines.flatMap { it.inlines }
            return listOf(
                Block(
                    BlockType.CODE,
                    page.index,
                    box,
                    codeText(lines),
                    chars = chars,
                    method = "geometry+columns",
                    inlines = inlines
                )
            )
        }
        val text = join(lines)
        val type = typeOf(lines, role, text)
        val region = if (type == BlockType.EQUATION) box.padded(EQUATION_PAD) else null
        val method = if (type == BlockType.EQUATION) "fallback:phase5" else "geometry+template"
        val inlines = if (type == BlockType.EQUATION) emptyList() else lines.flatMap { it.inlines }
        val shown = when (type) {
            BlockType.EQUATION -> text.replace(OBJECT.toString(), "")

            // A heading is bold already: its bold marks would say nothing.
            BlockType.HEADING -> text.filter { it != Mark.BOLD.open && it != Mark.BOLD.close }

            else -> text
        }
        val block = Block(
            type, page.index, box, shown, level, region = region, chars = chars, method = method, inlines = inlines
        )
        if (type == BlockType.PARAGRAPH) markerOf(lines.first())?.let { markers[block] = it }
        return listOf(block)
    }

    /** What a run of text lines with [role] is, from its look and its [text]. */
    private fun typeOf(lines: List<Line>, role: Role, marked: String): BlockType {
        val text = plain(marked)
        return when {
            isMath(lines.first()) -> BlockType.EQUATION

            // A "heading" with no letters is an equation number or a stray figure label, not a heading.
            // Nor is bold text at the body's size that starts in lower case or ends mid-sentence ("and
            // associative,"); a larger title may break mid-phrase over its lines. A table's header row, its cells
            // set far apart, is not a heading either.
            role == Role.HEADING && text.any { it.isLetter() } && !(isBodySize(lines.first()) && midSentence(text)) &&
                lines.none(::isRow) -> BlockType.HEADING

            CAPTION.containsMatchIn(text) -> BlockType.CAPTION

            else -> BlockType.PARAGRAPH
        }
    }

    /**
     * A code listing's text: rows top to bottom, each character at its column (its distance from the listing's left
     * edge in character widths), and the blank lines the gaps between rows leave.
     */
    private fun codeText(lines: List<Line>): String {
        val advance = advanceFor(lines)
        val left = lines.minOf { it.box.left }
        val rows = rowsOf(lines)
        val step = lineStep(rows)
        val sb = StringBuilder()
        var lastTop = Float.NaN
        for (row in rows) {
            val top = row.minOf { it.box.top }
            if (!lastTop.isNaN()) {
                val blank = ((top - lastTop) / step).roundToInt() - 1
                repeat(blank.coerceIn(0, MAX_BLANK_LINES)) { sb.append('\n') }
                sb.append('\n')
            }
            lastTop = top
            sb.append(rowText(row, left, advance))
        }
        return sb.toString()
    }

    /** One row of code: its characters at their columns, the spaces between them made again. */
    private fun rowText(row: List<Line>, left: Float, advance: Float): String {
        val out = StringBuilder()
        for (line in row.sortedBy { it.box.left }) line.spans.forEach { place(out, it, left, advance) }
        return out.toString().trimEnd()
    }

    /** [span]'s characters, each at its column from [left] in character widths of [advance]. */
    private fun place(out: StringBuilder, span: Span, left: Float, advance: Float) {
        for (k in span.text.indices) {
            val c = span.text[k]
            if (c.isWhitespace()) continue
            val x = span.lefts.getOrNull(k) ?: span.box.left
            val column = ((x - left) / advance).roundToInt().coerceAtLeast(0)
            while (out.length < column) out.append(' ')
            out.append(c)
        }
    }

    /** Lines grouped into rows (a row can hold several fragments: a prompt and the code after it), top to bottom. */
    private fun rowsOf(lines: List<Line>): List<List<Line>> {
        val rows = ArrayList<MutableList<Line>>()
        for (line in lines.sortedBy { it.box.top }) {
            val row = rows.lastOrNull()?.takeIf { r -> Extraction.sameRow(r.first().box, line.box) }
            if (row != null) row += line else rows += mutableListOf(line)
        }
        return rows
    }

    /** The usual distance from one row of code to the next: the median, so blank lines don't count. */
    private fun lineStep(rows: List<List<Line>>): Float {
        val steps = rows.zipWithNext { a, b -> b.minOf { it.box.top } - a.minOf { it.box.top } }.filter { it > 0f }
        val first = rows.first().first()
        return steps.sorted().getOrNull(steps.size / 2) ?: (first.box.height * DEFAULT_LINE_STEP)
    }

    /**
     * The character cell to lay this listing out on.
     *
     * The listing's own spacing comes first, because it is measured from these very glyphs; the style's width is
     * an inference across the book and only stands in when this listing offers nothing to measure. Preferring the
     * style was what broke a Packt DevOps PDF (owner, 2026-11-03): it sets its fonts at size 1 and scales them
     * through the text matrix, so every style came through as `size=1.0`, and the width fell back to
     * `size * TYPICAL_MONO_EMS` = 0.6pt against a real cell of 6pt. Ten times too narrow puts every character in
     * its own column, which is how whole listings came out as vertical columns of single letters.
     */
    internal fun advanceFor(lines: List<Line>): Float {
        estimatedAdvance(lines)?.let { return it }
        val style = (byKey[lines.first().style]?.advance ?: 0f).takeIf { it > 0f }
        return style ?: (lines.first().style.size * TYPICAL_MONO_EMS)
    }

    /** The median distance between neighbouring characters here, or null when there is no pair to measure. */
    private fun estimatedAdvance(lines: List<Line>): Float? {
        val advances = lines.flatMap { l ->
            l.spans.flatMap { s ->
                (0 until minOf(s.text.length, s.lefts.size) - 1)
                    .filter { !s.text[it].isWhitespace() && !s.text[it + 1].isWhitespace() }
                    .map { s.lefts[it + 1] - s.lefts[it] }
            }
        }.filter { it > 0f }.sorted()
        return advances.getOrNull(advances.size / 2)
    }

    private fun regionBlock(page: Page, region: Region): Block {
        val chars = region.lines.sumOf { l -> l.text.count { !it.isWhitespace() } }
        val ordered = region.lines.sortedWith(compareBy({ it.box.top }, { it.box.left }))
        return if (region.type == BlockType.SIDEBAR) {
            val paragraphs = paragraphsOf(region.lines)
            Block(
                BlockType.SIDEBAR,
                page.index,
                region.box,
                paragraphs.joinToString("\n"),
                items = paragraphs,
                chars = chars,
                inlines = ordered.flatMap { it.inlines }
            )
        } else {
            val alt = join(ordered).replace(OBJECT.toString(), "").trim()
            val method = if (region.type == BlockType.FIGURE) "crop" else "fallback:phase5"
            Block(region.type, page.index, region.box, alt, region = region.box, chars = chars, method = method)
        }
    }

    /** A sidebar's lines as paragraphs, by the same rules as running text. */
    private fun paragraphsOf(lines: List<Line>): List<String> {
        val out = ArrayList<String>()
        val run = ArrayList<Line>()
        for (line in lines.sortedWith(compareBy({ it.box.top }, { it.box.left }))) {
            if (run.isNotEmpty() && breaks(run.first(), run.last(), line)) {
                out += join(run)
                run.clear()
            }
            run += line
        }
        if (run.isNotEmpty()) out += join(run)
        return out
    }

    /**
     * Consecutive list items become one list. An item's depth is how far its marker sits in, among the list's marker
     * positions; a paragraph set under the previous item's text (its second paragraph) joins that item.
     */
    private fun lists(blocks: List<Block>): List<Block> {
        val out = ArrayList<Block>()
        var i = 0
        while (i < blocks.size) {
            if (markers[blocks[i]] == null) {
                out += blocks[i++]
            } else {
                val (list, next) = readList(blocks, i)
                out += list
                i = next
            }
        }
        return out
    }

    /** The list starting at [start]: its block, and the index of the block after it. */
    // One case per kind of block a list takes in, or that ends it.
    @Suppress("CyclomaticComplexMethod")
    private fun readList(blocks: List<Block>, start: Int): Pair<Block, Int> {
        val members = ArrayList<Pair<Block, Marker>>()
        val texts = ArrayList<String>()
        val taken = ArrayList<Block>()
        var i = start
        while (i < blocks.size) {
            val b = blocks[i]
            val m = markers[b]
            val last = members.lastOrNull()?.second
            val joins = when {
                m != null && members.isNotEmpty() && startsAnotherList(members.first().second, m) -> false

                m != null -> {
                    members += b to m
                    texts += stripMarker(b.text)
                    true
                }

                last != null && b.type == BlockType.PARAGRAPH && abs(b.box.left - last.textLeft) <= LEVEL_SLACK -> {
                    texts[texts.lastIndex] = texts.last() + " " + b.text
                    true
                }

                else -> false
            }
            if (!joins) break
            taken += b
            i++
        }
        val tiers = ArrayList<Float>()
        for (x in members.map { it.second.left }.sorted()) {
            if (tiers.isEmpty() || x - tiers.last() > LEVEL_SLACK) tiers += x
        }
        val items = members.mapIndexed { n, (_, m) ->
            ListItem(texts[n], m.text, tiers.indexOfLast { it <= m.left + LEVEL_SLACK }.coerceAtLeast(0))
        }
        val blocksIn = members.map { it.first }
        val list = Block(
            BlockType.LIST,
            blocksIn.first().page,
            blocksIn.map { it.box }.reduce(Box::union),
            texts.joinToString("\n"),
            items = texts,
            chars = taken.sumOf { it.chars },
            method = "geometry+marker",
            // In the items' order, each drawn bullet's picture left out with the bullet itself.
            inlines = taken.flatMap { b -> if (markers[b]?.kind == DRAWN) b.inlines.drop(1) else b.inlines },
            listItems = items
        )
        return list to i
    }

    /**
     * [m] starts a list of its own after the list [first] started: at the same depth, its marker is of another kind
     * (numbers after bullets, another bullet glyph), or its numbering starts again.
     */
    private fun startsAnotherList(first: Marker, m: Marker): Boolean {
        if (abs(m.left - first.left) > LEVEL_SLACK) return false
        // Emoji bullets are one kind, whether set as text or drawn as pictures (fonts without colour emoji).
        val emoji = setOf(SYMBOL, DRAWN)
        val sameKind = m.kind == first.kind || (m.kind in emoji && first.kind in emoji)
        return !sameKind || (m.kind == ORDERED && m.text.trim('(', ')', '.') in FIRST_NUMBERS)
    }

    /** A line's list marker: what it shows, where it is and where the item's text starts; null for other lines. */
    // Each return is one kind of line that is not a list item, each branch one kind of marker.
    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private fun markerOf(line: Line): Marker? {
        val t = line.text
        val trimmed = t.trimStart()
        if (trimmed.isEmpty() || kindOf(line).first != Role.BODY) return null
        val lead = t.length - trimmed.length
        val symbol = symbolBullet(trimmed)
        // A character set in a code font is code, not a marker ("the * in front of them" carried to a new line); but
        // Word sets its second-level bullet as an "o" in Courier New.
        val firstSpan = line.spans.firstOrNull { it.text.isNotBlank() }
        val wordO = wordBullet(line)
        if (firstSpan != null && isCodeStyle(firstSpan.style) && !wordO) return null
        val m = LIST_MARKER.find(trimmed)
        val (shown, length, kind) = when {
            wordO -> Triple(BULLET, 1 + trimmed.drop(1).takeWhile { it.isWhitespace() }.length, "glyph:o")

            // Kept as drawn: a tick and a cross in one list mean something.
            symbol > 0 -> Triple(trimmed.take(symbol).trim(), symbol, SYMBOL)

            m != null -> {
                val mark = m.groupValues[1]
                val glyph = mark.length == 1 && !mark[0].isLetterOrDigit()
                Triple(if (glyph) BULLET else mark, m.value.length, if (glyph) mark else ORDERED)
            }

            glyphBullet(line, trimmed) ->
                Triple(BULLET, 1 + trimmed.drop(1).takeWhile { it.isWhitespace() }.length, "glyph:${trimmed[0]}")

            else -> return null
        }
        if (lead + length >= t.length) return null
        return Marker(shown, xAt(line, lead), xAt(line, lead + length), kind)
    }

    /** Word's second-level bullet beginning [line]: an "o" in a code font (Courier New) before text that isn't. */
    private fun wordBullet(line: Line): Boolean {
        val i = line.spans.indexOfFirst { it.text.isNotBlank() }
        val first = line.spans.getOrNull(i) ?: return false
        return first.text.trim() == "o" && isCodeStyle(first.style) &&
            line.spans.drop(i + 1).any { it.text.isNotBlank() && !isCodeStyle(it.style) }
    }

    /**
     * The length of a symbol or emoji bullet at the start of [t] ("✅ ", "🔹 ", "❶ ") and the spaces after it, or 0. A
     * symbol (not a letter, digit or punctuation) followed by text after a space.
     */
    private fun symbolBullet(t: String): Int {
        val cp = t.firstOrNull()?.let { t.codePointAt(0) }
        val n = if (cp != null && isSymbolBullet(cp)) emojiLength(t) else 0
        val spaced = n in 1 until t.length && t[n].isWhitespace()
        val spaces = if (spaced) t.drop(n).takeWhile { it.isWhitespace() }.length else 0
        return if (spaced && n + spaces < t.length) n + spaces else 0
    }

    /** [cp] is a symbol or emoji, but not one of the usual bullets (squares, discs, diamonds: the reader's own). */
    private fun isSymbolBullet(cp: Int): Boolean {
        val type = Character.getType(cp).toByte()
        val symbol = type == Character.OTHER_SYMBOL || (type == Character.MATH_SYMBOL && cp in ARROWS)
        return (symbol || cp in DINGBATS || cp in EMOJI) && cp !in GEOMETRIC && cp != OBJECT.code
    }

    /** The length of the emoji starting [t], with the variation selectors, joiners and skin tones after it. */
    private fun emojiLength(t: String): Int {
        var n = Character.charCount(t.codePointAt(0))
        while (n < t.length && t.codePointAt(n) in JOINED) {
            n += Character.charCount(t.codePointAt(n))
        }
        return n
    }

    /** A bullet set as a glyph of a symbol font, or drawn as a small shape at the start of the line. */
    private fun glyphBullet(line: Line, trimmed: String): Boolean {
        val c = trimmed[0]
        val firstSpan = line.spans.firstOrNull { it.text.isNotBlank() } ?: return false
        val dingbat = DINGBAT_FONTS.any { firstSpan.style.family.contains(it, ignoreCase = true) } &&
            firstSpan.text.trim().length == 1
        val drawn = c == OBJECT &&
            line.inlines.firstOrNull()?.let { it.ems <= SMALL_MARK_EMS && it.widthEms <= SMALL_MARK_EMS } == true
        // A drawn bullet is its own picture: the text may follow it with no space.
        return trimmed.length > 1 && (drawn || (dingbat && trimmed[1].isWhitespace()))
    }

    /** The x where character [index] of [line]'s text starts. */
    private fun xAt(line: Line, index: Int): Float {
        var pos = 0
        for (span in line.spans) {
            if (index < pos + span.text.length) return span.lefts.getOrNull(index - pos) ?: span.box.left
            pos += span.text.length
        }
        return line.box.right
    }

    /** An item's text without its marker. */
    private fun stripMarker(text: String): String {
        val t = text.trimStart()
        val shown = plain(t)
        // A glyph bullet is one character and the spaces after it.
        val length = symbolBullet(shown).takeIf { it > 0 } ?: LIST_MARKER.find(shown)?.value?.length
            ?: (1 + shown.drop(1).takeWhile { it.isWhitespace() }.length)
        return withoutEmptyRuns(dropShown(t, length))
    }

    /** [t] without runs left empty (a code run that held only the marker cut from it). */
    private fun withoutEmptyRuns(t: String): String {
        var s = t
        for (m in Mark.entries) s = s.replace(Regex("${m.open}\\s*${m.close}"), "")
        return s.trimStart()
    }

    /** [t] without its first [n] shown characters; the marks among them (a bold marker's) stay, so runs still pair. */
    private fun dropShown(t: String, n: Int): String {
        val out = StringBuilder()
        var dropped = 0
        for (c in t) {
            val mark = c in SUP_OPEN..ITALIC_CLOSE
            if (!mark && dropped < n) dropped++ else out.append(c)
        }
        return out.toString()
    }

    /**
     * Tables drawn without rules (Regions finds those with rules): three or more rows in a stack, each with three or
     * more short cells set far apart, the cells' starts lining up from row to row. They go out as one table region,
     * like a ruled table (an image of the page this phase; Phase 4 converts tables).
     */
    // Page geometry: each condition is one case of the layout, kept together to read as one rule.
    @Suppress("CyclomaticComplexMethod")
    private fun withTables(page: Page, items: List<Item>): List<Item> {
        val lines = items.filterIsInstance<Item.Text>().map { it.line }.filter { !isCode(it) }
        val rows = rowsOf(lines).map { r -> r.sortedBy { it.box.left } }
        val cells = rows.map { r -> if (isCellRow(page, r)) r else null }
        val tables = ArrayList<List<List<Line>>>()
        var run = ArrayList<List<Line>>()
        fun close() {
            // Two columns of short lines are an index, or a page's two text columns, unless a bold row heads them
            // ("Command  Description") over rows that aren't bold, each read across.
            val wide = run.any { it.size >= MIN_CELLS }
            val headed = run.firstOrNull()?.all { it.style.bold } == true &&
                run.drop(1).none { r -> r.all { it.style.bold } } && everyRowAcross(lines, run)
            val table = wide || headed
            if (run.size >= MIN_TABLE_ROWS && table && readAcross(lines, run)) tables += run
            run = ArrayList()
        }
        for (row in cells) {
            val last = run.lastOrNull()
            when {
                row == null -> close()

                last == null -> run += row

                aligned(last, row) && row.first().box.top - last.maxOf { it.box.bottom } < row.first().box.height * 2 ->
                    run += row

                else -> {
                    close()
                    run += row
                }
            }
        }
        close()
        if (tables.isEmpty()) return items
        val claimed = IdentityHashMap<Line, Region>()
        for (t in tables) {
            val inside = t.flatten()
            val box = inside.map { it.box }.reduce(Box::union)
            val region = Region(
                BlockType.TABLE,
                Box(
                    box.left - TABLE_PAD,
                    box.top - TABLE_PAD,
                    box.right + TABLE_PAD,
                    box.bottom + TABLE_PAD
                ),
                inside
            )
            inside.forEach { claimed[it] = region }
        }
        val out = ArrayList<Item>()
        val placed = HashSet<Region>()
        for (item in items) {
            val region = (item as? Item.Text)?.let { claimed[it.line] }
            when {
                region == null -> out += item
                placed.add(region) -> out += Item.Area(region)
            }
        }
        return out
    }

    /** A row of three or more short cells, set far apart (two columns of short lines are an index, not a table). */
    private fun isCellRow(page: Page, row: List<Line>): Boolean {
        if (row.size < MIN_ROW_CELLS) return false
        val em = row.first().style.size.coerceAtLeast(1f)
        return row.all { it.box.width <= page.width * MAX_CELL_SHARE } &&
            row.zipWithNext().all { (a, b) -> b.box.left - a.box.right > em * CELL_GAP_EMS }
    }

    /**
     * The reading order goes across [rows] (a table's cells, one row after another), not down them: text set in
     * columns (an index in three columns) is read a column at a time, and is not a table.
     */
    private fun readAcross(ordered: List<Line>, rows: List<List<Line>>): Boolean {
        val rowOf = IdentityHashMap<Line, Int>()
        rows.forEachIndexed { i, r -> r.forEach { rowOf[it] = i } }
        val inTable = ordered.filter { it in rowOf }
        val steps = inTable.zipWithNext()
        return steps.isNotEmpty() && steps.count { (a, b) -> rowOf[a] == rowOf[b] } * 2 >= steps.size
    }

    /** Each of [rows] is read in one go: its cells come one after another in the reading order. */
    private fun everyRowAcross(ordered: List<Line>, rows: List<List<Line>>): Boolean {
        val index = IdentityHashMap<Line, Int>()
        ordered.forEachIndexed { i, l -> index[l] = i }
        return rows.all { r ->
            val at = r.mapNotNull { index[it] }.sorted()
            at.size == r.size && at.last() - at.first() == r.size - 1
        }
    }

    /** Two rows' cells start in the same places (at least two of them). */
    private fun aligned(a: List<Line>, b: List<Line>): Boolean =
        b.count { cell -> a.any { abs(it.box.left - cell.box.left) <= LEVEL_SLACK } } >= 2

    /**
     * Items with a bullet set apart from its text on the same row (a tab after the marker) joined into one line, so
     * the marker and its text are read as one item.
     */
    private fun withMarkersJoined(items: List<Item>): List<Item> {
        val out = ArrayList<Item>()
        var i = 0
        while (i < items.size) {
            val a = items[i]
            val b = items.getOrNull(i + 1)
            if (a is Item.Text && b is Item.Text && markerBeside(a.line, b.line)) {
                out += Item.Text(joinedRow(a.line, b.line))
                i += 2
            } else {
                out += a
                i++
            }
        }
        return out
    }

    /** [a] is a list marker alone, and [b] its item's text on the same row, to its right. */
    private fun markerBeside(a: Line, b: Line) =
        (LONE_MARKER.matches(a.text.trim()) || wordSubBullet(a, b)) && Extraction.sameRow(a.box, b.box) &&
            b.box.left > a.box.left

    /** Word's second-level bullet: an "o" in Courier New before text that is not code. */
    private fun wordSubBullet(a: Line, b: Line) = a.text.trim() == "o" && isCodeStyle(a.style) && !isCode(b)

    private fun joinedRow(a: Line, b: Line): Line {
        val space = Span(" ", Box(a.box.right, a.box.top, b.box.left, a.box.bottom), a.style, floatArrayOf(a.box.right))
        return Line(a.spans + space + b.spans, a.box.union(b.box), a.inlines + b.inlines)
    }

    /** A list marker: as shown, where it and its item's text start, and its [kind] (its glyph, or numbered). */
    private class Marker(val text: String, val left: Float, val textLeft: Float, val kind: String)

    private fun isMath(line: Line): Boolean {
        val total = line.spans.sumOf { it.text.count { c -> !c.isWhitespace() } }
        val math = line.spans.filter { isMathFont(it.style) }.sumOf { it.text.count { c -> !c.isWhitespace() } }
        return total > 0 && math >= total * MATH_SHARE
    }

    private fun isMathFont(key: StyleKey) = MATH_FAMILIES.any { key.family.contains(it, ignoreCase = true) }

    private fun Box.padded(by: Float) = Box(left - by, top - by, right + by, bottom + by)

    /** A row of table cells: three or more set a few ems apart (a number in a tab before its title is one gap). */
    private fun isRow(line: Line): Boolean {
        val em = line.style.size.coerceAtLeast(1f)
        val starts = line.spans.flatMap { s ->
            s.text.indices.filter { !s.text[it].isWhitespace() }.map { (s.lefts.getOrNull(it) ?: s.box.left) }
        }.sorted()
        return starts.zipWithNext().count { (a, b) -> b - a > em * CELL_GAP_EMS } >= MIN_CELL_GAPS
    }

    private fun isBodySize(line: Line) = line.style.size <= bodySize * BODY_SIZE_SLACK

    private fun midSentence(text: String) =
        text.first().isLowerCase() || text.trimEnd().lastOrNull().let { it != null && it in MID_SENTENCE }

    companion object {
        /**
         * Lines as one text: a word broken by a hyphen at a line end is joined again; other lines meet with a
         * space.
         */
        fun join(lines: List<Line>, marks: (StyleKey) -> Set<Mark> = { emptySet() }): String {
            val sb = StringBuilder()
            for (line in lines) {
                val t = line.marked(marks).trim()
                if (sb.isEmpty()) {
                    sb.append(t)
                } else if (brokenWord(sb, t)) {
                    sb.setLength(sb.length - 1)
                    sb.append(t)
                } else if (codeWordBroken(sb, t)) {
                    // "Deploy" "ment": one name in code, broken over two lines with no hyphen.
                    sb.setLength(sb.length - 1)
                    sb.append(t, 1, t.length)
                } else {
                    sb.append(' ').append(t)
                }
            }
            return sb.toString().replace(SPACES, " ")
        }

        /**
         * [before] ends with a word broken at the end of a line, which [after] finishes: its hyphen goes and the two
         * meet with no space. A soft hyphen always breaks a word; Unicode's hyphen (U+2010), which typesetters set
         * where they break a line, before any letter ("Java‐" "Script"); a plain hyphen before a lower-case letter
         * only ("magni-" "tude", but "Azure-" "AD").
         */
        fun brokenWord(before: CharSequence, after: String): Boolean {
            val afterLetter = before.length >= 2 && before[before.length - 2].isLetter()
            val next = plain(after).firstOrNull()
            return when (before.lastOrNull()) {
                SOFT_HYPHEN -> true
                HYPHEN -> afterLetter && next?.isLetter() == true
                '-' -> afterLetter && next?.isLowerCase() == true
                else -> false
            }
        }

        /**
         * [sb] ends with a capitalised name in code ("Deploy") and [t] goes on with lower-case letters in code
         * ("ment"): typesetters break long names in code without a hyphen. Commands ("kubectl" "apply") are lower case
         * and stay apart.
         */
        private fun codeWordBroken(sb: StringBuilder, t: String): Boolean {
            val start = sb.lastIndexOf(CODE_OPEN.toString())
            val opens = t.startsWith(CODE_OPEN) && t.getOrNull(1)?.isLowerCase() == true
            if (!sb.endsWith(CODE_CLOSE) || !opens || start < 0) return false
            val word = sb.substring(start + 1, sb.length - 1).takeLastWhile { !it.isWhitespace() }
            val next = t.drop(1).takeWhile { it.isLetter() }
            val ends = t.drop(1 + next.length).firstOrNull().let {
                it == null || it == CODE_CLOSE ||
                    !it.isLetterOrDigit()
            }
            return capitalised(word) && next.isNotEmpty() && ends
        }

        /** A capitalised name of letters only ("Deploy"). */
        private fun capitalised(word: String) = word.length >= 2 && word[0].isUpperCase() && word.all { it.isLetter() }

        private const val HYPHEN = '\u2010'
        private const val SOFT_HYPHEN = '\u00AD'

        private val SPACES = Regex("\\s+")

        /**
         * A list marker at the start of a line, then a space: a bullet (including the private-use bullets of symbol
         * fonts mapped to Unicode), a dash, or a number, letter or roman numeral with a stop or bracket.
         */
        private val LIST_MARKER = Regex(
            "^\\s*([•◦▪▸►‣∙·○■□●◆◇❖➢➤✓✔▶–—*\\-\\uF0B7\\uF0A7\\uF0D8\\uF0FC\\uF076\\uF06E\\uF06C\\uF0A8]|" +
                "\\(?\\d{1,3}[.)]|\\(?[a-zA-Z][.)]|\\(?[ivxlc]{1,6}[.)])\\s+"
        )
        private val LONE_MARKER = Regex("^([•◦▪●■□–\\-\\uF0B7\\uF0A7]|\\(?\\d{1,3}[.)]|\\(?[a-z][.)])$")
        private const val BULLET = "•"
        private const val SYMBOL = "symbol"

        /** The kind of a bullet drawn as a small picture. */
        private const val DRAWN = "glyph:$OBJECT"
        private val DINGBATS = 0x2700..0x27BF
        private val ARROWS = 0x2190..0x21FF
        private val EMOJI = 0x1F300..0x1FAFF
        private val SKIN_TONES = 0x1F3FB..0x1F3FF
        private val GEOMETRIC = 0x25A0..0x25FF

        /** Emoji and symbols in the Basic Multilingual Plane (ticks, crosses, stars, arrows set as pictographs). */
        private val EMOJI_BMP = 0x2600..0x27BF
        private const val VS16 = 0xFE0F
        private const val ZWJ = 0x200D

        /** What joins an emoji: a variation selector, a joiner, a skin tone. */
        private val JOINED = SKIN_TONES.toSet() + VS16 + ZWJ
        private val DINGBAT_FONTS = listOf("Dingbat", "Wingding", "Webding", "Symbol")
        private const val MID_SENTENCE = ",;"
        private const val BODY_SIZE_SLACK = 1.1f
        private val CAPTION = Regex("^(Figure|FIGURE|Fig\\.|Table|TABLE|Chart|Photo)\\s*[0-9A-Z]")
        private val MATH_FAMILIES = listOf("Math", "Symbol", "CMMI", "CMSY", "CMEX", "STIX", "MTExtra", "Euclid")
        private const val MAX_LINE_GAP = 0.75f

        /** A usual line step wider than this many ems is not one; a step this much wider than it sets lines apart. */
        private const val MAX_PITCH_EMS = 2f
        private const val PARAGRAPH_STEP = 1.2f
        private const val MIN_PITCH_STEPS = 3
        private const val QUARTILE = 4

        /** A last line ending a sentence this many ems short of the next line's end ends its paragraph. */
        private const val SHORT_LINE_EMS = 5f
        private val SENTENCE_END = Regex("[.!?:][\"'”’)]?$")
        private const val INDENT_EMS = 0.8f
        private const val MATH_SHARE = 0.5f
        private const val EQUATION_PAD = 3f

        /** A wider gap than this many line heights ends a code listing; blank lines inside one are smaller. */
        private const val CODE_GAP = 2.5f

        /** Characters this many ems apart on one line are in different cells. */
        private const val CELL_GAP_EMS = 2.5f
        private const val MIN_TABLE_ROWS = 3
        private const val MIN_CELL_GAPS = 2
        private const val MIN_CELLS = 3
        private const val MIN_ROW_CELLS = 2

        /** A line ending this close (ems) to the column's right edge runs the full width. */
        private const val FULL_LINE_EMS = 1.5f
        private const val MAX_CELL_SHARE = 0.35f
        private const val TABLE_PAD = 4f
        private const val ORDERED = "ordered"
        private val FIRST_NUMBERS = setOf("1", "a", "A", "i", "I")

        /** A line is code when this share of its characters is in a code font ("mainloop):" ending prose isn't). */
        private const val CODE_LINE_SHARE = 0.95f

        /** A line is a heading when this share of it is in a heading style. */
        private const val HEADING_LINE_SHARE = 0.85f

        /** A line in a heading style this much larger than the body at most, this share of the column wide: prose. */
        private const val SAME_SIZE = 1.05f
        private const val PROSE_WIDTH = 0.75f
        private const val MAX_BLANK_LINES = 2
        private const val DEFAULT_LINE_STEP = 1.2f
        private const val TYPICAL_MONO_EMS = 0.6f

        /** How far (ems) a continuation line may sit from its item's text and still hang under it. */
        private const val HANG_SLACK_EMS = 0.6f

        /** Markers this close (points) are at one depth. */
        private const val LEVEL_SLACK = 4f

        /** A drawn bullet is a small mark, at most this many ems each way. */
        private const val SMALL_MARK_EMS = 0.7f
    }
}
