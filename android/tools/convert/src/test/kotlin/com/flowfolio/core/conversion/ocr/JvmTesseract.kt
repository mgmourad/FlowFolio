package com.flowfolio.core.conversion.ocr

import java.awt.image.BufferedImage
import java.io.File
import net.sourceforge.tess4j.ITessAPI
import net.sourceforge.tess4j.Tesseract

/**
 * [OcrReader] for Tesseract on the desktop JVM, through tess4j (ADR-0054): the same behaviour as the phone's
 * `TesseractOcr`, so the corpus gate measures what the phone runs. Tesseract returns lines in logical order, so no
 * reordering is applied. Requires libtesseract on the machine (CI installs it).
 */
class JvmTesseract(datapath: File, private val lang: String, engine: String? = null) : OcrReader {
    private val tesseract = Tesseract().apply {
        setDatapath(datapath.path)
        setLanguage(lang)
        setPageSegMode(PSM_AUTO)
    }
    private val engineId = engine ?: "tesseract-$lang"

    override fun read(image: OcrImage): OcrPage {
        val buffered = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        buffered.setRGB(0, 0, image.width, image.height, image.pixels, 0, image.width)
        val words = tesseract.getWords(buffered, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE)
        val lines = words.map { w ->
            val b = w.boundingBox
            TesseractLine(w.text, PixelBox(b.x, b.y, b.x + b.width, b.y + b.height), w.confidence / HUNDRED)
        }
        return TesseractPage.build(lines, engineId)
    }

    private companion object {
        const val PSM_AUTO = 3
        const val HUNDRED = 100f
    }
}
