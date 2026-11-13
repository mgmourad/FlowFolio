package com.flowfolio.core.conversion

import com.flowfolio.core.conversion.epub.EpubWriter
import com.flowfolio.core.conversion.epub.Sidecar
import com.flowfolio.core.conversion.epub.SourceInfo
import com.flowfolio.core.conversion.ir.Block
import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.ir.Chapter
import com.flowfolio.core.conversion.ir.Document
import com.flowfolio.core.conversion.ir.OutlineEntry
import com.flowfolio.core.conversion.ir.Page
import com.flowfolio.core.conversion.ir.PageKind
import com.flowfolio.core.conversion.ir.Role
import com.flowfolio.core.conversion.ocr.OcrPages
import com.flowfolio.core.conversion.ocr.OcrReader
import com.flowfolio.core.conversion.ocr.OcrResult
import com.flowfolio.core.conversion.pdf.Box
import com.flowfolio.core.conversion.pdf.Crop
import com.flowfolio.core.conversion.pdf.EncodedImage
import com.flowfolio.core.conversion.pdf.PdfSource
import com.flowfolio.core.conversion.stage.Blocks
import com.flowfolio.core.conversion.stage.DesignSystem
import com.flowfolio.core.conversion.stage.Extraction
import com.flowfolio.core.conversion.stage.GeometricVerify
import com.flowfolio.core.conversion.stage.Item
import com.flowfolio.core.conversion.stage.Linking
import com.flowfolio.core.conversion.stage.ReadingOrder
import com.flowfolio.core.conversion.stage.Regions
import com.flowfolio.core.conversion.stage.Shapes
import com.flowfolio.core.conversion.stage.TextDirection
import java.io.OutputStream

/**
 * Converter v1 (Phase 3): a born-digital PDF into EPUB 3 plus `flowfolio.json`. Stages A and B read every page, C
 * infers the design system, D lays out each page in reading order, F links the book into chapters and I writes the
 * package. Nothing goes out silently wrong: a page whose text can't be accounted for, and every scanned page, goes out
 * as an image of the page; tables and equations are images this phase (Phases 4, 5 and 9 take them further).
 */
class Converter(private val settings: Settings = Settings()) {
    data class Settings(
        /** Share of a page's text that must be found in its blocks, or the page goes out as an image (spec 4). */
        val coverageThreshold: Float = 0.98f,
        /** Pixels per point for figures, tables and equations (3 is 216 dpi, sharp when pinched). */
        val figureScale: Float = 3f,
        /** Pixels per point for whole-page images (2 is 144 dpi). */
        val pageScale: Float = 2f,
        /** Reads scanned pages (ADR-0054); null keeps every scanned page a picture, as before. */
        val ocr: com.flowfolio.core.conversion.ocr.OcrReader? = null,
        /** Pixels per point a scanned page is drawn at for OCR (3 is 216 dpi; a US Letter page is 1836 px wide). */
        val ocrScale: Float = 3f,
        /** Remembers each scanned page's OCR, so a stopped conversion resumes; null reads every page afresh. */
        val ocrCache: com.flowfolio.core.conversion.ocr.OcrCache? = null
    )

    /** Progress: [stage] (`reading`, `ocr` for scanned pages, `layout`, `images`, `writing`) and how far into it. */
    data class Progress(val stage: String, val done: Int, val total: Int)

    class Result(val document: Document, val sidecar: Sidecar)

    /**
     * Converts [pdf] (the file described by [source]) and writes the EPUB to [epub]. [modified] is the package's
     * `dcterms:modified` (`2026-10-27T12:00:00Z`).
     */
    fun convert(
        pdf: PdfSource,
        source: SourceInfo,
        epub: OutputStream,
        modified: String,
        progress: (Progress) -> Unit = {}
    ): Result {
        val doc = analyse(pdf, source, progress)
        val images = images(pdf, doc, progress)
        progress(Progress("writing", 0, 1))
        EpubWriter(doc, source.bookId(), modified).write(epub, images)
        progress(Progress("writing", 1, 1))
        return Result(doc, SidecarBuilder.build(doc, source, settings.coverageThreshold))
    }

