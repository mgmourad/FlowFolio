package com.flowfolio.core.conversion.ocr

/**
 * Whether a page read by OCR becomes text or stays a picture (ADR-0054, its measure revised by ADR-0055). Nothing
 * invented is better than nothing read.
 * - One reader (Latin script, PaddleOCR): its own mean line confidence must reach [MIN_CONFIDENCE].
 * - Two readers (Arabic, Tesseract checked by PaddleOCR): the two readings must [agree] at least [MIN_AGREEMENT]. Each
 *   engine's confidence is on its own scale, and Tesseract was 95% sure of a manuscript it misread, so neither engine's
 *   own number decides; the [primary] reader's text is the one kept.
 */
class OcrGate(private val primary: OcrReader, private val checker: OcrReader? = null) : OcrReader {
    override fun read(image: OcrImage): OcrPage? {
        val page = primary.read(image)?.takeIf { it.lines.isNotEmpty() } ?: return null
        val trusted = if (checker == null) page.confidence >= MIN_CONFIDENCE else trusts(page, checker.read(image))
        return page.takeIf { trusted }
    }

    companion object {
        const val MIN_CONFIDENCE = 0.85f

        /**
         * The agreement two readings need (ADR-0055). On the M3 corpus every page Tesseract read well (word recall
         * 0.74 or more) agreed 0.87 or more, and every page it misread, the manuscripts among them, 0.62 or less; the
         * owner's 1960s Egyptian book agreed 0.71 to 0.88 on its body pages. The bar sits between the two groups.
         */
        const val MIN_AGREEMENT = 0.66f

        /** [page] is backed by [check], a second engine's reading of the same picture. */
        fun trusts(page: OcrPage, check: OcrPage?): Boolean =
            page.lines.isNotEmpty() && check != null && agree(page, check) >= MIN_AGREEMENT

        /**
         * How alike two readings are: the Dice overlap of their letter pairs (as multisets), after Arabic vowel marks
         * and letter forms are evened out. Letter pairs give partial credit for a word one engine got nearly right;
         * whole-word matching threw away good Tesseract pages whenever PaddleOCR garbled a letter in most words
         * (ADR-0055). Engines reading unrelated nonsense still share few pairs.
         */
        fun agree(a: OcrPage, b: OcrPage): Float {
            val x = pairs(a)
            val y = pairs(b)
            val common = x.entries.sumOf { (k, n) -> minOf(n, y[k] ?: 0) }
            val total = x.values.sum() + y.values.sum()
            return if (total == 0) 0f else 2f * common / total
        }

        /** Letter pairs within words: no pair spans a space, so word order and spacing do not count. */
        private fun pairs(page: OcrPage): Map<String, Int> {
            val counts = HashMap<String, Int>()
            for (line in page.lines) {
                for (word in WORD.findAll(normalise(line.text))) {
                    word.value.windowed(2).forEach { counts[it] = (counts[it] ?: 0) + 1 }
                }
            }
            return counts
        }

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
