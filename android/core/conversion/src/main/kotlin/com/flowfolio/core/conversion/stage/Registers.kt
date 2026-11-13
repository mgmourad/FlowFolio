package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.ir.Block
import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.ir.Line
import com.flowfolio.core.conversion.ir.ListItem
import com.flowfolio.core.conversion.ir.OBJECT
import com.flowfolio.core.conversion.ir.Page
import com.flowfolio.core.conversion.pdf.Box

/**
 * A book's contents and index pages ("registers", owner 2026-11-12: "the one thing that has been bothering me"). The
 * paragraph rules made a mess of them: a contents entry's title and page number came out as two paragraphs with the
 * leader dots kept, and an index's entries ran together into long paragraphs with their sub-entries flattened.
 *
 * Here they become plain lists, one entry per item:
 * - **Contents:** "Title · 23", the leaders gone, a title wrapped over two lines joined, sections indented.
 * - **Index:** each headword with its page numbers, sub-entries one level in, wrapped page numbers joined back.
 */
// One stage: each rule is one case of how contents and indexes are printed, kept together to read as one.
@Suppress("CyclomaticComplexMethod", "ComplexCondition", "ReturnCount", "LoopWithTooManyJumpStatements")
object Registers {
    enum class Kind { CONTENTS, INDEX }

    /** What kind of register [lines] (a page's text, in reading order) are, or null for an ordinary page. */
    fun kindOf(page: Page, lines: List<Line>, pageCount: Int): Kind? {
        val texts = lines.map { clean(it) }.filter { it.isNotEmpty() }
        if (texts.size < MIN_LINES) return null
        // Contents come at the front of a book and an index at the back: a page of code or exercises in the middle,
        // whose lines also end in numbers, is never taken for either.
        val front = page.index < maxOf(FRONT_PAGES, (pageCount * FRONT_SHARE).toInt())
        val back = page.index >= pageCount - maxOf(BACK_PAGES, (pageCount * BACK_SHARE).toInt())
        val leaders = texts.count { LEADER.containsMatchIn(it) }
        if (front && leaders >= MIN_LINES && leaders * CONTENTS_SHARE >= texts.size) return Kind.CONTENTS
        if (!back) return null
        // An entry is a word and its page numbers, on one line or (as some PDFs set them) on two: "linear function"
        // then "30".
        val refs = texts.indices.count { i ->
            REFS_AT_END.containsMatchIn(texts[i]) ||
                (PAGE_REFS.matches(texts[i]) && i > 0 && texts[i - 1].lastOrNull()?.isLetter() == true)
        }
        val narrow = lines.map { it.box.width }.sorted()[lines.size / 2] < page.width * NARROW_SHARE
        return if (narrow && refs >= MIN_LINES * 2 && refs * INDEX_SHARE >= texts.size) Kind.INDEX else null
    }

    /** One list block of the [kind]'s entries in [lines], in order. */
    fun list(page: Page, lines: List<Line>, kind: Kind): Block? {
        if (lines.isEmpty()) return null
        val bases = columnBases(page, lines)
        val items = when (kind) {
            // Contents keep the reading order: their page numbers stand far right, like a column of their own.
            Kind.CONTENTS -> contents(lines, bases, CONTENTS_LEVEL_EMS)

            // An index's narrow columns, each top to bottom: the reading order can take them across.
            Kind.INDEX -> {
                val ordered = lines.sortedWith(
                    compareBy({ l -> bases.indexOfLast { it <= l.box.left + SLACK } }, { it.box.top })
                )
                index(ordered, bases, INDEX_LEVEL_EMS)
            }
        }
        if (items.isEmpty()) return null
        val texts = items.map { it.text }
        return Block(
            BlockType.LIST,
            page.index,
            lines.map { it.box }.reduce(Box::union),
            texts.joinToString("\n"),
            items = texts,
            chars = lines.sumOf { l -> clean(l).count { !it.isWhitespace() } },
            method = "geometry+register",
            listItems = items
        )
    }

    private class Entry(val level: Int) {
        val parts = ArrayList<String>()
        fun add(text: String) {
            val last = parts.lastOrNull()
            // A word broken at the line's end ("chro-" / "mosome") joins without a space.
            if (last != null && last.endsWith('-') && last.length > 1 && last[last.length - 2].isLetter()) {
                parts[parts.lastIndex] = last.dropLast(1) + text
            } else {
                parts += text
            }
        }

        val text: String get() = parts.joinToString(" ")
    }

    private fun contents(lines: List<Line>, bases: List<Float>, step: Float): List<ListItem> {
        val out = ArrayList<ListItem>()
        var pending: Entry? = null
        fun emit(number: String?) {
            val e = pending ?: return
            val title = e.text.replace(LEADER_TAIL, "").trim()
            val page = number?.filter { !it.isWhitespace() }
            if (title.isNotEmpty()) {
                out +=
                    ListItem(if (page.isNullOrEmpty()) title else "$title · $page", PLAIN, e.level)
            }
            pending = null
        }
        for (line in lines) {
            val t = clean(line)
            if (t.isEmpty()) continue
            if (BARE_NUMBER.matches(t)) {
                // A number with no title before it (a stray folio) is dropped.
                if (pending != null) emit(t)
                continue
            }
            // The previous entry ended in leaders and its number never came: it is complete as it is.
            if (pending?.text?.let { LEADER.containsMatchIn(it) } == true) emit(null)
            val entry = pending ?: Entry(levelOf(line, bases, step)).also { pending = it }
            val number = LEADER_NUMBER.find(t)
            if (number != null) {
                entry.add(t.substring(0, number.range.first))
                emit(number.groupValues[1])
            } else {
                entry.add(t)
            }
        }
        emit(null)
        return out
    }

