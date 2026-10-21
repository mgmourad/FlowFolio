package com.flowfolio.feature.reader.riffle

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.core.designsystem.FlowFolioTheme
import com.flowfolio.core.model.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Who owns a touch on the strip (spec 5.3), and the haptic mapping (ADR-0023). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w840dp-h320dp-420dpi")
class RiffleGesturesTest {
    @get:Rule
    val compose = createComposeRule()

    private val g = StripGeometry(width = 2000f, height = 300f, density = 2.5f)

    @Test
    fun flagsWinAboveTheBlockAndTheRibbonBelowIt() {
        val stacks = listOf(400f, 900f)
        assertEquals(StripHit.Flag(1), StripHits.at(905f, g.top - 10f, g, pinX = 900f, stackXs = stacks))
        assertEquals(StripHit.Ribbon, StripHits.at(905f, g.bottom + 40f, g, pinX = 900f, stackXs = stacks))
        // Beside the ribbon by more than 26 dp, or on the block itself: the block.
        assertEquals(StripHit.Block, StripHits.at(900f + 27 * 2.5f, g.bottom + 40f, g, 900f, stacks))
        assertEquals(StripHit.Block, StripHits.at(900f, (g.top + g.bottom) / 2, g, 900f, stacks))
    }

    @Test
    fun hapticsFallBackOnOlderSystems() {
        assertEquals(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK, RiffleHaptics.constantFor(RiffleHaptic.TICK, 34))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, RiffleHaptics.constantFor(RiffleHaptic.TICK, 33))
        assertEquals(HapticFeedbackConstants.SEGMENT_TICK, RiffleHaptics.constantFor(RiffleHaptic.FLAG, 36))
        assertEquals(HapticFeedbackConstants.CONFIRM, RiffleHaptics.constantFor(RiffleHaptic.CHAPTER, 30))
        assertEquals(HapticFeedbackConstants.LONG_PRESS, RiffleHaptics.constantFor(RiffleHaptic.CHAPTER, 29))
    }

    private class Recorder : RiffleEvents {
        var released: Int? = null
        var flag: RiffleFlag? = null

        override fun onRelease(unit: Int) {
            released = unit
        }

        override fun onFlag(flag: RiffleFlag) {
            this.flag = flag
        }
    }

    private fun show(
        events: RiffleEvents,
        flags: List<RiffleFlag> = listOf(RiffleFlag(1, 432, 0xFF00F0FF.toInt()))
    ): RiffleScrubberState {
        val book = SampleBook()
        val state = RiffleScrubberState(book, book.unitOf(215))
        state.flags = flags
        compose.setContent {
            FlowFolioTheme(ThemePreference.DARK) {
                RiffleDock(state, BoardColors(Color.DarkGray, Color.Black), Modifier.fillMaxWidth(), events)
            }
        }
        compose.waitForIdle()
        return state
    }

    /** Where the strip starts inside the dock (header, countdown line and paddings above it). */
    private fun stripOrigin(): Offset = with(compose.density) { Offset(24.dp.toPx(), (26 + 2 + 4).dp.toPx()) }

    @Test
    fun aDragThatStartsOnAFlagNeverMovesThePosition() {
        val events = Recorder()
        val state = show(events)
        val origin = stripOrigin()
        val g = requireNotNull(state.geometry)
        val flagX = state.leaves.x[state.stacks.single().leaf]
        compose.onRoot().performTouchInput {
            down(origin + Offset(flagX + 2, g.top - 8 * g.density))
            moveBy(Offset(600f, 0f))
            up()
        }
        compose.waitForIdle()
        assertNull("no riffle from a flag", events.released)
        assertNull("a drag is not a tap", events.flag)
        assertEquals(SampleBook().unitOf(215), state.currentUnit)
    }

    @Test
    fun aTapOnAFlagChoosesIt() {
        val events = Recorder()
        val state = show(events)
        val origin = stripOrigin()
        val g = requireNotNull(state.geometry)
        val flagX = state.leaves.x[state.stacks.single().leaf]
        compose.onRoot().performTouchInput { click(origin + Offset(flagX + 2, g.top - 8 * g.density)) }
        compose.waitForIdle()
        assertEquals(432, events.flag?.page)
    }

    @Test
    fun aStackOpensItsFlagsAndOneCanBeChosen() {
        val events = Recorder()
        val state = show(events, flags = listOf(300, 302, 304).mapIndexed { i, p -> RiffleFlag(i.toLong(), p, 0) })
        val origin = stripOrigin()
        val g = requireNotNull(state.geometry)
        val stack = state.stacks.single()
        assertEquals(3, stack.stack.count)
        compose.onRoot().performTouchInput {
            click(origin + Offset(state.leaves.x[stack.leaf] + 2, g.top - 8 * g.density))
        }
        compose.waitForIdle()
        assertNull("a stack opens its popover instead of jumping", events.flag)
        compose.onNodeWithText("p. 303").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(302, events.flag?.page)
        assertNull(state.openStack)
    }

    @Test
    fun aDragOnTheRibbonLandsElsewhere() {
        val events = Recorder()
        val state = show(events)
        val origin = stripOrigin()
        val g = requireNotNull(state.geometry)
        val start = SampleBook().unitOf(215)
        compose.onRoot().performTouchInput {
            down(origin + Offset(state.pinX, g.bottom + 30 * g.density))
            moveBy(Offset(400f, 0f))
            moveBy(Offset(400f, 0f))
            up()
        }
        compose.waitForIdle()
        val landed = requireNotNull(events.released)
        assertNotEquals(start, landed)
        assertTrue("moved right, toward the back", landed > start)
    }
}
