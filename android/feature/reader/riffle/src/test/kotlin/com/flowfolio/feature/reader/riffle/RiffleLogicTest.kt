package com.flowfolio.feature.reader.riffle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fine scrubbing, flags, the riffle state machine, landing and feedback (spec 8). */
class RiffleLogicTest {
    private val density = 2.5f

    @Test
    fun speedFollowsTheFingersHeightAboveTheGrabPoint() {
        assertEquals(ScrubSpeed.NORMAL, ScrubSpeed.forLift(-20f))
        assertEquals(ScrubSpeed.NORMAL, ScrubSpeed.forLift(55.9f))
        assertEquals(ScrubSpeed.HALF, ScrubSpeed.forLift(56f))
        assertEquals(ScrubSpeed.QUARTER, ScrubSpeed.forLift(112f))
        assertEquals(ScrubSpeed.FINE, ScrubSpeed.forLift(168f))
        assertEquals(ScrubSpeed.FINE, ScrubSpeed.forLift(900f))
    }

    @Test
    fun fullSpeedKeepsTheGrabOffset() {
        // Grabbed 6 px right of the ribbon.
        val drag = ScrubDrag(downX = 506f, downY = 1000f, ribbonX = 500f, minX = 0f, maxX = 1000f, density = density)
        assertFalse(drag.move(606f, 1000f))
        assertEquals(600f, drag.ribbonX, 0f)
    }

    @Test
    fun slowerSpeedsMoveRelativelyAndNeverJump() {
        val drag = ScrubDrag(500f, 1000f, 500f, 0f, 1000f, density)
        // Up 60 dp (half speed) without moving sideways: the ribbon stays put, and the speed change is reported.
        assertTrue(drag.move(500f, 1000f - 60 * density))
        assertEquals(500f, drag.ribbonX, 0f)
        assertEquals(ScrubSpeed.HALF, drag.speed)
        drag.move(600f, 1000f - 60 * density)
        assertEquals(550f, drag.ribbonX, 1e-3f)
        // Fine: a tenth.
        drag.move(600f, 1000f - 200 * density)
        drag.move(700f, 1000f - 200 * density)
        assertEquals(560f, drag.ribbonX, 1e-3f)
        // Back down to full speed: still relative, so no jump to the finger at 700.
        drag.move(700f, 1000f)
        assertEquals(560f, drag.ribbonX, 1e-3f)
        drag.move(710f, 1000f)
        assertEquals(570f, drag.ribbonX, 1e-3f)
    }

    @Test
    fun theRibbonStaysOnTheBlock() {
        val drag = ScrubDrag(500f, 1000f, 500f, 26f, 974f, density)
        drag.move(-400f, 1000f)
        assertEquals(26f, drag.ribbonX, 0f)
    }

    @Test
    fun fineScrubbingReachesEverySpreadOfTheSampleBook() {
        // An 800 dp strip: 422 spreads, about 1.9 dp each; at a tenth of the speed each is 19 dp of finger travel.
        val m = StripMapping(SampleBook())
        val width = 800 * density
        val drag = ScrubDrag(width / 2, 2000f, width / 2, 0f, width, density)
        drag.move(width / 2, 2000f - 200 * density)
        val seen = HashSet<Int>()
        var x = width / 2
        while (drag.ribbonX < width) {
            x += 2 * density // 2 dp finger steps
            drag.move(x, 2000f - 200 * density)
            seen += m.unitAtX(drag.ribbonX, 0f, width)
        }
        val from = m.unitAtX(width / 2, 0f, width)
        assertEquals((from + 1 until m.unitCount).toSet(), seen - from)
    }

    @Test
    fun closeFlagsStackFromTheFirstOne() {
        val f = (0..5).map { RiffleFlag(it.toLong(), it * 10, color = it) }
        val merge = FlagStacking.MERGE_DP * density // 35 px
        val stacks = FlagStacking.stack(
            listOf(f[0] to 100f, f[1] to 120f, f[2] to 135f, f[3] to 136f, f[4] to 400f, f[5] to 434f),
            merge
        )
        assertEquals(listOf(3, 1, 2), stacks.map { it.count })
        assertEquals(0, stacks[0].color) // the first flag's colour
        assertEquals(136f, stacks[1].x, 0f)
    }

