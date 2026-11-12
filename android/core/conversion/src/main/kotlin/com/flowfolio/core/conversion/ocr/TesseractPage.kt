package com.flowfolio.core.conversion.ocr

/**
 * One line Tesseract read: [text] in Tesseract's own order (logical order, for Arabic as for Latin), [box] in pixels,
 * [confidence] the line's word confidence as 0 to 1.
 */
class TesseractLine(val text: String, val box: PixelBox, val confidence: Float)

/**
 * Tesseract's raw lines into a page (ADR-0054). Tesseract already gives lines top to bottom in logical order (unlike
 * PaddleOCR's Arabic model, which reads in visual order and is turned back by [RtlText]). Empty and blank lines go.
 */
object TesseractPage {
    fun build(lines: List<TesseractLine>, engine: String): OcrPage {
        val kept = lines.mapNotNull { line ->
            line.text.trim().takeIf { it.isNotEmpty() }?.let { OcrLine(it, line.box, line.confidence) }
        }
        return OcrPage(kept, engine)
    }
}
