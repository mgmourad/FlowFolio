package com.flowfolio.feature.reader.riffle

import kotlin.math.abs

/** What a touch on the strip landed on (spec 5.3): each part owns its touches. */
sealed interface StripHit {
    /** The flag stack at [index]: tap-only, never a riffle. */
    data class Flag(val index: Int) : StripHit

    /** The ribbon: the only thing that riffles. */
    data object Ribbon : StripHit

    /** Anywhere else on the strip: the page block, the boards, empty space. */
    data object Block : StripHit
}

object StripHits {
    /** The ribbon is taken within this distance either side of it (spec 5.3). */
    const val RIBBON_HALF_WIDTH_DP = 26f

    /** ... and from this far above the pin down to the ribbon's tail. */
    const val RIBBON_ABOVE_PIN_DP = 12f

    /**
     * What the touch at ([x], [y]) hits, with the block at [g], the pin at [pinX] and flag stacks at [stackXs] (their
     * x positions). Flags win above the block; the ribbon below it; everything else is the block.
     */
    fun at(x: Float, y: Float, g: StripGeometry, pinX: Float, stackXs: List<Float>): StripHit {
        val d = g.density
        val flagArea = FlagHitArea(g.top, FlagStacking.HIT_HALF_WIDTH_DP * d, FlagStacking.HIT_BELOW_TOP_DP * d)
        val flag = if (y <= flagArea.lowestY) {
            stackXs.indices.filter { abs(stackXs[it] - x) <= flagArea.halfWidthPx }.minByOrNull { abs(stackXs[it] - x) }
        } else {
            null
        }
        return when {
            flag != null -> StripHit.Flag(flag)
            abs(x - pinX) <= RIBBON_HALF_WIDTH_DP * d && y >= g.bottom - RIBBON_ABOVE_PIN_DP * d -> StripHit.Ribbon
            else -> StripHit.Block
        }
    }
}
