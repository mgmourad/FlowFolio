package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.ir.Block
import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.ir.Chapter
import com.flowfolio.core.conversion.ir.Document

/**
 * Stage F (basic): block ids, paragraphs continued over a page turn, and chapters. Chapters start where the PDF's
 * top-level outline entries point (at the matching heading on that page when there is one); without an outline, at
 * level-1 headings; without those, every [FALLBACK_PAGES] pages (flagged in the health report).
 */
// One stage: its rules belong together.
@Suppress("TooManyFunctions")
object Linking {
    fun apply(doc: Document) {
        Headings.apply(doc)
        assignIds(doc)
        linkContinuations(doc)
        doc.chapters = chapters(doc)
    }

    private fun assignIds(doc: Document) {
        for (page in doc.pages) {
            page.blocks.forEachIndexed { i, b -> b.id = "p%04d-b%03d".format(page.index + 1, i) }
        }
    }

    /** A paragraph that ends a page mid-sentence and one that starts the next in lower case are one paragraph. */
    // Page geometry: each early return or condition is one case of the layout, kept together to read as one rule.
    @Suppress("LoopWithTooManyJumpStatements", "ComplexCondition")
    private fun linkContinuations(doc: Document) {
        for ((a, b) in doc.pages.zipWithNext()) {
            val last = a.blocks.lastOrNull { it.type != BlockType.FIGURE && it.type != BlockType.CAPTION } ?: continue
            val first = b.blocks.firstOrNull { it.type != BlockType.FIGURE } ?: continue
            if (last.type == BlockType.PARAGRAPH && first.type == BlockType.PARAGRAPH &&
                last.text.trimEnd().lastOrNull()?.let { it in SENTENCE_END } == false &&
                first.text.firstOrNull()?.isLowerCase() == true
            ) {
                first.continuesFrom = last.id
            }
        }
    }

    private fun chapters(doc: Document): List<Chapter> {
        val blocks = doc.pages.flatMap { it.blocks }
        if (blocks.isEmpty()) {
            return listOf(
                Chapter(doc.title, emptyList(), file(0)).also {
                    it.pages =
                        doc.pages.map { p -> p.index }
                }
            )
        }
        val starts = fromOutline(doc, blocks) ?: fromHeadings(blocks) ?: fromPages(blocks)
        val cuts = (
            listOf(0 to doc.title) + starts.filter {
                it.first > 0
            }
            ).distinctBy { it.first }.sortedBy { it.first }
        val chapters = cuts.mapIndexed { n, (from, title) ->
            val to = cuts.getOrNull(n + 1)?.first ?: blocks.size
            Chapter(title, blocks.subList(from, to), file(n))
        }.filter { it.blocks.isNotEmpty() }
        assignPages(doc, chapters)
        return chapters
    }

    /** Where each top-level outline entry starts: its heading on the page it names, else that page's first block. */
    private fun fromOutline(doc: Document, blocks: List<Block>): List<Pair<Int, String>>? {
        val top = doc.outline.filter { it.depth == 0 && it.page >= 0 }.sortedBy { it.page }
        if (top.size < 2) return null
        return top.mapNotNull { entry ->
            val onPage = blocks.withIndex().filter { it.value.page == entry.page }
            val heading = onPage.firstOrNull {
                it.value.type == BlockType.HEADING && matches(it.value.text, entry.title)
            }
            val at = heading ?: onPage.firstOrNull() ?: blocks.withIndex().firstOrNull { it.value.page > entry.page }
            at?.let { labelBefore(blocks, it.index) to entry.title }
        }
    }

    /**
     * Where a chapter whose title is at [title] starts: before the headings set just above the title on its page (the
     * "Chapter 5" or "Part II" label over it), which belong to the new chapter, not the end of the last.
     */
    private fun labelBefore(blocks: List<Block>, title: Int): Int {
        var start = title
        while (start > 0) {
            val prev = blocks[start - 1]
            val short = prev.text.trim().split(' ').size <= MAX_LABEL_WORDS
            val label = prev.page == blocks[title].page &&
                ((prev.type == BlockType.HEADING && short) || LABEL.matches(prev.text.trim()))
            if (!label) break
            start--
        }
        return start
    }

    private fun fromHeadings(blocks: List<Block>): List<Pair<Int, String>>? = blocks.withIndex()
        .filter { it.value.type == BlockType.HEADING && it.value.level == 1 }
        .map { it.index to it.value.text }
        .takeIf { it.size >= 2 }

    private fun fromPages(blocks: List<Block>): List<Pair<Int, String>> = blocks.withIndex()
        .filter { (i, b) -> i == 0 || b.page / FALLBACK_PAGES != blocks[i - 1].page / FALLBACK_PAGES }
        .map { (i, b) -> i to "Pages ${b.page + 1}-${b.page + FALLBACK_PAGES}" }

    /** Each page goes to the chapter its first block is in; a page without blocks, to the chapter before it. */
    private fun assignPages(doc: Document, chapters: List<Chapter>) {
        val chapterOf = HashMap<Int, Int>()
        chapters.forEachIndexed { n, c -> c.blocks.forEach { b -> chapterOf.putIfAbsent(b.page, n) } }
        var current = 0
        val pages = Array(chapters.size) { ArrayList<Int>() }
        for (page in doc.pages) {
            current = chapterOf[page.index] ?: current
            pages[current] += page.index
        }
        chapters.forEachIndexed { n, c -> c.pages = pages[n] }
    }

    /** A heading and an outline title are the same when one contains the other, ignoring case, dots and spacing. */
    fun matches(heading: String, title: String): Boolean {
        val a = normal(heading)
        val b = normal(title)
        if (a.isEmpty() || b.isEmpty()) return false
        // Chapter numbers can sit on either side of the title ("Welcome to Economics! 1", "Chapter 1 Welcome...").
        return a.contains(b) || b.contains(a) || words(heading).let { it.isNotEmpty() && it == words(title) }
    }

    /**
     * [line] is a piece of [title] (a title set over two lines, its years on the second), at least four letters or
     * digits long, so a bare chapter number is not.
     */
    fun isPartOf(line: String, title: String): Boolean {
        val a = normal(line)
        return a.length >= MIN_TITLE_PART && normal(title).contains(a)
    }

    private fun normal(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Letters only, without a leading "chapter": the words of a title, whatever numbering is set with it. */
    private fun words(s: String) = s.lowercase().filter { it.isLetter() }.removePrefix("chapter")

    fun file(n: Int) = "ch%03d.xhtml".format(n + 1)

    private const val SENTENCE_END = ".!?:;\"”’)]"
    private const val FALLBACK_PAGES = 20
    private const val MAX_LABEL_WORDS = 4

    /** A chapter's label set above its title: "Chapter 5", "CHAPTER V", "Part II", "Unit 3". */
    val LABEL = Regex("^(chapter|part|unit|section|lesson|module)\\s+[0-9ivxlcdm]+$", RegexOption.IGNORE_CASE)
    private const val MIN_TITLE_PART = 4
}
