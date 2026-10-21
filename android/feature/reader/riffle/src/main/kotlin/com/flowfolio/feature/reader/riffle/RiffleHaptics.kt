package com.flowfolio.feature.reader.riffle

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The riffle scrubber's haptics, mapped to system constants by API level (ADR-0023). */
object RiffleHaptics {
    /** Two ticks this far apart mark a bookmark. */
    const val DOUBLE_TICK_MS = 40L

    /** The system constant for [haptic] on API level [sdk]. */
    fun constantFor(haptic: RiffleHaptic, sdk: Int): Int {
        val segments = sdk >= SEGMENTS
        return when (haptic) {
            RiffleHaptic.TICK -> if (segments) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else CLOCK_TICK
            RiffleHaptic.SPEED, RiffleHaptic.FLAG -> if (segments) HapticFeedbackConstants.SEGMENT_TICK else CLOCK_TICK
            RiffleHaptic.CHAPTER -> if (sdk >= CONFIRM_SDK) CONFIRM else HapticFeedbackConstants.LONG_PRESS
        }
    }

    /** Plays [haptic] on [view]; a bookmark plays twice. Follows the system's own touch-feedback setting. */
    fun play(view: View, haptic: RiffleHaptic, scope: CoroutineScope) {
        val constant = constantFor(haptic, Build.VERSION.SDK_INT)
        view.performHapticFeedback(constant)
        if (haptic == RiffleHaptic.FLAG) {
            scope.launch {
                delay(DOUBLE_TICK_MS)
                view.performHapticFeedback(constant)
            }
        }
    }

    private const val SEGMENTS = 34
    private const val CONFIRM_SDK = 30
    private const val CLOCK_TICK = HapticFeedbackConstants.CLOCK_TICK
    private const val CONFIRM = HapticFeedbackConstants.CONFIRM
}
