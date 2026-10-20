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
    private val imageSizes = HashMap<String, Pair<Int, Int>?>()

    /**
     * Lays the book out for [spec]. [density] turns CSS pixels (images) into screen pixels; [islandSize] gives
     * measured islands (null: placeholder size).
     */
    fun paginate(spec: PageSpec, density: Float, islandSize: (Int) -> Pair<Int, Int>? = { null }): Paginator {
        this.spec = spec
        val paginator = Paginator(spec) { block ->
            when (block) {
                is Block.Image -> imageSize(block.path)?.let { (w, h) ->
                    (w * density).toInt() to (h * density).toInt()
                }

                is Block.Island -> islandSize(block.id)

                is Block.Text -> null
            }
        }
        val all = ArrayList<Page>()
        val starts = IntArray(chapters.size)
        chapters.forEachIndexed { i, c ->
            starts[i] = all.size
            all += paginator.paginate(i, c)
        }
        pages = all
        chapterStart = starts
        return paginator
    }

    fun imageSize(path: String): Pair<Int, Int>? = imageSizes.getOrPut(path) {
        archive.read(path)?.let { bytes ->
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            if (o.outWidth > 0) o.outWidth to o.outHeight else null
        }
    }

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
