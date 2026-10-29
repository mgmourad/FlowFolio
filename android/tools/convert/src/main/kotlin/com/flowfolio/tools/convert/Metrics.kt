package com.flowfolio.tools.convert

import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.ir.Document
import com.flowfolio.core.conversion.ir.PageKind
import com.flowfolio.core.conversion.stage.Linking
import kotlinx.serialization.Serializable

/**
 * One corpus book's conversion metrics (Phase 3 plan, M2 item 6), as kept in `samples/golden/baselines/<id>.json`.
 * Shares are 0 to 1. [secondsPerPage] and [epubMegabytes] are recorded but never gated: they depend on the machine
 * and on the image encoder (PNG on the desktop, WebP on the phone).
 */
@Serializable
data class Metrics(
    val id: String,
    val pages: Int,
    /** Born-digital pages with text: the pages coverage is measured on. */
    val textPages: Int,
    val meanCoverage: Float,
    /** Share of [textPages] at or above the coverage threshold (exit criterion: at least 0.95 across the corpus). */
    val coveredShare: Float,
    val fallbackPages: List<Int>,
    val scannedPages: Int,
    val chapters: Int,
    val chaptersFrom: String,
    val headings: Int,
    val outlineEntries: Int,
    /** Share of outline entries whose page holds a heading with the entry's title. */
    val outlineMatched: Float,
    val figures: Int,
    val tables: Int,
    val equations: Int,
    val inlineDrawings: Int,
    val epubcheckErrors: Int,
    val epubcheckWarnings: Int,
    /** Share of hand-labelled headings found at the labelled level; -1 when the book has no labels. */
    val labelledHeadings: Float = -1f,
    /** Share of hand-labelled block pairs that come out in the labelled order; -1 without labels. */
    val labelledOrder: Float = -1f,
    val secondsPerPage: Float,
    val epubMegabytes: Float
) {
    companion object {
        const val THRESHOLD = 0.98f

// Each argument is one measured input of the book's metrics.
        @Suppress("LongParameterList")
        fun of(
            id: String,
            doc: Document,
            chaptersFrom: String,
            seconds: Float,
            epubBytes: Long,
            errors: Int,
            warnings: Int
        ): Metrics {
            val text = doc.pages.filter { it.kind == PageKind.BORN_DIGITAL && it.sourceChars > 0 }
            val blocks = doc.pages.flatMap { it.blocks }
            val outline = doc.outline.filter { it.page >= 0 && it.title.isNotBlank() }
            val matched = outline.count { e ->
                doc.pages.getOrNull(e.page)?.blocks.orEmpty().any {
                    it.type == BlockType.HEADING && Linking.matches(it.text, e.title)
                }
            }
            return Metrics(
                id = id,
                pages = doc.pages.size,
                textPages = text.size,
                meanCoverage = if (text.isEmpty()) 1f else text.map { it.coverage }.average().toFloat(),
                coveredShare = if (text.isEmpty()) {
                    1f
                } else {
                    text.count {
                        it.coverage >= THRESHOLD
                    }.toFloat() / text.size
                },
                fallbackPages = doc.pages.filter { it.fallback }.map { it.index + 1 },
                scannedPages = doc.pages.count { it.kind == PageKind.SCANNED },
                chapters = doc.chapters.size,
                chaptersFrom = chaptersFrom,
                headings = blocks.count { it.type == BlockType.HEADING },
                outlineEntries = outline.size,
                outlineMatched = if (outline.isEmpty()) 1f else matched.toFloat() / outline.size,
                figures = blocks.count { it.type == BlockType.FIGURE },
                tables = blocks.count { it.type == BlockType.TABLE },
                equations = blocks.count { it.type == BlockType.EQUATION },
                inlineDrawings = blocks.sumOf { it.inlines.size },
                epubcheckErrors = errors,
                epubcheckWarnings = warnings,
                secondsPerPage = if (doc.pages.isEmpty()) 0f else seconds / doc.pages.size,
                epubMegabytes = epubBytes / 1_048_576f
            )
        }
    }
}