    /**
     * Converts [pdf] progressively (spec 6): [emit] is called after each chapter's images are rendered, with a writer
     * of the EPUB for the chapters finished so far (a valid EPUB of only those chapters), its sidecar (`complete:
     * false` and [Sidecar.finishedSpineItems] until the last), and [finished] of [total] chapters done. The final emit
     * is the complete bundle, byte-identical to [convert]'s.
     *
     * The EPUB is written to whatever stream the caller gives, never held as bytes here: a scanned book of pictures
     * makes a 67 MB EPUB, and building it in memory and copying it out ran the conversion process out of memory
     * (the owner's Arabic book on `fold8_api37`, 2026-11-11).
     */
    fun convertProgressively(
        pdf: PdfSource,
        source: SourceInfo,
        modified: String,
        progress: (Progress) -> Unit = {},
        emit: (writeEpub: (OutputStream) -> Unit, sidecar: Sidecar, finished: Int, total: Int) -> Unit
    ): Result {
        val doc = analyse(pdf, source, progress)
        val images = LinkedHashMap<String, ByteArray>()
        val total = doc.chapters.size
        doc.chapters.forEachIndexed { i, chapter ->
            renderChapter(pdf, doc, chapter, images) { page -> progress(Progress("images", page, doc.pages.size)) }
            val finished = i + 1
            val complete = finished == total
            val chapters = doc.chapters.subList(0, finished)
            emit(
                { out -> EpubWriter(doc, source.bookId(), modified).write(out, images, chapters) },
                SidecarBuilder.build(
                    doc,
                    source,
                    settings.coverageThreshold,
                    complete = complete,
                    finishedSpineItems = if (complete) null else doc.chapters.subList(0, finished).map { it.file }
                ),
                finished,
                total
            )
        }
        progress(Progress("writing", 1, 1))
        return Result(doc, SidecarBuilder.build(doc, source, settings.coverageThreshold))
    }

    /** Stages A to F without images or the package: the book's structure, for the corpus metrics and debugging. */
    fun analyse(pdf: PdfSource, source: SourceInfo, progress: (Progress) -> Unit = {}): Document {
        val doc = read(pdf, source, progress)
        DesignSystem.apply(doc)
        layOut(doc, progress)
        Linking.apply(doc)
        return doc
    }

    private fun read(pdf: PdfSource, source: SourceInfo, progress: (Progress) -> Unit): Document {
        if (pdf.pageCount <=
            0
        ) {
            throw ConversionException(ConversionException.Reason.NO_PAGES, "${source.fileName} has no pages")
        }
        val ocr = settings.ocr?.forBook()
        val pages = (0 until pdf.pageCount).map { i ->
            progress(Progress("reading", i, pdf.pageCount))
            ocrIfScanned(pdf, i, Extraction.page(pdf.page(i)), ocr, progress).also {
                it.label = pdf.pageLabel(i)
                // The front and back covers are full-page designs, not reading text: keep each as one image. The
                // first/last page counts only when it is a design (any image or path) with little text — a title,
                // index or colophon page that happens to sit on an edge and carries a rule or logo is reading text,
                // not a cover.
                val edge = i == 0 || i == pdf.pageCount - 1
                it.cover = edge && (it.images.isNotEmpty() || it.paths.isNotEmpty()) && it.sourceChars < COVER_MAX_CHARS
            }
        }
        val title = pdf.metadata("Title") ?: source.fileName.substringBeforeLast('.')
        val outline = pdf.outline().map { OutlineEntry(it.depth, it.title, it.page) }
        return Document(title, pdf.metadata("Author"), pages, outline).also { doc ->
            // The book's script: a book whose strong letters read mostly right to left is Arabic, and its EPUB turns
            // right to left (dir="rtl", lang="ar", page-progression-direction="rtl").
            val text = pages.joinToString("") { p -> p.lines.joinToString("") { it.text } }
            doc.language = if (TextDirection.mostlyRightToLeft(text)) "ar" else "en"
        }
    }

