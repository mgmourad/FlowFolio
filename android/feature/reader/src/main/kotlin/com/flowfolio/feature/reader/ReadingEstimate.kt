package com.flowfolio.feature.reader

import androidx.compose.runtime.compositionLocalOf

/**
 * How long the rest of the book will take (ADR-0052, the title pill's "27 h left"): at the reader's own pace once there
 * is enough of it (at least [MIN_TIME_MS] over [MIN_PAGES] pages), else at the mock's 1.1 minutes a page. A pace is
 * kept between [FASTEST_MS] and [SLOWEST_MS] a page, so a book left open on one page does not claim a week.
 */
internal object ReadingEstimate {
    fun millisLeft(pagesLeft: Int, pagesRead: Int, readMillis: Long): Long {
        val pace = if (readMillis >= MIN_TIME_MS && pagesRead >= MIN_PAGES) {
            (readMillis / pagesRead).coerceIn(FASTEST_MS, SLOWEST_MS)
        } else {
            DEFAULT_MS
        }
        return pagesLeft.coerceAtLeast(0) * pace
    }

    /** Whole hours from an hour on ("27 h left"), else minutes ("45 min left"), at least one. */
    fun parts(millis: Long): Pair<Long, Boolean> {
        val minutes = (millis / MINUTE).coerceAtLeast(1)
        return if (minutes >=
            MINUTES_PER_HOUR
        ) {
            (minutes + MINUTES_PER_HOUR / 2) / MINUTES_PER_HOUR to true
        } else {
            minutes to false
        }
    }

    private const val MINUTE = 60_000L
    private const val MINUTES_PER_HOUR = 60L
    const val DEFAULT_MS = 66_000L
    private const val MIN_TIME_MS = 10 * MINUTE
    private const val MIN_PAGES = 10
    private const val FASTEST_MS = 15_000L
    private const val SLOWEST_MS = 5 * MINUTE
}

/** Time read in the open book so far (ReadingTimeViewModel), for the title pill. */
internal val LocalReadMillis = compositionLocalOf { 0L }
