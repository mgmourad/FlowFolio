package com.flowfolio.core.data.flow

import com.flowfolio.core.model.FlowStatus

/**
 * Notices a conversion that will never report back: Android stopped the conversion process (`:convert`) mid-book, for
 * memory most often, and no "done" broadcast is coming. Without this the app shows "Preparing" for good, and asking
 * again does nothing because the book still reads as converting.
 *
 * Silence alone proves nothing: a queued book hears nothing until its turn, and an app frozen in the background may
 * wake before its broadcasts arrive. So a book is lost only when it has been silent for [STALE_MS] *and* the conversion
 * process is not running.
 */
internal object FlowWatch {
    /** How long a book may go unheard before the process is checked. */
    const val STALE_MS = 15_000L

    /** How often the app looks while a book is converting or queued. */
    const val CHECK_MS = 5_000L

    /** Lost books convert again by themselves this many times per run; after that they are reported as stopped. */
    private const val AUTO_RESTARTS = 1

    /** The books in [statuses] that are converting or queued, silent since [lastHeard] (0 when never), and orphaned. */
    fun lost(
        statuses: Map<String, FlowStatus>,
        lastHeard: Map<String, Long>,
        now: Long,
        converterAlive: Boolean
    ): Set<String> {
        if (converterAlive) return emptySet()
        return statuses
            .filter { (id, status) -> status.waiting() && now - (lastHeard[id] ?: 0L) > STALE_MS }
            .keys
    }

    /** Whether a book lost [previous] times already should simply convert again. */
    fun restarts(previous: Int): Boolean = previous < AUTO_RESTARTS

    fun FlowStatus.waiting(): Boolean = this is FlowStatus.Converting || this == FlowStatus.Queued
}
