package com.flowfolio.feature.reader

import com.flowfolio.feature.reader.SessionTracker.Companion.IDLE_MS
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTrackerTest {
    private val saved = mutableListOf<Pair<Long, Long>>()
    private val tracker = SessionTracker { a, b -> saved += a to b }

    @Test
    fun aSessionRunsFromResumeToPause() {
        tracker.resume(0)
        tracker.activity(60_000)
        tracker.pause(90_000)
        assertEquals(listOf(0L to 90_000L), saved)
    }

    @Test
    fun aLongBreakSplitsTheSessionWhereUseStopped() {
        tracker.resume(0)
        tracker.activity(60_000)
        tracker.activity(60_000 + IDLE_MS + 1) // came back after a break
        tracker.pause(60_000 + IDLE_MS + 30_001)
        assertEquals(listOf(0L to 60_000 + IDLE_MS, 60_000 + IDLE_MS + 1 to 60_000 + IDLE_MS + 30_001), saved)
    }

    @Test
    fun leavingTheBookOpenCountsOnlyUpToTheIdleLimit() {
        tracker.resume(0)
        tracker.pause(3 * IDLE_MS)
        assertEquals(listOf(0L to IDLE_MS), saved)
    }

    @Test
    fun aGlanceIsNotKept() {
        tracker.resume(0)
        tracker.pause(5_000)
        assertEquals(emptyList<Pair<Long, Long>>(), saved)
    }
}
