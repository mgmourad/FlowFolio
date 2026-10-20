package com.flowfolio.feature.reader.riffle

import kotlin.math.abs
import kotlin.math.roundToInt

/** What the scrubber is doing (spec 7). */
sealed interface RiffleState {
    data object Idle : RiffleState

    /** The ribbon is held. */
    data class Dragging(val speed: ScrubSpeed) : RiffleState

    /** Released: turning sheets toward [target]. */
    data class Landing(val target: Int) : RiffleState

    /** A flag was tapped: the ribbon glides to [target] while the page lands there. */
    data class Travelling(val target: Int) : RiffleState
}

/** The scrubber's state changes, one event at a time; events that make no sense in a state leave it unchanged. */
object RiffleMachine {
    /** The ribbon was taken: from rest, or interrupting a landing or a travel. */
    fun grab(state: RiffleState): RiffleState =
        if (state is RiffleState.Dragging) state else RiffleState.Dragging(ScrubSpeed.NORMAL)

    fun speed(state: RiffleState, speed: ScrubSpeed): RiffleState =
        if (state is RiffleState.Dragging) RiffleState.Dragging(speed) else state

    /** The ribbon was let go over [target]; [current] is the unit shown. */
    fun release(state: RiffleState, target: Int, current: Int): RiffleState = when {
        state !is RiffleState.Dragging -> state
        target == current -> RiffleState.Idle
        else -> RiffleState.Landing(target)
    }

    /** A flag was tapped. A held ribbon cannot be, so the tap is ignored while dragging. */
    fun flagTapped(state: RiffleState, target: Int, current: Int): RiffleState = when {
        state is RiffleState.Dragging -> state
        target == current -> RiffleState.Idle
        else -> RiffleState.Travelling(target)
    }

    /** The page reached its target. */
    fun landed(state: RiffleState): RiffleState =
        if (state is RiffleState.Landing || state is RiffleState.Travelling) RiffleState.Idle else state
}

/** How the page turns to the target on release (spec 5.4): the units shown after each sheet, and its timing. */
data class LandingPlan(val steps: List<Int>, val sheetMs: Int, val staggerMs: Int) {
    val totalMs: Int get() = if (steps.isEmpty()) 0 else (steps.size - 1) * staggerMs + sheetMs

    companion object {
        const val MAX_SHEETS = 7
        const val SHEET_MS = 520
        const val STAGGER_MS = 70
        const val SINGLE_MS = 700

        /** Up to [MAX_SHEETS] sheets from [from] to [to], with the units between sampled evenly; the last is [to]. */
        fun between(from: Int, to: Int): LandingPlan {
            val distance = abs(to - from)
            val n = minOf(MAX_SHEETS, distance)
            val steps = (1..n).map { k -> (from + (to - from) * k.toFloat() / n).roundToInt() }
            return if (n == 1) LandingPlan(steps, SINGLE_MS, 0) else LandingPlan(steps, SHEET_MS, STAGGER_MS)
        }
    }
}

/** The loupe's mini page flips (spec 5.5). */
object LoupeFlips {
    const val MAX_IN_FLIGHT = 6
    private const val MIN_MS = 110L
    private const val MAX_MS = 320L
    private const val PER_GAP = 2.4f

    /** How long one flip takes, given the time since the previewed unit last changed: faster riffles flip quicker. */
    fun durationMs(sinceLastChangeMs: Long): Long = (sinceLastChangeMs * PER_GAP).toLong().coerceIn(MIN_MS, MAX_MS)
}
