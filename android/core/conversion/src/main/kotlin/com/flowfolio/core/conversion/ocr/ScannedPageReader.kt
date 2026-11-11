package com.flowfolio.core.conversion.ocr

/**
 * The converter's reader for scanned pages (ADR-0054): it finds the page's script, then reads it the agreed way.
 * - Latin script: PaddleOCR, kept when its confidence reaches the gate.
 * - Arabic: Tesseract's text, kept when Tesseract and PaddleOCR agree ([OcrGate]). Without a Tesseract reader (not
 *   yet on the phone), Arabic pages stay pictures: the Latin model can be 99.8% sure of nonsense on Arabic print, so
 *   it must never read them.
 *
 * Each book is read through [forBook], which votes on the book's script as its pages go by. Once a book is plainly
 * Arabic and there is no Arabic reader, its remaining pages are left as pictures straight away rather than detected
 * and recognised for nothing: on the owner's 663-page Arabic book that work was most of fifty minutes (2026-11-11).
 */
class ScannedPageReader(
    private val latin: PaddleOcr,
    private val arabic: PaddleOcr? = null,
    private val tesseractArabic: OcrReader? = null
) : OcrReader {
    override fun read(image: OcrImage): OcrPage? = readAs(image).second

    override fun forBook(): OcrReader = Book()

    /** The page's script (null when there was nothing to tell it by) and what was kept of it. */
    internal fun readAs(image: OcrImage): Pair<Script?, OcrPage?> {
        // One detection serves the script check and the reading: the two recognisers share PaddleOCR's detector.
        val rects = latin.detect(image)
        if (rects.isEmpty()) return null to null
        val script = arabic?.let { Script.of(image, rects, latin, it) } ?: Script.LATIN
        return script to when (script) {
            Script.LATIN -> latin.read(image, rects).takeIf { it.confidence >= OcrGate.MIN_CONFIDENCE }
            Script.ARABIC -> tesseractArabic?.let { OcrGate(it, arabic).read(image) }
        }
    }

    /** One book's pages: [BookScript] settles the book's script, and an Arabic book without a reader skips OCR. */
    private inner class Book : OcrReader {
        private val votes = BookScript()

        override fun read(image: OcrImage): OcrPage? {
            if (votes.settled == Script.ARABIC && tesseractArabic == null) return null
            val (script, page) = readAs(image)
            script?.let(votes::add)
            return page
        }
    }
}

/**
 * The script of a book, settled by its scanned pages' votes: [SETTLE_AFTER] pages with a script, and at least
 * [SETTLE_SHARE] of them agreeing. A book mixing the two (an Arabic book quoting an English newspaper, as the
 * owner's does on one page) still settles on its main script; only an even mix stays unsettled.
 */
class BookScript {
    private var arabic = 0
    private var latin = 0

    var settled: Script? = null
        private set

    fun add(script: Script) {
        if (settled != null) return
        if (script == Script.ARABIC) arabic++ else latin++
        val total = arabic + latin
        if (total < SETTLE_AFTER) return
        settled = when {
            arabic >= total * SETTLE_SHARE -> Script.ARABIC
            latin >= total * SETTLE_SHARE -> Script.LATIN
            else -> null
        }
    }

    companion object {
        const val SETTLE_AFTER = 6
        const val SETTLE_SHARE = 0.8f
    }
}