    private fun index(lines: List<Line>, bases: List<Float>, step: Float): List<ListItem> {
        val entries = ArrayList<Entry>()
        for (line in lines) {
            val t = clean(line)
            if (t.isEmpty() || RUNNING_HEAD.matches(t)) continue
            val last = entries.lastOrNull()
            val prev = last?.text
            val level = levelOf(line, bases, step)
            // A term too long for its column wraps onto a line (indented or not) before its page numbers come: it
            // starts with a capital ("Stanford Linear Accelerator" / "Center 802"), where a sub-entry under a
            // headword with no numbers of its own starts in lower case ("editor" / "changing default, 36").
            val wrappedTerm = last != null && prev != null && !REFS_AT_END.containsMatchIn(prev) &&
                !prev.last().isDigit() && level >= last.level && t.first().isUpperCase()
            val wraps = prev != null &&
                (t.first().isDigit() || prev.endsWith(',') || prev.endsWith('-') || prev.endsWith(";") || wrappedTerm)
            if (wraps) entries.last().add(t) else entries += Entry(level).apply { add(t) }
        }
        return entries.map { ListItem(it.text, PLAIN, it.level) }
    }

    /** How far in [line] sits from the start of its column: 0, 1 or 2 levels of [step] ems each. */
    private fun levelOf(line: Line, bases: List<Float>, step: Float): Int {
        val base = bases.lastOrNull { it <= line.box.left + SLACK } ?: return 0
        val em = line.box.height.coerceAtLeast(1f)
        return ((line.box.left - base) / (em * step)).toInt().coerceIn(0, MAX_LEVEL)
    }

    /** Where each column starts: the leftmost line of each group of lines whose starts lie close together. */
    private fun columnBases(page: Page, lines: List<Line>): List<Float> {
        val lefts = lines.map { it.box.left }.sorted()
        val bases = ArrayList<Float>()
        for (l in lefts) if (bases.isEmpty() || l - bases.last() > page.width * COLUMN_GAP_SHARE) bases += l
        return bases
    }

    /** [line] has a contents entry's leader dots ("Part I. Fundamentals . . . . 23"), even when set as a heading. */
    fun hasLeaders(line: Line): Boolean = LEADER.containsMatchIn(clean(line))

    /**
     * [line]'s text without the drawings set in it: an index often draws a link box around each page number, which
     * reaches the text as an object character and would hide the numbers.
     */
    private fun clean(line: Line): String = line.text.replace(OBJECT.toString(), "").replace(SPACES, " ").trim()

    /** A running head left on the page ("760 Index", "Index 761"). */
    private val RUNNING_HEAD = Regex("""(?i)(\d{1,4}\s+)?(index|contents)(\s+\d{1,4})?""")

    private val SPACES = Regex("""\s+""")

    /** An item with no marker: written as a list without bullets (`list-style-type: none`). */
    const val PLAIN = ""

    private val LEADER = Regex("""(?:\.\s?){4,}|…{2,}""")
    private val LEADER_TAIL = Regex("""\s*(?:\.\s?){2,}\s*$|\s*…+\s*$""")
    private val LEADER_NUMBER = Regex("""(?:\s*(?:\.\s?){3,}|\s*…+)\s*((?:\d\s?){1,4}|[ivxlcIVXLC]{1,6})\s*$""")
    private val BARE_NUMBER = Regex("""(?:\d\s?){1,4}|[ivxlcIVXLC]{1,6}""")

    /** An index entry: a word, then its page numbers ("cloning 70, 71", "$EDITOR, 337", "telomere 188–191"). */
    private val REFS_AT_END =
        Regex("""[\p{L})\]’'],?\s+\d{1,4}(?:\s*[–-]\s*\d{1,4})?(?:\s*,\s*\d{1,4}(?:\s*[–-]\s*\d{1,4})?)*\s*,?$""")

    private val PAGE_REFS = Regex("""\d{1,4}(?:\s*[–-]\s*\d{1,4})?(?:\s*,\s*\d{1,4}(?:\s*[–-]\s*\d{1,4})?)*""")

    private const val MIN_LINES = 4
    private const val FRONT_PAGES = 30
    private const val FRONT_SHARE = 0.08f
    private const val BACK_PAGES = 40
    private const val BACK_SHARE = 0.15f
    private const val CONTENTS_SHARE = 4
    private const val INDEX_SHARE = 2.5f
    private const val NARROW_SHARE = 0.5f
    private const val COLUMN_GAP_SHARE = 0.2f

    // Contents indent their sections a good em or more; an index sets its sub-entries in by under an em.
    private const val CONTENTS_LEVEL_EMS = 1.2f
    private const val INDEX_LEVEL_EMS = 0.7f
    private const val MAX_LEVEL = 2
    private const val SLACK = 1f
}
