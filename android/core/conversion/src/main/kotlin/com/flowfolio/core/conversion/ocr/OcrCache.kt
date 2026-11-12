package com.flowfolio.core.conversion.ocr

/**
 * What OCR made of each scanned page, kept so a stopped conversion resumes where it was (TM1, owner 2026-11-12): a
 * long scan paused for a quicker book, or stopped by Android, reads only the pages it has not read yet.
 */
interface OcrCache {
    /** The page at [index], or null when it has not been read. */
    fun get(index: Int): OcrResult?

    fun put(index: Int, result: OcrResult)
}

/**
 * One page's OCR outcome: [page] is what was kept (null when the page stays a picture), [scale] the pixels per point
 * it was read at, and [rendered] whether there was a picture to read at all.
 */
class OcrResult(val page: OcrPage?, val scale: Float, val rendered: Boolean)
