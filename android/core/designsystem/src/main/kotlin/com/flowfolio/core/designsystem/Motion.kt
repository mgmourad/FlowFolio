package com.flowfolio.core.designsystem

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring as Springs
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset

/**
 * Motion tokens (ADR-0052). Interactive things move on **springs** (they can be interrupted mid-flight and keep
 * their velocity, as current Android and iOS motion does); things that appear rise a short way on a decelerating
 * curve; colour changes cross-fade. Page turns keep our own curl timing (ADR-0015).
 */
object Motion {
    /** The mock's `cubic-bezier(.2,.8,.2,1)`: panels, menus, snackbars and screens rising into place. */
    val Rise = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

    /** Material 3's emphasized decelerate: for larger surfaces entering (sheets, the What's new card). */
    val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Material 3's emphasized accelerate: for surfaces leaving, quicker than they came. */
    val EmphasizedExit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Default for entrances and most transitions. */
    val EaseOut = Rise

    /** Kept for tween callers from before ADR-0052; new code uses [springSpec]. */
    val Spring = CubicBezierEasing(0.34f, 1.4f, 0.64f, 1f)

    val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    /** Slide and scroll page styles. */
    val Page = CubicBezierEasing(0.45f, 0.05f, 0.25f, 1f)

    const val FAST_MS = 160
    const val MEDIUM_MS = 280
    const val SLOW_MS = 480

    /** Panels, menus and snackbars rising (`rise .28s`). */
    const val RISE_MS = 280

    /** Menus popping from their anchor (`menu .18s`). */
    const val MENU_MS = 180

    /** Bottom sheets sliding up (`up .32s`). */
    const val SHEET_MS = 320

    /** How far things rise into place, in dp (`translateY(16px)`). */
    const val RISE_DP = 16

    /** Kept for tween callers: the toggle and tab travel. */
    const val SPRING_MS = 360

    /** Book lift on press. */
    const val BOOK_MS = 600

    /** Progress bars grow in. */
    const val GROW_MS = 1100
    const val GROW_DELAY_MS = 200

    /** Toggles, tab indicators, selection: settles quickly with no visible bounce. */
    fun <T> springSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Springs.DampingRatioNoBouncy, stiffness = Springs.StiffnessMediumLow)

    /** A touch of overshoot for things the finger let go of (a toggle's thumb, a dropped pin). */
    fun <T> bouncySpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = BOUNCY_DAMPING, stiffness = Springs.StiffnessMedium)

    /** Pressed buttons: fast in, settling back on release. */
    fun <T> pressSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = PRESS_DAMPING, stiffness = Springs.StiffnessHigh)

    /** Colour cross-fades (selected states, theme-aware fills). */
    fun colorSpec(): AnimationSpec<Color> = tween(COLOR_MS, easing = Rise)

    /** An offset rising into place. */
    fun riseSpec(): FiniteAnimationSpec<IntOffset> = tween(RISE_MS, easing = Rise)

    private const val COLOR_MS = 220
    private const val BOUNCY_DAMPING = 0.72f
    private const val PRESS_DAMPING = 0.65f
}

/**
 * True when the reader asked the system for less motion ("Remove animations"): flips, lenses and entrances are then
 * skipped, and only haptics mark what they would have shown (ADR-0052).
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Reads the system's animator scale once per composition of the theme: zero means "Remove animations" is on. */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Whether to skip decorative motion here. */
val reducedMotion: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalReducedMotion.current
