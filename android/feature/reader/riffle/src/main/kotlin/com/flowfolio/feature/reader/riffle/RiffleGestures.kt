package com.flowfolio.feature.reader.riffle

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * The strip's touches (spec 5.3). A touch belongs to what it first lands on: a flag (tap-only, cancelled after 10 dp of
 * movement), the ribbon (the only riffle), or the block (a wiggle hint). Every touch is consumed, so nothing reaches
 * the page underneath.
 */
internal fun Modifier.riffleGestures(state: RiffleScrubberState, events: RiffleEvents): Modifier = this
    .pointerInput(state, events) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            events.onInteraction()
            val g = state.geometry ?: return@awaitEachGesture
            val stackXs = state.stacks.map { state.leaves.x[it.leaf] }
            when (val hit = StripHits.at(down.position.x, down.position.y, g, state.pinX, stackXs)) {
                is StripHit.Flag -> flagTap(down, state, hit.index, events)

                StripHit.Ribbon -> riffle(down, state, g, events)

                StripHit.Block -> {
                    state.wiggle()
                    consumeUntilUp(down)
                }
            }
        }
    }
    .pointerInput(state) {
        // A mouse or trackpad hovering a flag lifts it and previews its spread (spec 4.3).
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.first()
                val g = state.geometry
                state.hovered =
                    if (g != null && change.type == PointerType.Mouse && event.type != PointerEventType.Exit) {
                        val hit = StripHits.at(
                            change.position.x,
                            change.position.y,
                            g,
                            state.pinX,
                            state.stacks.map {
                                state.leaves.x[it.leaf]
                            }
                        )
                        (hit as? StripHit.Flag)?.index ?: -1
                    } else {
                        -1
                    }
            }
        }
    }

private suspend fun AwaitPointerEventScope.flagTap(
    down: PointerInputChange,
    state: RiffleScrubberState,
    index: Int,
    events: RiffleEvents
) {
    val tap = TapTracker(down.position.x, down.position.y, TapTracker.SLOP_DP * density)
    val up = trackUntilUp(down) { tap.move(it.position.x, it.position.y) }
    val stack = state.stacks.getOrNull(index)?.stack
    if (up && tap.up() && stack != null) {
        if (stack.count == 1) events.onFlag(stack.flags.first()) else state.openStack = stack
    }
}

/** A drag of the ribbon: fine scrubbing by height, feedback as units pass, landing on release. */
private suspend fun AwaitPointerEventScope.riffle(
    down: PointerInputChange,
    state: RiffleScrubberState,
    g: StripGeometry,
    events: RiffleEvents
) {
    val drag = ScrubDrag(down.position.x, down.position.y, state.pinX, g.x0, g.x1, density)
    val rules = state.feedbackRules()
    var unit = state.mapping.unitAtX(drag.ribbonX, g.x0, g.x1)
    state.riffleX = drag.ribbonX
    state.scrubSpeed = ScrubSpeed.NORMAL
    events.onPause(ChromePause.DRAGGING, true)
    trackUntilUp(down) { change ->
        if (drag.move(change.position.x, change.position.y)) {
            state.scrubSpeed = drag.speed
            events.onSpeed(drag.speed)
        }
        state.riffleX = drag.ribbonX
        val next = state.mapping.unitAtX(drag.ribbonX, g.x0, g.x1)
        if (next != unit) {
            events.onFeedback(rules.between(unit, next))
            unit = next
        }
    }
    state.riffleX = null
    state.scrubSpeed = ScrubSpeed.NORMAL
    events.onPause(ChromePause.DRAGGING, false)
    events.onRelease(unit)
    state.nudges++
}

/** Follows [down]'s pointer, calling [onMove] for each move, until it lifts (true) or is cancelled (false). */
private suspend fun AwaitPointerEventScope.trackUntilUp(
    down: PointerInputChange,
    onMove: (PointerInputChange) -> Unit
): Boolean {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return false
        change.consume()
        if (!change.pressed) return true
        onMove(change)
    }
}

private suspend fun AwaitPointerEventScope.consumeUntilUp(down: PointerInputChange) {
    trackUntilUp(down) { }
}
