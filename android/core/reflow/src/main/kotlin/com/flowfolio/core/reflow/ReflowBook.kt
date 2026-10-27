package com.flowfolio.core.reflow

import android.graphics.BitmapFactory
import com.flowfolio.core.epub.EpubArchive
import com.flowfolio.core.epub.EpubPackage
import com.flowfolio.core.epub.NavPoint
import java.util.Locale

/**
 * An EPUB ready to paginate: its chapters parsed once, then laid out into pages for a [PageSpec] (pane size and text
 * size). Locators (chapter and character offset) map to pages after any re-layout.
 */
@Suppress("TooManyFunctions") // laying out, mapping locators and pages, and the book's contents, notes and islands
class ReflowBook(val archive: EpubArchive, val pkg: EpubPackage) {
    val locale: Locale = pkg.metadata.language?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()

    /** Reading order (linear spine items), parsed. */
    val chapters: List<Chapter>
    private val chapterByPath: Map<String, Int>
    private var islandIds = 0

    init {
        val linear = pkg.spine.filter { it.linear && it.mediaType.contains("html") }.ifEmpty { pkg.spine }
        chapters = linear.map { item ->
            val bytes = archive.read(item.path)
            if (bytes ==
                null
            ) {
                Chapter(item.path, emptyList(), emptyMap(), emptyMap(), 0)
            } else {
                ChapterParser(item.path) { islandIds++ }.parse(bytes)
            }
        }
        chapterByPath = chapters.withIndex().associate { (i, c) -> c.path to i }
    }

    /** The current layout; [paginate] replaces it. */
    lateinit var spec: PageSpec
        private set
    var pages: List<Page> = emptyList()
        private set

    /** First page of each chapter in [pages] (its blank page, when it has one). */
    private var chapterStart = IntArray(0)

    /** Each chapter's own pages, laid out or estimated; [join] puts them together, with blanks, as [pages]. */
    private var byChapter: Array<List<Page>> = emptyArray()

    /**
     * Spread pairing to keep, as `Spreads` counts it (1 with the cover on its own, else 0), or null in single-page
     * layout. In a spread every chapter after the first starts on a left page (ADR-0041).
     */
    private var spreadShift: Int? = null

    /** The blank page before each chapter that needs one, made once per layout so cached images can follow it. */
    private var blanks: Array<Page?> = emptyArray()

    /** Image sizes by path, read once; [NO_SIZE] for images that can't be read. Chapters are laid out in parallel. */
    private val imageSizes = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Int>>()

    /** Which chapters are laid out exactly; the others hold estimated pages until [applyRest]. */
    private var exact = BooleanArray(0)

    /** Raised by every [paginate]; work for an older layout is dropped. */
    var generation = 0
        private set

    /** Every chapter is laid out exactly (no estimates left). */
    val complete: Boolean get() = exact.all { it }

    /**
     * Lays the book out for [spec]. [density] turns CSS pixels (images) into screen pixels; [islandSize] gives
     * measured islands (null: placeholder size). [spreadShift] keeps chapters on their own spreads (see the property).
     * With a [first] chapter, only that chapter is laid out now (ADR-0041: the reader's page shows within 300 ms
     * wherever it is in the book); every other chapter gets pages estimated from its text per page, until [layOutRest]
     * and [applyRest] replace them. In a spread the first chapter still starts on a left page, so the spread shown
     * never re-pairs; only page numbers settle.
     */
    fun paginate(
        spec: PageSpec,
        density: Float,
        first: Int = -1,
        islandSize: (Int) -> Pair<Int, Int>? = { null },
        spreadShift: Int? = null
    ): Paginator {
        this.spec = spec
        this.spreadShift = spreadShift
        generation++
        // The old layout's text layouts go now, not after the new ones are built: holding both made the collector work
        // through the layout (270 ms on a fresh open, 450 ms or more on a re-layout).
        pages = emptyList()
        byChapter = emptyArray()
        blanks = arrayOfNulls(chapters.size)
        val paginator = Paginator(spec) { block ->
            when (block) {
                is Block.Image -> imageSize(block.path)?.let { (w, h) ->
                    (w * density).toInt() to (h * density).toInt()
                }

                is Block.Island -> islandSize(block.id)

                is Block.Text -> null
            }
        }
        val now = if (first in chapters.indices) listOf(first) else chapters.indices.toList()
        exact = BooleanArray(chapters.size) { it in now }
        val laidOut = layOutAll(paginator, now)
        val perPage = charsPerPage(laidOut, spec)
        byChapter = Array(chapters.size) { i -> laidOut[i] ?: estimate(i, chapters[i], perPage) }
        join()
        return paginator
    }

