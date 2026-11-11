package com.flowfolio.core.conversion.ocr

import com.flowfolio.core.conversion.pdf.FontInfo
import com.flowfolio.core.conversion.pdf.RawPage

/**
 * A page read by OCR, as the characters PDFium would give for a typed page (ADR-0054). From here the converter's
 * usual stages place it: lines, reading order, paragraphs and headings. Each line's characters share its box evenly
 * (left to right; OCR gives no per-character boxes); its font size follows the line's height, rounded to a half point
 * so lines of one style group together; lines are separated by generated breaks. The scan itself is left out of
 * [RawPage.images], or the page would still look scanned.
 */
object OcrPages {
    fun raw(index: Int, width: Float, height: Float, page: OcrPage, scale: Float): RawPage {
        // Lines near the page's typical height share its size: OCR line heights vary with ascenders and descenders,
        // and sizes that differ by a hair would read as different styles, some of them headings.
        val heights = page.lines.map { it.box.height }.sorted()
        val typical = if (heights.isEmpty()) 0 else heights[heights.size / 2]
        val lengths = page.lines.map { it.text.length }.sorted()
        val typicalLength = if (lengths.isEmpty()) 0 else lengths[lengths.size / 2]
        val text = StringBuilder()
        val boxes = ArrayList<Float>()
        val sizes = ArrayList<Float>()
        for ((n, line) in page.lines.withIndex()) {
            if (n > 0) {
                text.append('\n')
                boxes.addAll(NO_BOX)
                sizes.add(0f)
            }
            val b = line.box
            val left = b.left / scale
            val right = b.right / scale
            val top = b.top / scale
            val bottom = b.bottom / scale
            val step = (right - left) / line.text.length
            val near = b.height in (typical * SAME_LOW).toInt()..(typical * SAME_HIGH).toInt()
            // A tall line as long as the body's is body text with a drop cap, not a heading (headings are short).
            val dropCap = b.height > typical && line.text.length >= typicalLength * LONG_SHARE
            val height = if (near || dropCap) typical else b.height
            val size = (height / scale * TEXT_SHARE * 2).toInt() / 2f
            for ((i, _) in line.text.withIndex()) {
                boxes.addAll(listOf(left + i * step, top, left + (i + 1) * step, bottom))
                sizes.add(size)
            }
            text.append(line.text)
        }
        val length = text.length
        val generated = BooleanArray(length) { text[it] == '\n' }
        return RawPage(
            index = index,
            width = width,
            height = height,
            text = text.toString(),
            boxes = boxes.toFloatArray(),
            fontSizes = sizes.toFloatArray(),
            fontWeights = IntArray(length) { REGULAR },
            fontIds = IntArray(length) { if (generated[it]) -1 else 0 },
            colors = IntArray(length) { BLACK },
            generated = generated,
            fonts = listOf(FontInfo("${page.engine}-ocr", 0)),
            images = emptyList(),
            paths = emptyList()
        )
    }

    /** About this share of a detected line's (grown) box is the type itself. */
    private const val TEXT_SHARE = 0.62f
    private const val SAME_LOW = 0.8f
    private const val SAME_HIGH = 1.3f
    private const val LONG_SHARE = 0.6f
    private const val REGULAR = 400
    private const val BLACK = 0xFF000000.toInt()
    private val NO_BOX = listOf(-1f, -1f, -1f, -1f)
}
