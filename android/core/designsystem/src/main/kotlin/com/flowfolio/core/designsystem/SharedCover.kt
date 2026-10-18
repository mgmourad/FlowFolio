package com.flowfolio.core.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** The app's shared-element scope (the cover flight between the library and a book's details). */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The navigation destination's enter/exit scope, which drives shared elements on that screen. */
val LocalDestinationScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * The mock's cover flight (`flyCover`): a book cover with the same [key] on two screens flies from one to the other
 * over 620 ms on `cubic-bezier(.2,.9,.2,1)`. Outside a shared-transition scope it does nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCover(key: String): Modifier {
    val shared = LocalSharedTransition.current
    val scope = LocalDestinationScope.current
    if (shared == null || scope == null) return this
    return with(shared) {
        this@sharedCover.sharedElement(
            rememberSharedContentState(key),
            scope,
            boundsTransform = { _, _ -> tween(COVER_FLIGHT_MS, easing = CoverFlight) }
        )
    }
}

/** The key that pairs a book's cover in a library row ([origin]) with its cover on the details screen. */
fun coverKey(bookId: String, origin: String) = "cover/$origin/$bookId"

private const val COVER_FLIGHT_MS = 620
private val CoverFlight = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)
