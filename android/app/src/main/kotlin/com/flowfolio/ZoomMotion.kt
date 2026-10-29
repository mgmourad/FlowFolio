package com.flowfolio

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.flowfolio.core.designsystem.ZoomOrigin

/**
 * The zoom from the Shelves tab into a shelf ([NavMotion], owner M3T1): the shelf's page grows out of the card tapped
 * ([ZoomOrigin]) while the shelves fade where they are, and shrinks back into it on Back. Only scale and alpha change,
 * so nothing is measured again while it runs.
 */
internal object ZoomMotion {
    /** The page grows out of the card tapped, fading in over the first part. */
    fun zoomIn(): EnterTransition = scaleIn(tween(ZOOM_MS, easing = Decelerate), ZOOM_SCALE, ZoomOrigin.pivot) +
        fadeIn(tween(ZOOM_FADE_MS, easing = Decelerate))

    /**
     * The page shrinks back into its card, gone before it gets there: it fades out over the first part of the shrink,
     * so it and the shelves coming back are hardly ever seen together.
     */
    fun zoomOut(): ExitTransition = scaleOut(tween(ZOOM_BACK_MS, easing = Accelerate), ZOOM_SCALE, ZoomOrigin.pivot) +
        fadeOut(tween(ZOOM_BACK_FADE_MS, easing = Decelerate))

    /** The shelves behind the zoom: they fade, holding still, so only one thing moves. */
    fun behindOut(): ExitTransition = fadeOut(tween(ZOOM_FADE_MS, easing = Accelerate))

    /** The shelves come back once the page has mostly gone. */
    fun behindIn(): EnterTransition =
        fadeIn(tween(ZOOM_BACK_MS - BEHIND_DELAY_MS, delayMillis = BEHIND_DELAY_MS, easing = Decelerate))

    /** Material 3's emphasized decelerate and accelerate curves. */
    private val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    private const val ZOOM_MS = 420
    private const val ZOOM_BACK_MS = 300
    private const val ZOOM_FADE_MS = 180
    private const val ZOOM_BACK_FADE_MS = 180
    private const val BEHIND_DELAY_MS = 80
    private const val ZOOM_SCALE = 0.25f
}