    /**
     * A scanned page is read by [Settings.ocr] when there is one: what it trusts becomes the page's characters, so the
     * usual stages lay it out; what it does not stays a picture, marked so the sidecar says why.
     */
    private fun ocrIfScanned(pdf: PdfSource, i: Int, page: Page, ocr: OcrReader?, progress: (Progress) -> Unit): Page {
        val reader = ocr?.takeIf { page.kind == PageKind.SCANNED } ?: return page
        progress(Progress("ocr", i, pdf.pageCount))
        val result = settings.ocrCache?.get(i) ?: readPage(pdf, i, reader).also { settings.ocrCache?.put(i, it) }
        val read = result.page
        page.ocrRejected = result.rendered && read == null
        return if (read == null) {
            page
        } else {
            Extraction.page(OcrPages.raw(i, page.width, page.height, read, result.scale)).also {
                it.ocr = "ocr:${read.engine}@${"%.2f".format(java.util.Locale.ROOT, read.confidence)}"
            }
        }
    }

    /** Draws page [i] and reads it; what is kept must have [MIN_OCR_CHARS] of text. */
    private fun readPage(pdf: PdfSource, i: Int, reader: OcrReader): OcrResult {
        val image = pdf.renderPixels(i, settings.ocrScale) ?: return OcrResult(null, 0f, rendered = false)
        val read = reader.read(image)?.takeIf { r -> r.lines.sumOf { it.text.length } >= MIN_OCR_CHARS }
        return OcrResult(read, image.scale, rendered = true)
    }

    private fun layOut(doc: Document, progress: (Progress) -> Unit) {
        val blocks = Blocks(doc.styles).also { it.pageCount = doc.pages.size }
        // Chapter titles: lines in the book's chapter-title style, or pieces of a chapter's outline title on its page.
        val titleStyles = doc.styles.filter { it.role == Role.HEADING && it.level == 1 }.map { it.key }.toSet()
        val codeStyles = doc.styles.filter { it.role == Role.CODE }.map { it.key }.toSet()
        val chapterTitles = doc.outline.filter { it.depth == 0 }.groupBy({ it.page }, { it.title })
        doc.pages.forEachIndexed { n, page ->
            progress(Progress("layout", n, doc.pages.size))
            if (page.kind == PageKind.BORN_DIGITAL && !page.cover) {
                val lines = page.lines.filter { !it.artifact }
                val onPage = chapterTitles[page.index].orEmpty()
                val regions = Regions.find(
                    page,
                    lines,
                    isTitle = { l -> l.style in titleStyles || onPage.any { Linking.isPartOf(l.text, it) } },
                    isCode = { l -> l.style in codeStyles }
                )
                val claimed = regions.flatMap { it.lines }.toSet()
                val placed = Shapes.place(page, lines.filter { it !in claimed }, regions)
                val items = placed.lines.map { Item.Text(it) } + (regions + placed.equations).map { Item.Area(it) }
                page.blocks = blocks.of(page, ReadingOrder.order(page, items))
                page.drawings = placed.lines.sumOf { it.inlines.size } + placed.equations.size
            }
            check(page)
        }
    }

