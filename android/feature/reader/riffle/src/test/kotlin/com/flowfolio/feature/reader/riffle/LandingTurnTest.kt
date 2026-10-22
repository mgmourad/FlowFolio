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

/** The landing turn's timing (spec 5.4, 6) on a hand-driven clock, and the arrows at the strip's ends (spec 3.2). */
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
    fun theArrowsAtTheStripEndsStepOneUnit() {
        val state = RiffleScrubberState(SampleBook(), 100)
        var went: Int? = null
        val events = object : RiffleEvents {
            override fun onGoTo(unit: Int) {
                went = unit
            }
        }
        compose.setContent {
            FlowFolioTheme(ThemePreference.DARK) {
                RiffleDock(state, BoardColors(Color.DarkGray, Color.Black), Modifier.fillMaxWidth(), events)
            }
        }
        // Always there, with no riffle first.
        compose.onNodeWithContentDescription("Next page").performClick()
        assertEquals(101, went)
        compose.onNodeWithContentDescription("Previous page").performClick()
        assertEquals(99, went)
    }
}
