package com.flowfolio.core.conversion.ocr

/**
 * The converter's reader for scanned pages (ADR-0054): it finds the page's script, then reads it the agreed way.
 * - Latin script: PaddleOCR, kept when its confidence reaches the gate.
 * - Arabic: Tesseract's text, kept when Tesseract and PaddleOCR agree ([OcrGate]). Without a Tesseract reader (not
 *   yet on the phone), Arabic pages stay pictures: the Latin model can be 99.8% sure of nonsense on Arabic print, so
 *   it must never read them.
 */
class ScannedPageReader(
    private val latin: PaddleOcr,
    private val arabic: PaddleOcr? = null,
    private val tesseractArabic: OcrReader? = null
) : OcrReader {
    override fun read(image: OcrImage): OcrPage? {
        // One detection serves the script check and the reading: the two recognisers share PaddleOCR's detector.
        val rects = latin.detect(image)
        val script = arabic?.let { Script.of(image, rects, latin, it) } ?: Script.LATIN
        return when (script) {
            Script.LATIN -> latin.read(image, rects).takeIf { it.confidence >= OcrGate.MIN_CONFIDENCE }
            Script.ARABIC -> tesseractArabic?.let { OcrGate(it, arabic).read(image) }
        }
    }
}
