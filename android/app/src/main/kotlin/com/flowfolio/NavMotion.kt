package com.flowfolio

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.flowfolio.core.designsystem.Motion

/**
 * How screens change (ADR-0033), after Material 3's motion patterns:
 * - between the top-level tabs, **fade through** (ADR-0052, calmer than ADR-0034's zoom): the old tab fades and
 *   recedes a touch, the new one fades in from 98% as its elements rise into place
 *   ([com.flowfolio.core.designsystem.reveal]);
 * - into a page and back (a shelf, a book's bookmarks or annotations, edit details), **shared axis X**: the new page
 *   slides in from the side as the old one drifts back the other way, reversed on Back (and followed by the predictive
 *   back gesture);
 * - to and from the details and the reader, a **plain fade**: the cover flight and the book opening carry the motion
 *   there, and a moving page would throw off where the cover lands;
 * - from the Shelves tab into a shelf, a **zoom** (owner, M3T1): the shelf's page grows out of the card tapped
 *   ([ZoomOrigin]) as the shelves fade, and shrinks back into it on Back.
 */
internal object NavMotion {
    private val tabRoutes = TopLevel.entries.map { it.route }.toSet()

    private fun NavBackStackEntry.isTab() = destination.route?.substringBefore('?') in tabRoutes

    /** The details page and the reader animate their cover and book themselves. */
    private fun NavBackStackEntry.ownsMotion() =
        destination.route?.let { (it.startsWith("book/{") && "?from=" in it) || it.startsWith("reader/") } == true

    private enum class Kind { TABS, OWN, AXIS, ZOOM }

    private fun NavBackStackEntry.isShelf() = destination.route == Routes.SHELF

    private fun NavBackStackEntry.isShelves() = destination.route == TopLevel.Shelves.route

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.kind() = when {
        initialState.isTab() && targetState.isTab() -> Kind.TABS

        (initialState.isShelves() && targetState.isShelf()) || (initialState.isShelf() && targetState.isShelves()) ->
            Kind.ZOOM

        initialState.ownsMotion() || targetState.ownsMotion() -> Kind.OWN

        else -> Kind.AXIS
    }

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        when (kind()) {
            Kind.TABS -> fadeThroughIn()
            Kind.OWN -> fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut))
            Kind.AXIS -> axisIn(forward = true)
            Kind.ZOOM -> ZoomMotion.zoomIn()
        }
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        when (kind()) {
            Kind.TABS -> fadeThroughOut()
            Kind.OWN -> fadeOut(tween(Motion.FAST_MS, easing = Motion.EaseOut))
            Kind.AXIS -> axisOut(forward = true)
            Kind.ZOOM -> ZoomMotion.behindOut()
        }
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        when (kind()) {
            Kind.TABS -> fadeThroughIn()
            Kind.OWN -> fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut))
            Kind.AXIS -> axisIn(forward = false)
            Kind.ZOOM -> ZoomMotion.behindIn()
        }
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        when (kind()) {
            Kind.TABS -> fadeThroughOut()
            Kind.OWN -> fadeOut(tween(Motion.FAST_MS, easing = Motion.EaseOut))
            Kind.AXIS -> axisOut(forward = false)
            Kind.ZOOM -> ZoomMotion.zoomOut()
        }
    }

    private fun fadeThroughIn() = fadeIn(tween(THROUGH_IN_MS, delayMillis = THROUGH_OUT_MS, easing = Emphasized)) +
        scaleIn(
            tween(THROUGH_IN_MS, delayMillis = THROUGH_OUT_MS, easing = Emphasized),
            initialScale = THROUGH_SCALE
        )

    private fun fadeThroughOut() = fadeOut(tween(THROUGH_OUT_MS, easing = Accelerate)) +
        scaleOut(tween(THROUGH_OUT_MS, easing = Accelerate), targetScale = OUT_SCALE)

    /** In from the side the page lies on: from the end when going forward, from the start when coming back. */
    private fun axisIn(forward: Boolean) = slideInHorizontally(tween(AXIS_MS, easing = Emphasized)) { w ->
        (w * AXIS_SHARE).toInt() * if (forward) 1 else -1
    } +
        fadeIn(tween(AXIS_FADE_IN_MS, delayMillis = AXIS_FADE_DELAY_MS, easing = Emphasized))

    private fun axisOut(forward: Boolean) = slideOutHorizontally(tween(AXIS_MS, easing = Emphasized)) { w ->
        (w * AXIS_SHARE).toInt() * if (forward) -1 else 1
    } +
        fadeOut(tween(AXIS_FADE_OUT_MS, easing = Accelerate))

    /** Material 3's emphasized decelerate and accelerate curves. */
    private val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    private const val THROUGH_OUT_MS = 90
    private const val THROUGH_IN_MS = 300
    private const val THROUGH_SCALE = 0.98f
    private const val OUT_SCALE = 0.99f
    private const val AXIS_MS = 360
    private const val AXIS_SHARE = 0.12f
    private const val AXIS_FADE_IN_MS = 260
    private const val AXIS_FADE_DELAY_MS = 60
    private const val AXIS_FADE_OUT_MS = 120
}
