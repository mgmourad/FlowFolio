package com.flowfolio.core.conversion.ocr

/**
 * Which script a scanned page is in, so it goes to the right reader (ADR-0054). Both PaddleOCR recognisers read the
 * page's few longest lines; the one surer of itself names the script. Both are PaddleOCR models scored the same way,
 * so here their confidences can be compared (unlike PaddleOCR's against Tesseract's).
 */
enum class Script {
    LATIN,
    ARABIC;

    companion object {
        fun of(image: OcrImage, latin: PaddleOcr, arabic: PaddleOcr): Script =
            of(image, latin.detect(image), latin, arabic)

        /** As [of], for lines already found by the shared detector. */
        internal fun of(image: OcrImage, rects: List<RotatedRect>, latin: PaddleOcr, arabic: PaddleOcr): Script {
            val lines = rects.sortedByDescending { it.width }.take(SAMPLE_LINES)
            if (lines.isEmpty()) return LATIN
            val asLatin = lines.map { latin.recognise(image, it).second }.average()
            val asArabic = lines.map { arabic.recognise(image, it).second }.average()
            return if (asArabic > asLatin) ARABIC else LATIN
        }

        private const val SAMPLE_LINES = 4
    }
}