    /** The page's coverage; a page below the threshold, or scanned, becomes one image of the whole page. */
    private fun check(page: Page) {
        val expected =
            page.sourceChars - page.lines.filter { it.artifact }.sumOf { l -> l.text.count { !it.isWhitespace() } }
        val found = page.blocks.sumOf { it.chars }
        page.coverage = if (expected <= 0) 1f else (found.toFloat() / expected).coerceAtMost(1f)
        page.geometricCoverage = GeometricVerify.coverage(page.lines.filter { !it.artifact })
        val empty = page.kind == PageKind.EMPTY
        val broken = page.kind == PageKind.SCANNED || page.cover || page.coverage < settings.coverageThreshold
        if (!empty && broken) {
            val text = Blocks.join(page.lines.filter { !it.artifact })
            val method = when {
                page.kind == PageKind.SCANNED && page.ocrRejected -> "fallback:ocr-low-confidence"
                page.kind == PageKind.SCANNED -> "fallback:scanned"
                page.cover -> "fallback:cover"
                else -> "fallback:coverage"
            }
            page.blocks = listOf(
                Block(
                    BlockType.FALLBACK,
                    page.index,
                    Box(0f, 0f, page.width, page.height),
                    text,
                    region = Box(0f, 0f, page.width, page.height),
                    chars = expected.coerceAtLeast(0),
                    confidence = page.coverage,
                    method = method
                )
            )
            page.fallback = true
        }
    }

    /** Every picture of the book (figures, tables, equations, inline drawings, page fallbacks), one render per page. */
    private fun images(pdf: PdfSource, doc: Document, progress: (Progress) -> Unit): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        doc.chapters.forEach { chapter ->
            renderChapter(pdf, doc, chapter, out) { page -> progress(Progress("images", page, doc.pages.size)) }
        }
        return out
    }

    /** Renders [chapter]'s pictures into [out], reporting each page via [onPage] (its 0-based index, in order). */
    private fun renderChapter(
        pdf: PdfSource,
        doc: Document,
        chapter: Chapter,
        out: MutableMap<String, ByteArray>,
        onPage: (Int) -> Unit
    ) {
        for (pageIndex in chapter.pages) {
            onPage(pageIndex)
            val page = doc.pages[pageIndex]
            // Each crop: its file name (without extension), what it shows, and where its name goes once drawn.
            val crops = ArrayList<Triple<String, Crop, (String, EncodedImage) -> Unit>>()
            for (b in page.blocks) {
                b.region?.let { r ->
                    val photo = b.type == BlockType.FIGURE || b.type == BlockType.FALLBACK
                    crops += Triple("images/${b.id}", Crop(clip(r, page), photo)) { name, image ->
                        b.image = name
                        b.coloured = image.coloured
                    }
                }
                b.inlines.forEachIndexed { k, inline ->
                    crops += Triple("images/${b.id}-${k + 1}", Crop(clip(inline.box, page), false)) { name, _ ->
                        inline.image = name
                    }
                }
            }
            val visible = crops.filter { it.second.box.width >= 1f && it.second.box.height >= 1f }
            if (visible.isEmpty()) continue
            val scale = if (page.fallback) settings.pageScale else settings.figureScale
            val drawn = pdf.renderImages(page.index, visible.map { it.second }, scale)
            visible.forEachIndexed { k, (base, _, setName) ->
                val image = drawn.getOrNull(k)
                if (image != null && image.bytes.isNotEmpty()) {
                    val name = "$base.${image.extension}"
                    out[name] = image.bytes
                    setName(name, image)
                }
            }
        }
    }

    private fun clip(b: Box, page: Page) = Box(
        b.left.coerceAtLeast(0f),
        b.top.coerceAtLeast(0f),
        b.right.coerceAtMost(page.width),
        b.bottom.coerceAtMost(page.height)
    )
}

/** The book's id (`docs/conversion-spec.md`): the first 32 hex characters of the source's SHA-256. */
fun SourceInfo.bookId(): String = sha256.take(BOOK_ID_CHARS)

private const val BOOK_ID_CHARS = 32

/** A cover is a design: a first/last page with graphics but little text (a title or index page has far more). */
private const val COVER_MAX_CHARS = 300

/** Fewer characters than this from OCR is not a page of text (a figure, a stamp): it stays a picture. */
private const val MIN_OCR_CHARS = 40
