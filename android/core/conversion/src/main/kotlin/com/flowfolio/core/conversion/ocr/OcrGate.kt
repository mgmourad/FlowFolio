package com.flowfolio.core.conversion.ocr

/**
 * Whether a page read by OCR becomes text or stays a picture (ADR-0054). Nothing invented is better than nothing read.
 * - One reader (Latin script, PaddleOCR): its own mean line confidence must reach [MIN_CONFIDENCE].
 * - Two readers (Arabic, PaddleOCR and Tesseract): their words must [agree] at least [MIN_AGREEMENT]. Each engine's
 *   confidence is on its own scale, and Tesseract was 95% sure of a manuscript it misread, so neither engine's own
 *   number decides; the [primary] reader's text is the one kept.
 */
class OcrGate(private val primary: OcrReader, private val checker: OcrReader? = null) : OcrReader {
    override fun read(image: OcrImage): OcrPage? {
        val page = primary.read(image)?.takeIf { it.lines.isNotEmpty() }
        val trusted = when {
            page == null -> false
            checker == null -> page.confidence >= MIN_CONFIDENCE
            else -> checker.read(image)?.let { agree(page, it) >= MIN_AGREEMENT } == true
        }
        return page.takeIf { trusted }
    }

    companion object {
        const val MIN_CONFIDENCE = 0.85f
        const val MIN_AGREEMENT = 0.40f

        /** The share of words the two readings have in common (as multisets), over the longer reading. */
        fun agree(a: OcrPage, b: OcrPage): Float {
            val wa = words(a)
            val wb = words(b).groupingBy { it }.eachCount().toMutableMap()
            var common = 0
            for (w in wa) {
                val n = wb[w] ?: 0
                if (n > 0) {
                    common++
                    wb[w] = n - 1
                }
            }
            val longer = maxOf(wa.size, words(b).size)
            return if (longer == 0) 0f else common.toFloat() / longer
        }

        private fun words(page: OcrPage): List<String> =
            page.lines.flatMap { WORD.findAll(normalise(it.text)).map { m -> m.value }.toList() }

        /** Arabic vowel marks and tatweel go, and alef, ya and ta-marbuta forms unify: the engines differ on them. */
        private fun normalise(text: String): String = text.lowercase()
            .replace(MARKS, "")
            .replace(ALEFS, "ا")
            .replace('ى', 'ي')
            .replace('ة', 'ه')

        private val WORD = Regex("[\\p{L}\\p{N}]+")
        private val MARKS = Regex("[\\u064B-\\u065F\\u0670\\u0640]")
        private val ALEFS = Regex("[إأآٱ]")
    }
}
