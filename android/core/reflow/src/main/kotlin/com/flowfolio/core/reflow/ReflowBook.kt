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

    /** First page of each chapter. */
    private var chapterStart = IntArray(0)

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
     * measured islands (null: placeholder size). With a [first] chapter, the chapters up to and including it are laid
     * out now (so every page number up to the reader's is exact, and spreads pair as they will stay); the chapters
     * after it get pages estimated from its text per page, until [layOutRest] and [applyRest] replace them.
     */
    fun paginate(
        spec: PageSpec,
        density: Float,
        first: Int = -1,
        islandSize: (Int) -> Pair<Int, Int>? = { null }
    ): Paginator {
        this.spec = spec
        generation++
        // The old layout's text layouts go now, not after the new ones are built: holding both made the collector work
        // through the layout (270 ms on a fresh open, 450 ms or more on a re-layout).
        pages = emptyList()
        val paginator = Paginator(spec) { block ->
            when (block) {
                is Block.Image -> imageSize(block.path)?.let { (w, h) ->
                    (w * density).toInt() to (h * density).toInt()
                }

                is Block.Island -> islandSize(block.id)

                is Block.Text -> null
            }
        }
        val last = if (first in chapters.indices) first else chapters.lastIndex
        exact = BooleanArray(chapters.size) { it <= last }
        val all = ArrayList<Page>()
        val starts = IntArray(chapters.size)
        var charsPerPage = 1f
        chapters.forEachIndexed { i, c ->
            starts[i] = all.size
            if (i <= last) {
                val laidOut = paginator.paginate(i, c)
                if (laidOut.isNotEmpty() && c.length > 0) charsPerPage = c.length.toFloat() / laidOut.size
                all += laidOut
            } else {
                all += estimate(i, c, charsPerPage)
            }
        }
        pages = all
        chapterStart = starts
        return paginator
    }

    /** Pages standing in for [chapter] until it is laid out: empty, evenly over its text, so locators still map. */
    private fun estimate(chapter: Int, c: Chapter, charsPerPage: Float): List<Page> {
        val count = (c.length / charsPerPage.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        return List(count) { k -> Page(chapter, emptyList(), c.length * k / count, c.length * (k + 1) / count) }
    }

    /**
     * The chapters still estimated, laid out with [paginator]. Reads only the parsed chapters, so it can run while
     * pages are drawn; the result goes to [applyRest].
     */
    fun layOutRest(paginator: Paginator): Map<Int, List<Page>> {
        val todo = exact.indices.filter { !exact[it] }
        return todo.associateWith { paginator.paginate(it, chapters[it]) }
    }

    /**
     * Puts [rest] in place of the estimates. Chapters laid out exactly in the meantime ([repaginate]) keep their pages;
     * pages already laid out keep their objects, so whoever holds one can find its new number.
     */
    fun applyRest(rest: Map<Int, List<Page>>) {
        val all = ArrayList<Page>()
        val starts = IntArray(chapters.size)
        for (i in chapters.indices) {
            starts[i] = all.size
            val end = if (i + 1 < chapters.size) chapterStart[i + 1] else pages.size
            all += if (exact[i]) pages.subList(chapterStart[i], end) else rest[i] ?: pages.subList(chapterStart[i], end)
            if (i in rest) exact[i] = true
        }
        pages = all
        chapterStart = starts
    }

    /**
     * Lays out one chapter again with [paginator] (from the last [paginate]), for example once its islands have their
     * sizes. Other chapters keep their pages; only page numbers after it move.
     */
    fun repaginate(chapter: Int, paginator: Paginator) {
        val first = chapterStart[chapter]
        val end = if (chapter + 1 < chapters.size) chapterStart[chapter + 1] else pages.size
        val fresh = paginator.paginate(chapter, chapters[chapter])
        pages = pages.subList(0, first) + fresh + pages.subList(end, pages.size)
        val shift = fresh.size - (end - first)
        for (i in chapter + 1 until chapters.size) chapterStart[i] += shift
        exact[chapter] = true
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

    /** Every island in reading order, with its chapter (block islands and inline ones). */
    fun islands(chapter: Int): List<Pair<Int, String>> = chapters[chapter].blocks.flatMap { b ->
        when (b) {
            is Block.Island -> listOf(b.id to b.xml)
            is Block.Text -> b.text.getSpans(0, b.text.length, IslandSpan::class.java).map { it.id to it.xml }
            is Block.Image -> emptyList()
        }
    }
}

/** Marks an image whose size couldn't be read (a concurrent map holds no nulls). */
private val NO_SIZE = -1 to -1
