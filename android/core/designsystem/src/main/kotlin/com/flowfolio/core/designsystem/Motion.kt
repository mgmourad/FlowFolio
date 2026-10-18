package com.flowfolio.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing

/** The mock's motion tokens (`--ease-*`, `--dur-*`). Page turns keep our own curl timing (ADR-0015). */
object Motion {
    /** Default for entrances and most transitions. */
    val EaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Overshooting spring for toggles, tabs, pressed buttons. */
    val Spring = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    /** Slide and scroll page styles. */
    val Page = CubicBezierEasing(0.45f, 0.05f, 0.25f, 1f)

    const val FAST_MS = 180
    const val MEDIUM_MS = 380
    const val SLOW_MS = 650

    /** Toggle knob travel and tab indicator (`.5s var(--ease-spring)`). */
    const val SPRING_MS = 500

    /** Book lift on press (`.7s var(--ease-out)`). */
    const val BOOK_MS = 700

    /** Progress bars grow in (`1.4s`, after `.3s`). */
    const val GROW_MS = 1400
    const val GROW_DELAY_MS = 300
}
