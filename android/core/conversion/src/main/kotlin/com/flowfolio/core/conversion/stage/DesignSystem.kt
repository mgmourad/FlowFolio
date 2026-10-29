package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.ir.Document
import com.flowfolio.core.conversion.ir.Line
import com.flowfolio.core.conversion.ir.Page
import com.flowfolio.core.conversion.ir.PageKind
import com.flowfolio.core.conversion.ir.Role
import com.flowfolio.core.conversion.ir.Style
import com.flowfolio.core.conversion.ir.StyleKey
import com.flowfolio.core.conversion.pdf.Box

/**
 * Stage C: the book's design system. Text repeated in the same margin on many pages is a running header or footer,
 * recorded as an artifact; the numbers in it give printed page numbers where the PDF has no labels. Of the styles
 * left, the one with most text is the body; clearly larger or bold short-line styles are headings, ranked by size into
 * levels; smaller ones are captions and notes; monospace is code.
 */
// One stage: its rules belong together.
@Suppress("TooManyFunctions")
object DesignSystem {
    fun apply(doc: Document) {
        markArtifacts(doc.pages)
        dropMarginImages(doc.pages)
        labelPages(doc.pages)
        doc.styles = styles(doc.pages)
    }

    private fun content(pages: List<Page>) = pages.filter { it.kind == PageKind.BORN_DIGITAL }

    /** Lines in the top or bottom margin whose text, digits aside, comes back on many pages; and bare folios. */
    private fun markArtifacts(pages: List<Page>) {
        val margins = content(pages).flatMap { p -> p.lines.filter { inMargin(p, it) }.map { p to it } }
        val seen = margins.groupBy { (_, line) -> normal(line.text) }.mapValues { (_, v) -> v.map { it.first }.toSet() }
        // A section's running header repeats only over its own few pages (right-hand pages only): two are enough.
        val repeats = MIN_REPEATS
        for ((_, line) in margins) {
            val key = normal(line.text)
            if ((seen[key]?.size ?: 0) >= repeats || FOLIO.matches(line.text.trim())) line.artifact = true
        }
        // A running header on the row of the page number is one, however few pages its section has ("4.6. Interface
        // design" over a one-page section).
        for ((page, line) in margins) {
            if (!line.artifact && besideFolio(page, line)) line.artifact = true
        }
    }

    /**
     * Images in the top or bottom margin at the same place on several pages (a logo in the header, a band in the
     * footer): page furniture, like a running header, so they don't come back as a figure on every page.
     */
    private fun dropMarginImages(pages: List<Page>) {
        fun key(b: Box) = listOf(b.left, b.top, b.right, b.bottom).map { (it / IMAGE_SNAP).toInt() }
        val inMargin = content(pages).flatMap { p ->
            p.images.filter { inMarginOf(p, it) }
                .map { p to it }
        }
        val repeated = inMargin.groupBy { key(it.second) }
            .filterValues { v -> v.map { it.first }.toSet().size >= MIN_IMAGE_REPEATS }.keys
        if (repeated.isEmpty()) return
        for (p in content(pages)) p.images = p.images.filter { key(it) !in repeated || !inMarginOf(p, it) }
    }

    private fun inMarginOf(p: Page, b: Box) =
        b.bottom < p.height * MARGIN_SHARE * 2 || b.top > p.height * (1 - MARGIN_SHARE * 2)

    /** [line] is on the row of [page]'s page number. */
    private fun besideFolio(page: Page, line: Line) =
        page.lines.any { it.artifact && FOLIO.matches(it.text.trim()) && sameRow(it, line) }

    private fun sameRow(a: Line, b: Line) = Extraction.sameRow(a.box, b.box)

    private fun inMargin(page: Page, line: Line) =
        line.box.bottom < page.height * MARGIN_SHARE || line.box.top > page.height * (1 - MARGIN_SHARE)

    /** Text with digits folded and spacing normalised, so "84 Chapter 3" and "85 Chapter 3" match. */
    private fun normal(text: String) = text.lowercase().replace(DIGITS, "#").replace(SPACES, " ").trim()

