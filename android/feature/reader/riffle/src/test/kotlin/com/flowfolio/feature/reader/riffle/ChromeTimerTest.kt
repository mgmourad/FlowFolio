package com.flowfolio.feature.reader.riffle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Auto-hide rules (spec 3.4) on a virtual clock. */
class ChromeTimerTest {
    @Test
    fun hidesAfterTheChosenDelay() {
        val t = ChromeTimer(AutoHide.S5, now = 0)
        assertFalse(t.tick(4_999))
        assertEquals(0.5f, t.remaining(2_500), 1e-6f)
        assertTrue(t.tick(5_000))
        assertEquals(ChromeState.HIDDEN, t.state)
        assertFalse(t.tick(6_000)) // only once
    }

    @Test
    fun interactionRestartsTheCountdownButPageTurnsDoNot() {
        val t = ChromeTimer(AutoHide.S5, now = 0)
        t.interacted(3_000)
        assertFalse(t.tick(7_999))
        t.pageTurned()
        assertTrue(t.tick(8_000))
    }

    @Test
    fun holdsPauseItAndItRestartsInFullAfterTheLastOne() {
        val t = ChromeTimer(AutoHide.S3, now = 0)
        t.hold(ChromePause.DRAGGING)
        t.hold(ChromePause.NUDGE)
        assertFalse(t.tick(60_000))
        assertEquals(1f, t.remaining(60_000), 0f)
        t.release(ChromePause.DRAGGING, 60_000)
        assertFalse(t.tick(90_000)) // the nudge arrows still hold it
        t.release(ChromePause.NUDGE, 90_000)
        assertFalse(t.tick(92_999))
        assertTrue(t.tick(93_000))
    }

    @Test
    fun neverMeansNever() {
        val t = ChromeTimer(AutoHide.NEVER, now = 0)
        assertFalse(t.tick(Long.MAX_VALUE / 2))
        assertEquals(1f, t.remaining(1_000_000), 0f)
    }

    @Test
    fun theMiddleOfThePageToggles() {
        val t = ChromeTimer(AutoHide.S8, now = 0)
        t.hold(ChromePause.POPOVER)
        t.toggle(1_000)
        assertEquals(ChromeState.HIDDEN, t.state)
        // Hiding closed the popover, so the timer runs again once shown.
        t.toggle(2_000)
        assertEquals(ChromeState.VISIBLE, t.state)
        assertTrue(t.tick(10_000))
    }

    @Test
    fun changingTheDelayShowsTheChromeAndStartsAgain() {
        val t = ChromeTimer(AutoHide.S3, now = 0)
        t.tick(3_000)
        t.setAutoHide(AutoHide.S15, 4_000)
        assertEquals(ChromeState.VISIBLE, t.state)
        assertFalse(t.tick(18_999))
        assertTrue(t.tick(19_000))
    }
}
