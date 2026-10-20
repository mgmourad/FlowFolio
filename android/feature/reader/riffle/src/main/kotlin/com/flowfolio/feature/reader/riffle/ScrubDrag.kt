package com.flowfolio.feature.reader.riffle

/** How fast the ribbon follows the finger, set by how far the finger has moved up off the ribbon (spec 3.2). */
@Suppress("MagicNumber") // the spec's factors, named by their speeds
enum class ScrubSpeed(val factor: Float) {
    NORMAL(1f),
    HALF(0.5f),
    QUARTER(0.25f),
    FINE(0.1f);

    companion object {
        private const val HALF_DP = 56f
        private const val QUARTER_DP = 112f
        private const val FINE_DP = 168f

        /** The speed for a finger [liftDp] above the point where it took the ribbon (negative: below it). */
        fun forLift(liftDp: Float): ScrubSpeed = when {
            liftDp < HALF_DP -> NORMAL
            liftDp < QUARTER_DP -> HALF
            liftDp < FINE_DP -> QUARTER
            else -> FINE
        }
    }
}

/**
 * One drag of the ribbon. At full speed the ribbon keeps its grab offset to the finger (absolute); once the finger
 * moves up into a slower speed the ribbon moves by the finger's horizontal movement times the factor (relative), and
 * stays relative for the rest of the drag, so it never jumps back to the finger.
 */
class ScrubDrag(
    downX: Float,
    private val downY: Float,
    ribbonX: Float,
    private val minX: Float,
    private val maxX: Float,
    private val density: Float
) {
    private val grabOffset = downX - ribbonX
    private var lastX = downX
    private var relative = false

    var ribbonX: Float = ribbonX.coerceIn(minX, maxX)
        private set

    var speed: ScrubSpeed = ScrubSpeed.NORMAL
        private set

    /** The finger moved to ([x], [y]); returns true when the speed changed (a haptic tick). */
    fun move(x: Float, y: Float): Boolean {
        val next = ScrubSpeed.forLift((downY - y) / density)
        val changed = next != speed
        speed = next
        if (speed != ScrubSpeed.NORMAL) relative = true
        ribbonX = if (relative) ribbonX + (x - lastX) * speed.factor else x - grabOffset
        ribbonX = ribbonX.coerceIn(minX, maxX)
        lastX = x
        return changed
    }
}
