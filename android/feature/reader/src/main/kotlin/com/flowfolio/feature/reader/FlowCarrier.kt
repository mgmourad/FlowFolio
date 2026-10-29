package com.flowfolio.feature.reader

import android.content.Context
import android.util.Log
import com.flowfolio.core.data.AnnotationRepository
import com.flowfolio.core.data.BookmarkRepository
import com.flowfolio.core.data.ReaderRepository
import com.flowfolio.core.data.flow.AnnotationCarrier
import com.flowfolio.core.data.flow.DynamicFlow
import com.flowfolio.core.data.flow.FlowNotifications
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.FlowNotice
import com.flowfolio.core.model.Passage
import com.flowfolio.core.pdf.PdfDocument
import com.flowfolio.core.reflow.Block
import com.flowfolio.core.reflow.Locator
import com.flowfolio.core.reflow.ReflowBook
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "Convert the changes" (ADR-0045): brings a PDF book's highlights, notes and bookmarks from the view being left into
 * the other one, in the background, with a notification on the phone and in the app. Each highlight's text is looked
 * for near its mapped place (the converter's page markers): a PDF page's start in Dynamic Flow, or Dynamic Flow's
 * position's PDF page and the pages either side. What can't be found is counted, never dropped from where it was;
 * what the other view already has is not added twice.
 */
@Singleton
class FlowCarrier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reader: ReaderRepository,
    private val annotations: AnnotationRepository,
    private val bookmarks: BookmarkRepository,
    private val dynamicFlow: DynamicFlow
) : AnnotationCarrier {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Carries [bookId]'s items out of Dynamic Flow ([fromFlow]) or out of the PDF, in the background. */
    override fun carry(bookId: String, fromFlow: Boolean) {
        scope.launch {
            val book = reader.book(bookId) ?: return@launch
            val notes = FlowNotifications(context)
            notes.showCarry(bookId, book.title, null, 0)
            dynamicFlow.notify(FlowNotice.CarryStarted(bookId, book.title))
            val (carried, total) = runCatching { if (fromFlow) toPdf(bookId) else toFlow(bookId) }
                .onFailure { Log.w(TAG, "could not carry annotations of $bookId", it) }
                .getOrDefault(0 to 0)
            notes.showCarry(bookId, book.title, carried, total)
            dynamicFlow.notify(FlowNotice.Carried(bookId, book.title, carried, total))
        }
    }

    override fun reanchor(bookId: String) {
        scope.launch {
            runCatching {
                relocate(bookId)
            }.onFailure { Log.w(TAG, "could not find the highlights of $bookId again", it) }
        }
    }

    /**
     * The reading place and each Dynamic Flow highlight found again in the re-converted text. The place is a character
     * offset in the old text, so it is taken again from the PDF page it was on (kept with it); a highlight is found by
     * its words, from a little before its old place on, and one whose words can't be found stays where it was.
     */
    private suspend fun relocate(bookId: String) {
        val inFlow = annotations.observe(bookId).first().filter { it.locator != null }
        val place = reader.book(bookId)?.takeIf { it.flowLocator != null }?.lastPage
        if (inFlow.isEmpty() && place == null) return
        val opened = reader.openFlow(bookId)
        opened.archive.use {
            val book = ReflowBook(opened.archive, opened.pkg)
            place?.let(book::locatorOfSourcePage)?.let { dynamicFlow.saveFlowPlace(bookId, it.encode(), null) }
            for (a in inFlow) {
                val old = Locator.decode(a.locator) ?: continue
                val near = Locator(old.chapter.coerceIn(book.chapters.indices), a.start)
                val found = find(book, near, a.text)?.takeIf { it.chapter != old.chapter || it.offset != a.start }
                if (found != null) {
                    val end = found.offset + spanOf(book, found, a.text) - 1
                    annotations.relocate(a.id, found.encode(), found.offset, maxOf(found.offset, end))
                }
            }
        }
    }

    /** PDF to Dynamic Flow: each highlight found in the chapter where its page starts; bookmarks at that start. */
    private suspend fun toFlow(bookId: String): Pair<Int, Int> {
        val all = annotations.observe(bookId).first()
        val marks = bookmarks.observe(bookId).first()
        val from = all.filter { it.locator == null }
        val fromMarks = marks.filter { it.locator == null }
        val have = all.filter { it.locator != null }
        val haveMarks = marks.mapNotNull { it.locator }.toSet()
        val opened = reader.openFlow(bookId)
        var carried = 0
        opened.archive.use {
            val book = ReflowBook(opened.archive, opened.pkg)
            for (a in from) {
                val found = book.locatorOfSourcePage(a.page)?.let { find(book, it, a.text) } ?: continue
                carried++
                if (have.none { Locator.decode(it.locator)?.chapter == found.chapter && it.start == found.offset }) {
                    val end = found.offset + spanOf(book, found, a.text) - 1
                    // Its page and rects are worked out where the reader next shows it (ADR-0040).
                    annotations.add(
                        bookId,
                        Passage(
                            0,
                            a.label,
                            found.offset..maxOf(found.offset, end),
                            a.text,
                            emptyList(),
                            found.encode()
                        ),
                        a.color,
                        a.note
                    )
                }
            }
            for (m in fromMarks) {
                val at = book.locatorOfSourcePage(m.page) ?: continue
                carried++
                if (at.encode() !in haveMarks) bookmarks.add(bookId, 0, m.color, at.encode())
            }
        }
        return carried to from.size + fromMarks.size
    }

    /** Dynamic Flow to the PDF: each highlight found on its PDF page or the pages beside it; bookmarks on that page. */
    private suspend fun toPdf(bookId: String): Pair<Int, Int> {
        val all = annotations.observe(bookId).first()
        val marks = bookmarks.observe(bookId).first()
        val from = all.filter { it.locator != null }
        val fromMarks = marks.filter { it.locator != null }
        val have = all.filter { it.locator == null }
        val haveMarks = marks.filter { it.locator == null }.map { it.page }.toSet()
        val flow = reader.openFlow(bookId)
        val pages = flow.archive.use {
            val book = ReflowBook(flow.archive, flow.pkg)
            Pair(
                from.associateWith { a -> Locator.decode(a.locator)?.let(book::sourcePageOf) },
                fromMarks.associateWith { m -> Locator.decode(m.locator)?.let(book::sourcePageOf) }
            )
        }
        val pdf = reader.open(bookId)
        var carried = 0
        try {
            for ((a, page) in pages.first) {
                val hit = page?.let { findOnPdf(pdf.document, it, a) } ?: continue
                carried++
                if (have.none { it.page == hit.first && it.start == hit.second.first }) {
                    val text = pdf.document.pageText(hit.first)
                    annotations.add(
                        bookId,
                        Passage(hit.first, a.label, hit.second, a.text, TextSelection.rects(text, hit.second)),
                        a.color,
                        a.note
                    )
                }
            }
            for ((m, page) in pages.second) {
                page ?: continue
                carried++
                if (page !in haveMarks) bookmarks.add(bookId, page, m.color)
            }
        } finally {
            pdf.document.close()
        }
        return carried to from.size + fromMarks.size
    }

    /** [quote] on PDF page [page] or the pages either side: the page and the characters it covers. */
    private suspend fun findOnPdf(doc: PdfDocument, page: Int, a: Annotation): Pair<Int, IntRange>? =
        listOf(page, page + 1, page - 1).filter { it in 0 until doc.pageCount }.firstNotNullOfOrNull { p ->
            val text = runCatching { doc.pageText(p) }.getOrNull()
            text?.let { Matcher(it.text).find(a.text, 0) }?.let { p to it }
        }

    /** [quote] in the chapter of [near] from a little before it on (and in the next chapter): where it starts. */
    private fun find(book: ReflowBook, near: Locator, quote: String): Locator? {
        for (chapter in listOf(near.chapter, near.chapter + 1).filter { it in book.chapters.indices }) {
            val from = if (chapter == near.chapter) (near.offset - LOOK_BACK).coerceAtLeast(0) else 0
            val range = Matcher(chapterText(book, chapter)).find(quote, from) ?: continue
            return Locator(chapter, range.first)
        }
        return null
    }

    /** How many characters of the chapter [quote] covers from [at] (spacing can differ between the views). */
    private fun spanOf(book: ReflowBook, at: Locator, quote: String): Int =
        Matcher(chapterText(book, at.chapter)).find(quote, at.offset)?.let { it.last - it.first + 1 } ?: quote.length

    /** A chapter's text at its locator offsets: text blocks as they are, pictures and islands as one character each. */
    private fun chapterText(book: ReflowBook, chapter: Int): String {
        val c = book.chapters[chapter]
        val out = CharArray(c.length) { OBJECT }
        for (b in c.blocks) {
            if (b is Block.Text) {
                val t = b.text
                for (i in 0 until minOf(t.length, c.length - b.start)) out[b.start + i] = t[i]
            }
        }
        return String(out)
    }

    /** Finds a quote in a text ignoring differences of spacing, hyphenation at line ends and case. */
    internal class Matcher(text: String) {
        private val normal = StringBuilder()
        private val origin = ArrayList<Int>()

        init {
            var space = true
            for ((i, ch) in text.withIndex()) {
                when {
                    ch.isWhitespace() || ch == OBJECT -> if (!space) {
                        normal.append(' ')
                        origin += i
                        space = true
                    }

                    ch == SOFT_HYPHEN -> Unit

                    else -> {
                        normal.append(ch.lowercaseChar())
                        origin += i
                        space = false
                    }
                }
            }
        }

        fun find(quote: String, fromOriginal: Int): IntRange? {
            val q = quote.lowercase().replace(SPACES, " ").trim()
            val start = origin.indexOfFirst { it >= fromOriginal }.coerceAtLeast(0)
            val at = if (q.length < MIN_QUOTE) -1 else normal.indexOf(q, start)
            return if (at < 0) null else origin[at]..origin[at + q.length - 1]
        }
    }

    private companion object {
        const val TAG = "FlowFolioFlow"
        const val OBJECT = '￼'
        const val SOFT_HYPHEN = '­'

        /** How far before a page's marker a highlight may start (a paragraph begun on the page before). */
        const val LOOK_BACK = 2_000

        /** Shorter quotes are too common to place safely. */
        const val MIN_QUOTE = 3
        val SPACES = Regex("\\s+")
    }
}
