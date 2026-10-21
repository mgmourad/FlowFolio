package com.flowfolio.feature.reader.riffle

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.exp

/** The strip's layout in pixels: the page block between [x0] and [x1], from [top] to [bottom] (spec 4.1, the mock). */
class StripGeometry(val width: Float, val height: Float, val density: Float) {
    val x0 = SIDE_DP * density
    val x1 = width - SIDE_DP * density
    val top = TOP_DP * density
    val bottom = BOTTOM_DP * density
    val ribbonBottom = height - RIBBON_GAP_DP * density

    private companion object {
        const val SIDE_DP = 26f
        const val TOP_DP = 24f
        const val BOTTOM_DP = 74f
        const val RIBBON_GAP_DP = 3f
    }
}

/**
 * Everything the riffle strip shows and animates (ADR-0022). The reader (or the demo) sets [currentUnit], [flags] and,
 * while the ribbon is held, [riffleX]; the strip's frame loop calls [advance] each frame while anything moves. Values
 * read only in drawing ([frame] and the animation fields) change without recomposing anything.
 */
@Stable
class RiffleScrubberState(val book: BookGeometry, current: Int = 0) {
    val mapping = StripMapping(book)

    private var wake by mutableLongStateOf(0L)

    /** Raised whenever input changes, so a stopped frame loop starts again. */
    internal val wakeups: Long get() = wake

    private var current by mutableIntStateOf(current.coerceIn(0, (book.unitCount - 1).coerceAtLeast(0)))

    /** The unit the reader shows. */
    var currentUnit: Int
        get() = current
        set(value) {
            current = value.coerceIn(0, (book.unitCount - 1).coerceAtLeast(0))
            wakeUp()
        }

    var flags: List<RiffleFlag> by mutableStateOf(emptyList())

    /** The unit under the ribbon while riffling or travelling, else [currentUnit]: what the header and loupe show. */
    var previewUnit by mutableIntStateOf(this.current)
        private set

    /** True while the previewed unit follows the ribbon rather than the reader. */
    var live by mutableStateOf(false)
        private set

    /** Where the held ribbon should be (strip pixels), or null when nothing holds it. */
    var riffleX: Float? = null
        set(value) {
            field = value
            wakeUp()
        }

    /** A demo fingertip is shown on the ribbon (the demo screen). */
    var ghost: Boolean = false
        set(value) {
            field = value
            wakeUp()
        }

    /** Logs the strip's draw times (the demo screen and measurements). */
    var measureDraw: Boolean = false

    /** Draw invalidation: read only while drawing, so a frame redraws the strip and nothing else. */
    internal var frame by mutableLongStateOf(0L)
        private set

    internal var geometry: StripGeometry? = null
        private set
    internal var leaves = LeafPhysics(0)
        private set
    internal var sheetsPerLeaf = 1
        private set
    private var rest = FloatArray(0)
    private var targets = FloatArray(0)
    private var gap = Gap(1f)

    internal var pinX = 0f
        private set
    internal var pinVelocity = 0f
        private set
    internal var engagement = 0f
        private set
    internal var grab = 0f
        private set
    internal var ghostAlpha = 0f
        private set

    /** How much the ribbon sways at rest: 1 for a while after any movement, then fading to 0 so the strip stops. */
    internal var swayAmount = 1f
        private set
    internal var timeSeconds = 0f
        private set

    private var lastNanos = 0L
    private var lastMotionNanos = 0L
    private var resumed = true

    /** The strip was laid out (or resized): places the leaves at rest and the pin at the current unit. */
    internal fun layout(width: Float, height: Float, density: Float) {
        val g = StripGeometry(width, height, density)
        if (geometry?.let { it.width == width && it.height == height && it.density == density } == true) return
        geometry = g
        gap = Gap(density)
        val span = g.x1 - g.x0
        sheetsPerLeaf = mapping.sheetsPerLeaf(span)
        val count = mapping.leafCount(span)
        if (leaves.x.size < count) leaves = LeafPhysics(count)
        rest = FloatArray(count) { j -> g.x0 + (j + HALF) * span / count }
        targets = FloatArray(count)
        pinX = mapping.xOf(current, g.x0, g.x1)
        gap.prepare(0f, pinX, g.x0, g.x1)
        for (j in 0 until count) targets[j] = rest[j] + gap.displacement(rest[j])
        leaves.reset(count, targets)
        wakeUp()
    }

