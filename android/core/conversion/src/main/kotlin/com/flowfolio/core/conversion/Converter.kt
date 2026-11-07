package com.flowfolio.core.conversion

import com.flowfolio.core.conversion.epub.EpubWriter
import com.flowfolio.core.conversion.epub.Sidecar
import com.flowfolio.core.conversion.epub.SourceInfo
import com.flowfolio.core.conversion.ir.Block
import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.ir.Document
import com.flowfolio.core.conversion.ir.OutlineEntry
import com.flowfolio.core.conversion.ir.Page
import com.flowfolio.core.conversion.ir.PageKind
import com.flowfolio.core.conversion.ir.Role
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
        /** Share of a page's text that must agree with its glyph geometry, or the page goes out as an image. */
        val geometricCoverageThreshold: Float = 0.9f,
        /** Pixels per point for figures, tables and equations (2 is 144 dpi). */
        val figureScale: Float = 2f,
        /** Pixels per point for whole-page images. */
        val pageScale: Float = 1.5f
    )

    /** Progress: [stage] (`reading`, `layout`, `images`, `writing`) and how far into it. */
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
        val pages = (0 until pdf.pageCount).map { i ->
            progress(Progress("reading", i, pdf.pageCount))
            Extraction.page(pdf.page(i)).also { it.label = pdf.pageLabel(i) }
        }
        val title = pdf.metadata("Title") ?: source.fileName.substringBeforeLast('.')
        val outline = pdf.outline().map { OutlineEntry(it.depth, it.title, it.page) }
        return Document(title, pdf.metadata("Author"), pages, outline)
    }

    private fun layOut(doc: Document, progress: (Progress) -> Unit) {
        val blocks = Blocks(doc.styles)
        // Chapter titles: lines in the book's chapter-title style, or pieces of a chapter's outline title on its page.
        val titleStyles = doc.styles.filter { it.role == Role.HEADING && it.level == 1 }.map { it.key }.toSet()
        val codeStyles = doc.styles.filter { it.role == Role.CODE }.map { it.key }.toSet()
        val chapterTitles = doc.outline.filter { it.depth == 0 }.groupBy({ it.page }, { it.title })
        doc.pages.forEachIndexed { n, page ->
            progress(Progress("layout", n, doc.pages.size))
            if (page.kind == PageKind.BORN_DIGITAL) {
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
        val broken = page.kind == PageKind.SCANNED ||
            page.coverage < settings.coverageThreshold ||
            page.geometricCoverage < settings.geometricCoverageThreshold
        if (!empty && broken) {
            val text = Blocks.join(page.lines.filter { !it.artifact })
            val method = when {
                page.kind == PageKind.SCANNED -> "fallback:scanned"
                page.coverage < settings.coverageThreshold -> "fallback:coverage"
                else -> "fallback:geometry"
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
        doc.pages.forEachIndexed { n, page ->
            progress(Progress("images", n, doc.pages.size))
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
            if (visible.isEmpty()) return@forEachIndexed
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
        return out
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
