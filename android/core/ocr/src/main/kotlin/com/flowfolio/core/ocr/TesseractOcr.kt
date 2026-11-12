package com.flowfolio.core.ocr

import com.flowfolio.core.conversion.ocr.OcrImage
import com.flowfolio.core.conversion.ocr.OcrPage
import com.flowfolio.core.conversion.ocr.OcrReader
import com.flowfolio.core.conversion.ocr.PixelBox
import com.flowfolio.core.conversion.ocr.TesseractLine
import com.flowfolio.core.conversion.ocr.TesseractPage
import java.io.File

/**
 * Tesseract as an [OcrReader] on the phone (ADR-0054): reads a page with the `tessdata_best` [lang] model in
 * [datapath] and returns its lines with boxes. Tesseract returns lines in logical order (Arabic as well as Latin), so
 * no reordering is applied; the engine id goes into the sidecar as `ocr:<engine>@<confidence>`.
 */
class TesseractOcr(datapath: File, private val lang: String, engine: String? = null) : OcrReader {
    private val bridge = TesseractBridge(datapath.path, lang)
    private val engineId = engine ?: "tesseract-$lang"

    override fun read(image: OcrImage): OcrPage {
        val (texts, boxes, confidences) = bridge.read(image.pixels, image.width, image.height)
        val lines = texts.indices.map { i ->
            val o = i * BOX_INTS
            TesseractLine(
                texts[i],
                PixelBox(boxes[o + LEFT], boxes[o + TOP], boxes[o + RIGHT], boxes[o + BOTTOM]),
                confidences[i]
            )
        }
        return TesseractPage.build(lines, engineId)
    }

    /** Releases the engine; the reader is loaded once per conversion and normally lives until the process ends. */
    fun close() = bridge.close()

    private companion object {
        /** The bridge packs each line's box as left, top, right, bottom. */
        private const val BOX_INTS = 4
        private const val LEFT = 0
        private const val TOP = 1
        private const val RIGHT = 2
        private const val BOTTOM = 3
    }
}