    /**
     * Text per page for estimates: from the chapters laid out, or, when they hold too little text to tell (a title
     * page), from the page's size in lines and characters.
     */
    private fun charsPerPage(laidOut: Map<Int, List<Page>>, spec: PageSpec): Float {
        val chars = laidOut.keys.sumOf { chapters[it].length }
        val count = laidOut.values.sumOf { it.size }
        if (chars >= MIN_SAMPLE_CHARS && count > 0) return chars.toFloat() / count
        val perLine = spec.width / (spec.bodyPx * AVERAGE_CHAR_EMS)
        val lines = spec.height / (spec.bodyPx * spec.lineSpacing)
        return perLine * lines
    }

    /** Pages standing in for [chapter] until it is laid out: empty, evenly over its text, so locators still map. */
    private fun estimate(chapter: Int, c: Chapter, charsPerPage: Float): List<Page> {
        val count = (c.length / charsPerPage.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        return List(count) { k -> Page(chapter, emptyList(), c.length * k / count, c.length * (k + 1) / count) }
    }

    /**
     * [pages] from each chapter's pages: in a spread, a blank page before every chapter after the first that would
     * otherwise start on a right page (ADR-0041).
     */
    private fun join() {
        val all = ArrayList<Page>()
        val starts = IntArray(chapters.size)
        val shift = spreadShift
        for (i in chapters.indices) {
            starts[i] = all.size
            if (shift != null && i > 0 && (all.size + shift) % 2 == 1) {
                all += blanks[i] ?: Page(i, emptyList(), 0, 0).also { blanks[i] = it }
            }
            all += byChapter[i]
        }
        pages = all
        chapterStart = starts
    }

    /**
     * The chapters still estimated, laid out with [paginator]. Reads only the parsed chapters, so it can run while
     * pages are drawn; the result goes to [applyRest].
     */
    fun layOutRest(paginator: Paginator): Map<Int, List<Page>> =
        layOutAll(paginator, exact.indices.filter { !exact[it] })

    /**
     * [indices]' chapters laid out at once on the device's cores: chapters are independent (each builds its own text
     * layouts and paints; image sizes are a concurrent map).
     */
    private fun layOutAll(paginator: Paginator, indices: List<Int>): Map<Int, List<Page>> = indices.parallelStream()
        .map { it to paginator.paginate(it, chapters[it]) }
        .collect(java.util.stream.Collectors.toMap({ it.first }, { it.second }))

    /**
     * Puts [rest] in place of the estimates. Chapters laid out exactly in the meantime ([repaginate]) keep their pages;
     * pages already laid out keep their objects, so whoever holds one can find its new number.
     */
    fun applyRest(rest: Map<Int, List<Page>>) {
        for ((i, laidOut) in rest) {
            if (exact[i]) continue
            byChapter[i] = laidOut
            exact[i] = true
        }
        join()
    }

    /**
     * Lays out one chapter again with [paginator] (from the last [paginate]), for example once its islands have their
     * sizes. Other chapters keep their pages; only page numbers after it move.
     */
    fun repaginate(chapter: Int, paginator: Paginator) {
        byChapter[chapter] = paginator.paginate(chapter, chapters[chapter])
        exact[chapter] = true
        join()
    }

    fun imageSize(path: String): Pair<Int, Int>? = imageSizes.getOrPut(path) {
        archive.read(path)?.let { bytes ->
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            if (o.outWidth > 0) o.outWidth to o.outHeight else null
        } ?: NO_SIZE
    }.takeIf { it !== NO_SIZE }

    /** Where [page] starts. */
    fun locatorOf(page: Int): Locator = pages.getOrNull(page)?.let { Locator(it.chapter, it.start) } ?: Locator(0, 0)

    /** The page showing [locator]: the last page of its chapter starting at or before it. */
    fun pageOf(locator: Locator): Int {
        if (pages.isEmpty()) return 0
        val chapter = locator.chapter.coerceIn(0, chapters.size - 1)
        val first = chapterStart[chapter]
        val last = if (chapter + 1 < chapters.size) chapterStart[chapter + 1] - 1 else pages.size - 1
        var page = first
        for (p in first..last) if (pages[p].start <= locator.offset) page = p else break
        return page
    }

    /** A book-internal href (`OEBPS/ch02.xhtml#sec`) as a locator, or null when it is not in the reading order. */
    fun locatorOf(href: String): Locator? {
        val chapter = chapterByPath[href.substringBefore('#')] ?: return null
        val anchor = href.substringAfter('#', "").takeIf { it.isNotEmpty() }
        return Locator(chapter, anchor?.let { chapters[chapter].anchors[it] } ?: 0)
    }

    /** The book's contents with the page each entry starts on, flattened with depth. */
    fun contents(): List<Triple<Int, String, Int>> {
        val out = ArrayList<Triple<Int, String, Int>>()
        fun walk(points: List<NavPoint>, depth: Int) {
            for (p in points) {
                locatorOf(p.href)?.let { out += Triple(depth, p.label, pageOf(it)) }
                walk(p.children, depth + 1)
            }
        }
        walk(pkg.toc, 0)
        return out
    }

    /**
     * The printed page number each screen page starts in, from the book's page list (null before the first listed
     * page, or when the book has no page list).
     */
    fun printedPages(): List<String?> {
        val marks = pkg.pageList.mapNotNull { p -> locatorOf(p.href)?.let { it to p.label } }.sortedBy { it.first }
        if (marks.isEmpty()) return List(pages.size) { null }
        var m = -1
        return pages.map { page ->
            val at = Locator(page.chapter, page.start)
            while (m + 1 < marks.size && marks[m + 1].first <= at) m++
            marks.getOrNull(m)?.second
        }
    }

    /** A footnote's text for a link [href] that points at one, else null. */
    fun footnote(href: String): CharSequence? {
        val chapter = chapterByPath[href.substringBefore('#')] ?: return null
        return chapters[chapter].footnotes[href.substringAfter('#', "")]
    }

    /**
     * Packs every island's markup (M3 EPUB memory): run in the background once the book is open, so opening is not
     * slowed; markup is read as it is until then.
     */
    fun packIslands() = chapters.asSequence().flatMap { it.blocks }.flatMap(::markupOf).forEach(PackedText::pack)

    /** The ids of [chapter]'s equations (`math` islands), without unpacking their markup. */
    fun mathIds(chapter: Int): List<Int> = chapters[chapter].blocks.flatMap { b ->
        when (b) {
            is Block.Island -> listOfNotNull(b.id.takeIf { b.kind == "math" })

            is Block.Text -> b.text.getSpans(0, b.text.length, IslandSpan::class.java)
                .filter { it.kind == "math" }.map { it.id }

            is Block.Image -> emptyList()
        }
    }

    /** Every island in reading order, with its chapter (block islands and inline ones). */
    fun islands(chapter: Int): List<Pair<Int, String>> = chapters[chapter].blocks.flatMap { b ->
        when (b) {
            is Block.Island -> listOf(b.id to b.xml)
            is Block.Text -> b.text.getSpans(0, b.text.length, IslandSpan::class.java).map { it.id to it.xml }
            is Block.Image -> emptyList()
        }
    }
}

/** The island markup in [b]: a block island's, or the inline ones in its text. */
private fun markupOf(b: Block): List<PackedText> = when (b) {
    is Block.Island -> listOf(b.packed)
    is Block.Text -> b.text.getSpans(0, b.text.length, IslandSpan::class.java).map { it.packed }
    is Block.Image -> emptyList()
}

/** A chapter with less text than this is too small to estimate the others' pages from. */
private const val MIN_SAMPLE_CHARS = 2_000

/** The average character's width in ems, for estimating pages from the page's size alone. */
private const val AVERAGE_CHAR_EMS = 0.5f

/** Marks an image whose size couldn't be read (a concurrent map holds no nulls). */
private val NO_SIZE = -1 to -1