    /** The resting x of leaf [j] (before the gap moves it). */
    internal fun restX(j: Int): Float = rest[j]

    /** The leaf that shows [page]'s sheet. */
    internal fun leafOfPage(page: Int): Int = (mapping.sheetOf(page) / sheetsPerLeaf).coerceIn(0, leaves.count - 1)

    private fun wakeUp() {
        resumed = true
        wake++
    }

    /** One frame at [nanos]; returns true while the strip needs more frames. */
    internal fun advance(nanos: Long): Boolean {
        val g = geometry ?: return false
        if (resumed) {
            lastNanos = nanos - FRAME_NANOS
            lastMotionNanos = nanos
            resumed = false
        }
        val dt = ((nanos - lastNanos) / NANOS).coerceIn(0f, MAX_DT)
        lastNanos = nanos
        timeSeconds = nanos / NANOS
        val held = riffleX
        val target = held?.coerceIn(g.x0, g.x1) ?: mapping.xOf(current, g.x0, g.x1)
        movePin(held != null, target, dt)
        updatePreview(held != null || abs(target - pinX) > PIN_REST_PX * g.density, g)

        gap.prepare(engagement, pinX, g.x0, g.x1)
        for (j in 0 until leaves.count) targets[j] = rest[j] + gap.displacement(rest[j])
        val leavesMoving = leaves.step(dt, targets, MAX_BOW_DP * g.density)

        val moving = held != null || leavesMoving || engagement > SETTLED || abs(target - pinX) > SETTLED
        if (moving) lastMotionNanos = nanos
        val idle = (nanos - lastMotionNanos) / NANOS
        swayAmount = (1f - (idle - SWAY_HOLD_S) / SWAY_FADE_S).coerceIn(0f, 1f)
        frame++
        return moving || swayAmount > 0f || ghostAlpha > SETTLED
    }

    /** The pin follows the held ribbon closely, or glides home; engagement, grab and the ghost ease with it. */
    private fun movePin(held: Boolean, target: Float, dt: Float) {
        engagement = approach(engagement, if (held) 1f else 0f, dt)
        grab = approach(grab, if (held) 1f else 0f, dt * GRAB_RATE)
        ghostAlpha += ((if (ghost) 1f else 0f) - ghostAlpha) * (1 - exp(-dt * GHOST_RATE))
        val previous = pinX
        pinX += (target - pinX) * (1 - exp(-dt * if (held) HELD_RATE else REST_RATE))
        pinVelocity += ((pinX - previous) / dt.coerceAtLeast(MIN_DT) - pinVelocity) * VELOCITY_SMOOTHING
    }

    private fun updatePreview(following: Boolean, g: StripGeometry) {
        live = following
        previewUnit = if (following) mapping.unitAtX(pinX, g.x0, g.x1) else current
    }

    /** Engagement springs up quickly and settles more slowly (the mock's rates). */
    private fun approach(value: Float, target: Float, dt: Float): Float =
        value + (target - value) * (1 - exp(-dt * if (target > value) RISE_RATE else FALL_RATE))

    private companion object {
        const val HALF = 0.5f
        const val NANOS = 1e9f
        const val FRAME_NANOS = 16_666_667L
        const val MAX_DT = 0.05f
        const val MIN_DT = 1e-3f
        const val RISE_RATE = 14f
        const val FALL_RATE = 6f
        const val GRAB_RATE = 2f
        const val GHOST_RATE = 10f
        const val HELD_RATE = 32f
        const val REST_RATE = 9f
        const val VELOCITY_SMOOTHING = 0.25f
        const val PIN_REST_PX = 0.5f
        const val SETTLED = 0.01f
        const val MAX_BOW_DP = 14f

        /** The ribbon keeps swaying this long after the last movement, then fades over [SWAY_FADE_S]. */
        const val SWAY_HOLD_S = 3f
        const val SWAY_FADE_S = 1f
    }
}
