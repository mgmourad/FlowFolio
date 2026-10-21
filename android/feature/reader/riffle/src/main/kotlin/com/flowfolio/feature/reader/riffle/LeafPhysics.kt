package com.flowfolio.feature.reader.riffle

import kotlin.math.abs

/**
 * The leaves of the fore edge as springs (spec 5.2): each leaf's x is pulled toward its target ([STIFFNESS], damped by
 * [DAMPING]), stepped in two substeps per frame with the real frame time so it behaves the same at 60 or 120 Hz. Each
 * leaf also bows in its direction of travel by its velocity. All state lives in arrays allocated once: stepping a frame
 * allocates nothing.
 */
class LeafPhysics(capacity: Int) {
    val x = FloatArray(capacity)
    val velocity = FloatArray(capacity)

    /** How far each leaf's middle leads its ends, in pixels. */
    val bow = FloatArray(capacity)

    var count = 0
        private set

    /** Puts [count] leaves at rest at [targets] (a new size, a new book). */
    fun reset(count: Int, targets: FloatArray) {
        require(count <= x.size) { "$count leaves, room for ${x.size}" }
        this.count = count
        for (j in 0 until count) {
            x[j] = targets[j]
            velocity[j] = 0f
            bow[j] = 0f
        }
    }

    /**
     * Moves every leaf toward its target for [dt] seconds; [maxBowPx] limits the bow. Returns true while any leaf is
     * still moving (the frame loop keeps going).
     */
    fun step(dt: Float, targets: FloatArray, maxBowPx: Float): Boolean {
        val h = dt / SUBSTEPS
        var moving = false
        for (j in 0 until count) {
            var p = x[j]
            var v = velocity[j]
            repeat(SUBSTEPS) {
                val a = STIFFNESS * (targets[j] - p) - DAMPING * v
                v += a * h
                p += v * h
            }
            x[j] = p
            velocity[j] = v
            bow[j] = (v * BOW_PER_VELOCITY).coerceIn(-maxBowPx, maxBowPx)
            if (abs(v) > REST_VELOCITY || abs(targets[j] - p) > REST_DISTANCE) moving = true
        }
        return moving
    }

    companion object {
        const val STIFFNESS = 700f
        const val DAMPING = 22f
        const val SUBSTEPS = 2

        /** Bow per unit of velocity (pixels per pixel/second): the mock's 0.022, density-independent. */
        const val BOW_PER_VELOCITY = 0.022f

        /** Below these a leaf is at rest (pixels per second, pixels). */
        private const val REST_VELOCITY = 0.5f
        private const val REST_DISTANCE = 0.05f
    }
}