    /**
     * Printed page numbers from the folios in the margins, where the PDF has no labels: a number at either end of an
     * artifact line. Only numbers that agree with the most common offset from the page index are trusted, and pages
     * between them follow the same offset.
     */
    // Page geometry: each early return or condition is one case of the layout, kept together to read as one rule.
    @Suppress("ReturnCount")
    private fun labelPages(pages: List<Page>) {
        if (pages.any { it.label != null }) return
        val candidates = content(pages).mapNotNull { p ->
            p.lines.filter { it.artifact }.firstNotNullOfOrNull { folio(it.text) }?.let { p.index to it }
        }
        if (candidates.size < pages.size * MIN_FOLIO_SHARE) return
        val offset = candidates.groupingBy { (index, n) -> n - index }.eachCount().maxBy { it.value }
        if (offset.value < candidates.size * MIN_AGREEMENT) return
        val first = candidates.filter { (i, n) -> n - i == offset.key }.minOf { it.first }
        pages.filter { it.index >= first }.forEach { it.label = (it.index + offset.key).toString() }
    }

    private fun folio(text: String): Int? {
        val t = text.trim()
        return LEADING_NUMBER.find(t)?.groupValues?.get(1)?.toIntOrNull()
            ?: TRAILING_NUMBER.find(t)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun styles(pages: List<Page>): List<Style> {
        val lines = content(pages).flatMap { p -> p.lines.filter { !it.artifact } }
        if (lines.isEmpty()) return emptyList()
        val chars = lines.groupingBy { it.style }.fold(0) { acc, l -> acc + l.text.length }
        val counts = lines.groupingBy { it.style }.eachCount()
        val body = proseBody(pages, chars)
        val bodyChars = chars.getValue(body)
        // Code fonts: flagged or named so, or measured to advance evenly; a family's other weights and slants with it.
        val measured = Monospace.measure(lines)
        // A book set in a monospaced face throughout (notes typed in a code font) has its code in another face: its
        // body's own family is prose, whatever its glyphs.
        val monoFamilies = (measured.keys + chars.keys.filter { it.mono }).map { it.family }.toSet() - body.family
        val mono = chars.keys.filter { it.family in monoFamilies }.toSet()
        val roles = chars.keys.associateWith { key ->
            val perLine = chars.getValue(key).toFloat() / counts.getValue(key)
            roleOf(key, key in mono, body, bodyChars, chars.getValue(key), perLine)
        }
        val headingSizes = roles.filterValues { it == Role.HEADING }.keys
            .map { headingRank(it) }.distinct().sortedDescending()
        return chars.entries.sortedByDescending { it.value }.map { (key, n) ->
            val role = roles.getValue(key)
            val level = if (role ==
                Role.HEADING
            ) {
                (headingSizes.indexOf(headingRank(key)) + 1).coerceAtMost(MAX_LEVEL)
            } else {
                0
            }
            Style(key, role, level, n, if (role == Role.CODE) advanceOf(key, measured) else 0f)
        } + inlineCode(lines, chars.keys, monoFamilies, measured)
    }

    /**
     * The body's style: the most used, unless that one sits mostly on shaded panels (code listings, in a book of
     * notes with more code than prose); then the most used style that doesn't, if it is used about as much.
     * Backgrounds covering most of a page (a dark page) are not panels.
     */
    private fun proseBody(pages: List<Page>, chars: Map<StyleKey, Int>): StyleKey {
        val ranked = chars.entries.sortedByDescending { it.value }
        val top = ranked.first()
        val inPanels = HashMap<StyleKey, Int>()
        val all = HashMap<StyleKey, Int>()
        for (p in content(pages)) {
            val area = p.width * p.height
            val panels = p.paths.filter { it.filled }.map { it.bounds }
                .filter { it.area >= area * MIN_PANEL_SHARE && it.area <= area * MAX_PANEL_SHARE }
            for (l in p.lines) {
                if (l.artifact) continue
                all.merge(l.style, 1, Int::plus)
                val x = (l.box.left + l.box.right) / 2
                val y = (l.box.top + l.box.bottom) / 2
                if (panels.any { it.contains(x, y) }) inPanels.merge(l.style, 1, Int::plus)
            }
        }
        fun paneled(k: StyleKey) = (inPanels[k] ?: 0) > (all[k] ?: 0) * PANEL_SHARE
        if (!paneled(top.key)) return top.key
        return ranked.firstOrNull { (k, n) -> !paneled(k) && n >= top.value * RIVAL_SHARE }?.key ?: top.key
    }

    /**
     * Code fonts used only inside lines of prose ("Call print() to show text"): never a line's main style, so the
     * roles above don't see them, but they are code too.
     */
    private fun inlineCode(
        lines: List<Line>,
        known: Set<StyleKey>,
        monoFamilies: Set<String>,
        measured: Map<StyleKey, Float>
    ): List<Style> = lines.asSequence().flatMap { it.spans.asSequence() }
        .filter { it.style !in known && (it.style.mono || it.style.family in monoFamilies) }
        .groupingBy { it.style }.fold(0) { acc, s -> acc + s.text.length }
        .map { (key, n) -> Style(key, Role.CODE, 0, n, advanceOf(key, measured)) }

    /** A code style's character width: measured, else from its family's measured width, else a typical 0.6 em. */
    private fun advanceOf(key: StyleKey, measured: Map<StyleKey, Float>): Float = measured[key]
        ?: measured.entries.firstOrNull { it.key.family == key.family }?.let { (k, w) -> w / k.size * key.size }
        ?: (key.size * TYPICAL_MONO_EMS)

    /** Larger headings rank first; at one size, bold ranks above regular. */
    private fun headingRank(key: StyleKey) = key.size * 2 + if (key.bold) 1 else 0

    // Each argument is one fact about the style; the rules read them together.
    @Suppress("LongParameterList")
    private fun roleOf(key: StyleKey, mono: Boolean, body: StyleKey, bodyChars: Int, chars: Int, perLine: Float): Role =
        when {
            key == body -> Role.BODY

            mono -> Role.CODE

            key.size >= body.size * HEADING_SCALE && perLine < HEADING_LINE_CHARS -> Role.HEADING

            key.bold && key.size >= body.size * SAME_SIZE && perLine < HEADING_LINE_CHARS && chars < bodyChars * RARE ->
                Role.HEADING

            key.size < body.size * SMALL_SCALE -> Role.SMALL

            else -> Role.BODY
        }

    private val DIGITS = Regex("\\d+")
    private val SPACES = Regex("\\s+")
    private val FOLIO = Regex("^([0-9]{1,4}|[ivxlcdm]{1,7})$", RegexOption.IGNORE_CASE)
    private val LEADING_NUMBER = Regex("^(\\d{1,4})\\b")
    private val TRAILING_NUMBER = Regex("\\b(\\d{1,4})$")
    private const val MARGIN_SHARE = 0.09f
    private const val MIN_REPEATS = 2

    /** Shaded panels: between these shares of the page; a style with over [PANEL_SHARE] of its lines on them. */
    private const val MIN_PANEL_SHARE = 0.01f
    private const val MAX_PANEL_SHARE = 0.5f
    private const val PANEL_SHARE = 0.5f

    /** A style not on panels is the body when it has at least this share of the top style's characters. */
    private const val RIVAL_SHARE = 0.3f

    /** Images within this many points of each other are at the same place. */
    private const val IMAGE_SNAP = 4f
    private const val MIN_IMAGE_REPEATS = 3
    private const val MIN_FOLIO_SHARE = 0.3f
    private const val MIN_AGREEMENT = 0.6f
    private const val HEADING_SCALE = 1.15f
    private const val SAME_SIZE = 0.98f
    private const val SMALL_SCALE = 0.92f
    private const val HEADING_LINE_CHARS = 90f
    private const val RARE = 0.2f
    private const val MAX_LEVEL = 4
    private const val TYPICAL_MONO_EMS = 0.6f
}
