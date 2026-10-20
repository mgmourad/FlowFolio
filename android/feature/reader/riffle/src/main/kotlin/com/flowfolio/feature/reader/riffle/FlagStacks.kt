package com.flowfolio.feature.reader.riffle

import kotlin.math.abs
import kotlin.math.hypot

/** One flag, or several close together shown as one with a count (spec 3.3); [x] is where it is drawn. */
data class FlagStack(val flags: List<RiffleFlag>, val x: Float) {
    /** The colour of the first flag (ARGB). */
    val color: Int get() = flags.first().color
    val count: Int get() = flags.size
}

object FlagStacking {
    /** Spec 3.3: flags closer than this merge into a stack. */
    const val MERGE_DP = 14f

    /** Touch targets are at least 48 dp wide, so a flag is hit within half of that either side (spec 6). */
    const val HIT_HALF_WIDTH_DP = 24f

    /** A flag is hit only above the page block, or within this much below its top edge. */
    const val HIT_BELOW_TOP_DP = 10f

    /**
     * Groups flags placed at x positions ([placed]: flag and x) into stacks: a stack takes every following flag within
     * [mergePx] of its first one, so a long run of close flags makes several stacks rather than one wide one.
     */
    fun stack(placed: List<Pair<RiffleFlag, Float>>, mergePx: Float): List<FlagStack> {
        val out = ArrayList<FlagStack>()
        var group = ArrayList<RiffleFlag>()
        var groupX = 0f
        for ((flag, x) in placed.sortedBy { it.second }) {
            if (group.isNotEmpty() && x - groupX > mergePx) {
                out += FlagStack(group, groupX)
                group = ArrayList()
            }
            if (group.isEmpty()) groupX = x
            group += flag
        }
        if (group.isNotEmpty()) out += FlagStack(group, groupX)
        return out
    }

    /** The stack under a touch at ([x], [y]), the nearest within [area]'s half width, or null. */
    fun stackAt(stacks: List<FlagStack>, x: Float, y: Float, area: FlagHitArea) = if (y > area.lowestY) {
        null
    } else {
        stacks.filter { abs(it.x - x) <= area.halfWidthPx }.minByOrNull { abs(it.x - x) }
    }
}

/**
 * Where flags can be touched: within [halfWidthPx] either side of one, and only above [blockTop] + [belowTopPx], so a
 * touch on the page block below is never taken for a flag.
 */
data class FlagHitArea(val blockTop: Float, val halfWidthPx: Float, val belowTopPx: Float) {
    val lowestY: Float get() = blockTop + belowTopPx
}

/** A touch that is a tap only if it never moves more than [slopPx] from where it went down (spec 5.3: 10 dp). */
class TapTracker(private val downX: Float, private val downY: Float, private val slopPx: Float) {
    var cancelled = false
        private set

    fun move(x: Float, y: Float) {
        if (hypot(x - downX, y - downY) > slopPx) cancelled = true
    }

    /** The finger lifted: true when it was a tap. */
    fun up(): Boolean = !cancelled

    companion object {
        const val SLOP_DP = 10f
    }
}
