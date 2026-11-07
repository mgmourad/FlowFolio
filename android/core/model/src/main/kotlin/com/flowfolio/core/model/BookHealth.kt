package com.flowfolio.core.model

/**
 * How well a book's Dynamic Flow conversion went, from its sidecar's health (`docs/conversion-spec.md` §3). Read from
 * `flowfolio.json` beside `book.epub`; nothing in the converter produces it before Phase 5 M2 surfaces it.
 */
enum class HealthGrade { GOOD, FAIR, POOR }

/** The numbers a reader needs to judge a reflowed textbook at a glance (M2). */
data class BookHealth(
    val pages: Int,
    /** Pages whose text coverage fell below the threshold and went out as an image. */
    val pagesBelowThreshold: Int,
    /** Pages that went out as a whole-page image for any reason (scanned, cover, or low coverage). */
    val fallbackPages: Int,
    val scannedPages: Int,
    /** Mean share of a page's text found in its blocks, 0 to 1. */
    val textCoverage: Float,
    /** Mean share of text whose glyphs agree with their declared size (Phase 4), 0 to 1. */
    val geometricCoverage: Float
) {
    /** Good when nothing fell back; fair when a few pages did; poor when many did (M2). */
    val grade: HealthGrade
        get() = when {
            fallbackPages == 0 -> HealthGrade.GOOD
            fallbackPages <= maxOf(MIN_FAIR_PAGES, pages / FAIR_SHARE) -> HealthGrade.FAIR
            else -> HealthGrade.POOR
        }

    private companion object {
        /** Up to ~2% of pages falling back reads as "a few"; anything above is "many". */
        const val FAIR_SHARE = 50
        const val MIN_FAIR_PAGES = 2
    }
}
