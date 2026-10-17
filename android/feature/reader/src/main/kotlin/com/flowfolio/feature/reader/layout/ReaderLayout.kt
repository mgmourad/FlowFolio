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
 * Print-style spread pairing: the cover (page 0) stands alone on the right, then 1-2, 3-4, ... Unit n shows pages
 * 2n-1 (left) and 2n (right). A page index of -1 means an empty half.
 */
object Spreads {
    fun unitCount(pageCount: Int): Int = if (pageCount <= 0) 0 else pageCount / 2 + 1

    fun unitOf(page: Int): Int = (page + 1) / 2

    fun leftOf(unit: Int, pageCount: Int): Int = (2 * unit - 1).takeIf { unit > 0 && it < pageCount } ?: -1

    fun rightOf(unit: Int, pageCount: Int): Int = (2 * unit).takeIf { it < pageCount } ?: -1

    fun pagesOf(unit: Int, pageCount: Int): List<Int> {
        val pages = listOf(leftOf(unit, pageCount), rightOf(unit, pageCount))
        return pages.filter { it >= 0 }
    }

    /** The page that represents a unit when switching back to single-page layout: its first visible page. */
    fun anchorOf(unit: Int, pageCount: Int): Int = pagesOf(unit, pageCount).firstOrNull() ?: 0
}
