package com.flowfolio.feature.reader.riffle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The leaf springs (spec 5.2) and the strip's frame loop. */
class LeafPhysicsTest {
    private fun run(hz: Int, seconds: Float): LeafPhysics {
        val leaves = LeafPhysics(1)
        leaves.reset(1, floatArrayOf(0f))
        val target = floatArrayOf(100f)
        repeat((hz * seconds).toInt()) { leaves.step(1f / hz, target, 14f) }
        return leaves
    }

    @Test
    fun leavesSettleOnTheirTargetAtAnyFrameRate() {
        val at60 = run(60, 0.15f)
        val at120 = run(120, 0.15f)
        // Mid-flight the two frame rates agree closely: the motion does not depend on the frame rate.
        assertEquals(at60.x[0], at120.x[0], 3f)
        assertEquals(100f, run(120, 2f).x[0], 0.05f)
        assertFalse(run(120, 3f).step(1f / 120, floatArrayOf(100f), 14f))
    }

    @Test
    fun aLeafBowsInItsDirectionOfTravelUpToTheLimit() {
        val leaves = LeafPhysics(1)
        leaves.reset(1, floatArrayOf(0f))
        assertTrue(leaves.step(1f / 120, floatArrayOf(1000f), 14f))
        assertTrue(leaves.bow[0] > 0f)
        repeat(5) { leaves.step(1f / 120, floatArrayOf(1000f), 14f) }
        assertEquals(14f, leaves.bow[0], 0f) // clamped
        leaves.step(1f / 120, floatArrayOf(-1000f), 14f)
        // A few frames later it travels the other way (the spring overshoots later, so look early).
        repeat(3) { leaves.step(1f / 120, floatArrayOf(-1000f), 14f) }
        assertTrue(leaves.bow[0] < 0f)
    }

    @Test
    fun theStripFollowsTheRibbonAndThenStops() {
        val state = RiffleScrubberState(SampleBook(), current = 100)
        state.layout(width = 2000f, height = 300f, density = 2.5f)
        var now = 0L
        fun frames(n: Int): Boolean {
            var running = false
            repeat(n) {
                now += 8_333_333L
                running = state.advance(now)
            }
            return running
        }
        val g = requireNotNull(state.geometry)
        val x = state.mapping.xOf(300, g.x0, g.x1)
        state.riffleX = x
        assertTrue(frames(120))
        assertEquals(x, state.pinX, 0.5f)
        assertEquals(300, state.previewUnit)
        assertTrue(state.live)
        assertTrue(state.engagement > 0.95f)
        // Let go without landing: the pin glides home, the gap closes, and once the sway fades the loop stops.
        state.riffleX = null
        frames(240)
        assertEquals(100, state.previewUnit)
        assertFalse(state.live)
        assertFalse(frames(600))
        assertEquals(0f, state.swayAmount, 0f)
    }
}
