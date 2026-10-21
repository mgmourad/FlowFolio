package com.flowfolio.feature.reader.riffle

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.core.designsystem.FlowFolioTheme
import com.flowfolio.core.model.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The landing turn's timing (spec 5.4, 6) and the nudge arrows (spec 3.2), on a hand-driven clock. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w840dp-h500dp-420dpi")
class LandingTurnTest {
    @get:Rule
    val compose = createComposeRule()

    private fun land(from: Int, to: Int, reduced: Boolean): () -> Boolean {
        var done = false
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(400.dp)) {
                LandingTurn(SampleBook(), from, to, spread = true, reducedMotion = reduced, page = { _, m -> Box(m) }) {
                    done = true
                }
            }
        }
        return { done }
    }

    @Test
    fun aLongJumpTurnsSevenStaggeredSheets() {
        val done = land(100, 300, reduced = false)
        // 6 x 70 ms stagger + 520 ms for the last sheet.
        compose.mainClock.advanceTimeBy(900)
        assertFalse(done())
        compose.mainClock.advanceTimeBy(100)
        assertTrue(done())
    }

    @Test
    fun oneStepIsASingle700msTurn() {
        val done = land(100, 101, reduced = false)
        compose.mainClock.advanceTimeBy(650)
        assertFalse(done())
        compose.mainClock.advanceTimeBy(100)
        assertTrue(done())
    }

    @Test
    fun reducedMotionCrossfadesQuickly() {
        val done = land(100, 300, reduced = true)
        compose.mainClock.advanceTimeBy(300)
        assertTrue(done())
    }

    @Test
    fun theNudgeArrowsStepOneUnitAndHoldTheChrome() {
        val book = SampleBook()
        val state = RiffleScrubberState(book, 100)
        val pauses = ArrayList<Pair<ChromePause, Boolean>>()
        var went: Int? = null
        val events = object : RiffleEvents {
            override fun onGoTo(unit: Int) {
                went = unit
            }

            override fun onPause(reason: ChromePause, held: Boolean) {
                pauses += reason to held
            }
        }
        compose.setContent {
            FlowFolioTheme(ThemePreference.DARK) {
                RiffleDock(state, BoardColors(Color.DarkGray, Color.Black), Modifier.fillMaxWidth(), events)
            }
        }
        compose.waitForIdle()
        // The arrows wait three seconds: drive the clock by hand so waiting for idle does not skip them.
        compose.mainClock.autoAdvance = false
        // A riffle that lets go shows the arrows.
        val g = requireNotNull(state.geometry)
        compose.onRoot().performTouchInput {
            val origin = androidx.compose.ui.geometry.Offset(24.dp.toPx(), (26 + 2 + 4).dp.toPx())
            down(origin + androidx.compose.ui.geometry.Offset(state.pinX, g.bottom + 30 * g.density))
            up()
        }
        step()
        assertEquals("the riffle let go", 1, state.nudges)
        assertTrue("$pauses", ChromePause.NUDGE to true in pauses)
        compose.onNodeWithContentDescription("Next page").performClick()
        step()
        assertEquals(101, went)
        assertTrue(ChromePause.NUDGE to true in pauses)
        // Three seconds after the last tap they go, and the chrome's countdown may run again.
        compose.mainClock.advanceTimeBy(3_100)
        step()
        assertTrue("$pauses", ChromePause.NUDGE to false in pauses)
    }

    /** A frame, then the state changes it caused settled into composition (the clock does not move on its own). */
    private fun step() {
        compose.mainClock.advanceTimeBy(FRAME_MS)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(FRAME_MS)
    }

    private companion object {
        const val FRAME_MS = 100L
    }
}