    @Test
    fun flagsAreHitOnlyAboveTheBlock() {
        val stacks = FlagStacking.stack(listOf(RiffleFlag(1, 10, 0) to 300f, RiffleFlag(2, 90, 0) to 360f), 35f)
        val below = FlagStacking.HIT_BELOW_TOP_DP * density
        val area = FlagHitArea(blockTop = 60f, FlagStacking.HIT_HALF_WIDTH_DP * density, below)
        assertEquals(1L, FlagStacking.stackAt(stacks, 310f, 50f, area)?.flags?.first()?.id)
        assertEquals(2L, FlagStacking.stackAt(stacks, 345f, 50f, area)?.flags?.first()?.id)
        assertNull(FlagStacking.stackAt(stacks, 310f, 60f + below + 1, area))
        assertNull(FlagStacking.stackAt(stacks, 200f, 50f, area))
    }

    @Test
    fun aTapThatMovesTenDpIsCancelled() {
        val slop = TapTracker.SLOP_DP * density
        val tap = TapTracker(100f, 100f, slop)
        tap.move(100f + slop - 1, 100f)
        assertTrue(tap.up())
        val drag = TapTracker(100f, 100f, slop)
        drag.move(100f + slop + 1, 100f)
        drag.move(100f, 100f) // coming back does not undo it
        assertFalse(drag.up())
    }

    @Test
    fun riffleStatesFollowTheGestures() {
        var s: RiffleState = RiffleState.Idle
        s = RiffleMachine.grab(s)
        assertEquals(RiffleState.Dragging(ScrubSpeed.NORMAL), s)
        s = RiffleMachine.speed(s, ScrubSpeed.FINE)
        // A flag cannot be tapped while the ribbon is held.
        assertEquals(s, RiffleMachine.flagTapped(s, 40, 10))
        s = RiffleMachine.release(s, target = 40, current = 10)
        assertEquals(RiffleState.Landing(40), s)
        // Taking the ribbon again interrupts the landing.
        assertEquals(RiffleState.Dragging(ScrubSpeed.NORMAL), RiffleMachine.grab(s))
        assertEquals(RiffleState.Idle, RiffleMachine.landed(s))
        assertEquals(RiffleState.Travelling(90), RiffleMachine.flagTapped(RiffleState.Idle, 90, 10))
        // Letting go where it started lands nowhere.
        assertEquals(RiffleState.Idle, RiffleMachine.release(RiffleMachine.grab(RiffleState.Idle), 10, 10))
    }

    @Test
    fun landingTurnsAtMostSevenSheetsSampledEvenly() {
        val far = LandingPlan.between(10, 80)
        assertEquals(listOf(20, 30, 40, 50, 60, 70, 80), far.steps)
        assertEquals(520, far.sheetMs)
        assertEquals(6 * 70 + 520, far.totalMs)
        assertEquals(listOf(9, 8, 7), LandingPlan.between(10, 7).steps)
        val one = LandingPlan.between(10, 11)
        assertEquals(listOf(11), one.steps)
        assertEquals(700, one.totalMs)
        assertTrue(LandingPlan.between(5, 5).steps.isEmpty())
    }

    @Test
    fun loupeFlipsAreQuickerForFasterRiffles() {
        assertEquals(110L, LoupeFlips.durationMs(10))
        assertEquals(240L, LoupeFlips.durationMs(100))
        assertEquals(320L, LoupeFlips.durationMs(5000))
    }

    @Test
    fun feedbackRanksChaptersOverFlagsOverTicks() {
        val rules = FeedbackRules(chapterStarts = listOf(19, 40), flagUnits = listOf(25))
        assertEquals(RiffleHaptic.CHAPTER, rules.between(18, 19).haptic)
        assertEquals(RiffleHaptic.CHAPTER, rules.between(19, 18).haptic) // backward across the start too
        assertTrue(rules.between(18, 19).thump)
        assertEquals(RiffleHaptic.FLAG, rules.between(24, 26).haptic)
        assertEquals(RiffleHaptic.CHAPTER, rules.between(10, 50).haptic)
        assertEquals(RiffleHaptic.TICK, rules.between(11, 12).haptic) // 11 -> 12 crosses a multiple of 4
        assertNull(rules.between(12, 13).haptic)
        assertEquals(FeedbackRules.MAX_FLICKS, rules.between(0, 100).flicks)
        assertEquals(0, rules.between(7, 7).flicks)
    }

    @Test
    fun bleedBandsStepDownAndRepeat() {
        val book = SampleBook()
        val bands = BleedBands.of(book.sections, StripMapping(book))
        assertEquals(19, bands.size)
        assertEquals((0 until 19).map { it % 8 }, bands.map { it.band })
        assertEquals(0, bands.first().firstSheet)
        assertEquals(421, bands.last().endSheet)
        // Each section's sheets follow the previous one's.
        bands.zipWithNext().forEach { (a, b) -> assertEquals(a.endSheet, b.firstSheet) }
    }
}
