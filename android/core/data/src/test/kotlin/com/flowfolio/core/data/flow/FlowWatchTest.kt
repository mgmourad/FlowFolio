package com.flowfolio.core.data.flow

import com.flowfolio.core.model.FlowStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A conversion is only given up on when the app has heard nothing about it for a while *and* the conversion process is
 * gone: silence alone is normal for a book waiting its turn, and for an app that was frozen in the background.
 */
class FlowWatchTest {
    private val converting = FlowStatus.Converting(0.07f)

    @Test
    fun aSilentBookWhoseProcessDiedIsLost() {
        val lost = FlowWatch.lost(mapOf("a" to converting), mapOf("a" to 0L), now = 30_000L, converterAlive = false)
        assertEquals(setOf("a"), lost)
    }

    @Test
    fun silenceIsNotEnoughWhileTheProcessLives() {
        val lost = FlowWatch.lost(mapOf("a" to converting), mapOf("a" to 0L), now = 600_000L, converterAlive = true)
        assertTrue(lost.isEmpty())
    }

    @Test
    fun aBookHeardFromRecentlyIsNotLost() {
        val lost = FlowWatch.lost(
            mapOf("a" to converting),
            mapOf("a" to 25_000L),
            now = 30_000L,
            converterAlive = false
        )
        assertTrue(lost.isEmpty())
    }

    @Test
    fun queuedBooksGoWithTheProcessToo() {
        val statuses = mapOf("a" to converting, "b" to FlowStatus.Queued)
        val lost = FlowWatch.lost(statuses, mapOf("a" to 0L, "b" to 0L), now = 30_000L, converterAlive = false)
        assertEquals(setOf("a", "b"), lost)
    }

    @Test
    fun finishedBooksAreNeverLost() {
        val statuses = mapOf("a" to FlowStatus.Ready, "b" to FlowStatus.None)
        val lost = FlowWatch.lost(statuses, emptyMap(), now = 30_000L, converterAlive = false)
        assertTrue(lost.isEmpty())
    }

    @Test
    fun aBookNeverHeardFromCountsFromTheStart() {
        val lost = FlowWatch.lost(mapOf("a" to converting), emptyMap(), now = 30_000L, converterAlive = false)
        assertEquals(setOf("a"), lost)
    }

    @Test
    fun onlyTheFirstLossRestartsByItself() {
        assertTrue(FlowWatch.restarts(previous = 0))
        assertTrue(!FlowWatch.restarts(previous = 1))
    }
}
