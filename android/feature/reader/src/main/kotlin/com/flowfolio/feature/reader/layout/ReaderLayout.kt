package com.flowfolio.feature.reader.layout

/** How pages are arranged on screen. */
enum class ReaderLayout { SINGLE, SPREAD }

/**
 * Product rule (owner, 2026-10-16): single page on the folded cover screen and phones, two-page spread when unfolded.
 * Decided from the window, not the device: a spread when the window is at least [MIN_SPREAD_WIDTH_DP] wide and close
 * to square or wider. The Fold's inner screen (~752 x 835 dp) qualifies; its cover screen and phones in portrait
 * don't; tablets in landscape do.
 */
fun chooseLayout(widthDp: Float, heightDp: Float): ReaderLayout = if (widthDp >= MIN_SPREAD_WIDTH_DP &&
    widthDp / heightDp >= MIN_SPREAD_ASPECT
) {
    ReaderLayout.SPREAD
} else {
    ReaderLayout.SINGLE
}

private const val MIN_SPREAD_WIDTH_DP = 600f
private const val MIN_SPREAD_ASPECT = 0.8f

/**
 * Spread pairing. With [coverAlone] (print style, the default) the cover (page 0) stands alone on the right, then
 * 1-2, 3-4, ... Without it pages pair from the start: 0-1, 2-3, ... (some PDFs are laid out that way). A unit is one
 * spread; a page index of -1 means an empty half. Owner setting since milestone 4.
 */
object Spreads {
    private fun shift(coverAlone: Boolean) = if (coverAlone) 1 else 0

    fun unitCount(pageCount: Int, coverAlone: Boolean = true): Int =
        if (pageCount <= 0) 0 else (pageCount - 1 + shift(coverAlone)) / 2 + 1

    fun unitOf(page: Int, coverAlone: Boolean = true): Int = (page + shift(coverAlone)) / 2

    fun leftOf(unit: Int, pageCount: Int, coverAlone: Boolean = true): Int =
        (2 * unit - shift(coverAlone)).takeIf { it in 0 until pageCount } ?: -1

    fun rightOf(unit: Int, pageCount: Int, coverAlone: Boolean = true): Int =
        (2 * unit - shift(coverAlone) + 1).takeIf { it in 0 until pageCount } ?: -1

    fun pagesOf(unit: Int, pageCount: Int, coverAlone: Boolean = true): List<Int> =
        listOf(leftOf(unit, pageCount, coverAlone), rightOf(unit, pageCount, coverAlone)).filter { it >= 0 }

    /** The page that represents a unit when switching back to single-page layout: its first visible page. */
    fun anchorOf(unit: Int, pageCount: Int, coverAlone: Boolean = true): Int =
        pagesOf(unit, pageCount, coverAlone).firstOrNull() ?: 0
}
