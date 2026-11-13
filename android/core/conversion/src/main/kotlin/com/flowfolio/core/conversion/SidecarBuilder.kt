package com.flowfolio.core.conversion

import com.flowfolio.core.conversion.epub.Engine
import com.flowfolio.core.conversion.epub.EpubWriter
import com.flowfolio.core.conversion.epub.Graph
import com.flowfolio.core.conversion.epub.Health
import com.flowfolio.core.conversion.epub.HealthSummary
import com.flowfolio.core.conversion.epub.PageHealth
import com.flowfolio.core.conversion.epub.Recipe
import com.flowfolio.core.conversion.epub.Sidecar
import com.flowfolio.core.conversion.epub.SidecarBlock
import com.flowfolio.core.conversion.epub.SidecarPage
import com.flowfolio.core.conversion.epub.SourceInfo
import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.ir.Document
import com.flowfolio.core.conversion.ir.PageKind

/** `flowfolio.json` for a converted [Document] (`docs/conversion-spec.md` section 3). */
object SidecarBuilder {
    /** The converter's version, raised whenever its output could change. */
    const val ENGINE_VERSION = "1.6.0"

    // One field per section of flowfolio.json, in the spec's order.
    @Suppress("LongMethod")
    fun build(
        doc: Document,
        source: SourceInfo,
        threshold: Float,
        complete: Boolean = true,
        finishedSpineItems: List<String>? = null
    ): Sidecar {
        val fileOf = doc.chapters.flatMap { c -> c.pages.map { it to c.file } }.toMap()
        val blockFile = doc.chapters.flatMap { c -> c.blocks.map { it.id to c.file } }.toMap()
        val pages = doc.pages.map { p ->
            SidecarPage(
                page = p.index + 1,
                pageLabel = p.label,
                width = p.width,
                height = p.height,
                kind = when {
                    p.ocr != null -> "scanned"
                    else -> kindOf(p.kind)
                },
                xhtml = "${fileOf[p.index] ?: doc.chapters.firstOrNull()?.file.orEmpty()}#page-${p.index + 1}"
            )
        }
        val ocrPage = doc.pages.associate { it.index to it.ocr }
        val blocks = doc.pages.flatMap { it.blocks }.map { b ->
            SidecarBlock(
                blockId = b.id,
                type = b.type.json,
                page = b.page + 1,
                bbox = listOf(b.box.left, b.box.top, b.box.right, b.box.bottom).map(::hundredths),
                xhtml = "${blockFile[b.id].orEmpty()}#${b.id}",
                confidence = b.confidence,
                // Text read from a scan says so (spec: `ocr:<engine>`), with the engine's confidence.
                method = ocrPage[b.page]?.takeIf { b.type != BlockType.FALLBACK } ?: b.method,
                continuesFrom = b.continuesFrom
            )
        }
        val health = doc.pages.map { p ->
            PageHealth(
                page = p.index + 1,
                textCoverage = (p.coverage * 1000).toInt() / 1000f,
                geometricCoverage = (p.geometricCoverage * 1000).toInt() / 1000f,
                status = when {
                    p.kind == PageKind.SCANNED -> "scanned"
                    p.fallback -> "degraded"
                    else -> "ok"
                },
                drawingsAsImages = p.drawings
            )
        }
        val summary = HealthSummary(
            pages = doc.pages.size,
            pagesBelowThreshold = doc.pages.count { it.coverage < threshold },
            fallbackPages = doc.pages.count { it.fallback },
            scannedPages = doc.pages.count { it.kind == PageKind.SCANNED || it.ocr != null },
            chapters = doc.chapters.size,
            chaptersFrom = when {
                doc.outline.count { it.depth == 0 && it.page >= 0 } >= 2 -> "outline"
                doc.chapters.size > 1 && doc.chapters.none { it.title.startsWith("Pages ") } -> "headings"
                else -> "pages"
            },
            fallbackBlocks = doc.pages.sumOf { p -> p.blocks.count { it.type in IMAGES } }
        )
        return Sidecar(
            specVersion = EpubWriter.SPEC_VERSION,
            bookId = source.bookId(),
            complete = complete,
            finishedSpineItems = finishedSpineItems,
            source = source,
            pages = pages,
            blocks = blocks,
            graph = Graph(),
            health = Health(threshold, summary, health),
            recipe = Recipe(
                engine = Engine("flowfolio-converter", ENGINE_VERSION),
                components = mapOf("pdfium" to "chromium/8100"),
                settings = mapOf("coverageThreshold" to threshold.toString())
            )
        )
    }

    private val IMAGES = setOf(BlockType.TABLE, BlockType.EQUATION, BlockType.FALLBACK)

    /** Points to two decimals: enough for a block's place, and a smaller sidecar. */
    private fun hundredths(v: Float) = (v * HUNDRED).toInt() / HUNDRED

    private const val HUNDRED = 100f

    private fun kindOf(kind: PageKind): String = when (kind) {
        PageKind.BORN_DIGITAL -> "born-digital"
        PageKind.SCANNED -> "scanned"
        PageKind.EMPTY -> "empty"
    }
}
