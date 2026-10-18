package com.flowfolio.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay

/**
 * One cover flying between two screens: from where it was ([from], at [fromAngle]) to the cover with the same
 * [to] key on the next screen, which reports where it is once laid out.
 */
@Stable
class Flight internal constructor(val face: BookFace, val from: Rect, val fromAngle: Float, internal val to: String) {
    internal var target by mutableStateOf<Rect?>(null)
    internal var targetAngle = 0f
    internal var landed by mutableStateOf(false)
}

/** Starts and lands cover flights; provided by [CoverFlightHost]. */
@Stable
class CoverFlights internal constructor() {
    internal var flight by mutableStateOf<Flight?>(null)

    /** Flies the cover at [spot] to the cover registered under [to] on the screen being shown next. */
    fun launch(spot: CoverSpot, to: String) {
        val face = spot.face ?: return
        if (spot.bounds.isEmpty) return
        flight = Flight(face, spot.bounds, spot.angle, to)
    }
}

val LocalCoverFlights = staticCompositionLocalOf<CoverFlights?> { null }

/**
 * The cover flight between the library and a book's details (the mock's `flyCover`, 620 ms). A single 3D book, drawn
 * at the landing size, moves and scales on one smooth curve from the cover that was tapped to the cover on the next
 * screen, turning from its angle there to the angle it lands at; the landing cover stays hidden until it arrives, so
 * there is never a second book or a jump. Only a layer is moved, so nothing is re-rasterized per frame.
 */
@Composable
fun CoverFlightHost(content: @Composable () -> Unit) {
    val flights = remember { CoverFlights() }
    CompositionLocalProvider(LocalCoverFlights provides flights) {
        Box(Modifier.fillMaxSize()) {
            content()
            val f = flights.flight
            val target = f?.target
            if (f != null && target != null) FlyingCover(f, target) { flights.flight = null }
            // A flight whose landing never appears (the next screen has no such cover) is dropped.
            if (f != null) {
                LaunchedEffect(f) {
                    delay(LANDING_WAIT_MS)
                    if (f.target == null && flights.flight === f) flights.flight = null
                }
            }
        }
    }
}

@Composable
private fun FlyingCover(f: Flight, target: Rect, onLanded: () -> Unit) {
    val t = remember(f) { Animatable(0f) }
    LaunchedEffect(f) {
        try {
            t.animateTo(1f, tween(FLIGHT_MS, easing = FastOutSlowInEasing))
        } finally {
            f.landed = true
            onLanded()
        }
    }
    val density = LocalDensity.current
    val width = with(density) { target.width.toDp() }
    val height = with(density) { target.height.toDp() }
    Book3D(
        f.face,
        width,
        height,
        restAngle = lerp(f.fromAngle, f.targetAngle, t.value),
        modifier = Modifier.graphicsLayer {
            val p = t.value
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = lerp(f.from.left, target.left, p)
            translationY = lerp(f.from.top, target.top, p)
            scaleX = lerp(f.from.width / target.width, 1f, p)
            scaleY = lerp(f.from.height / target.height, 1f, p)
        }
    )
}

/**
 * Marks a cover as a flight's landing place under [key] (resting at [angle]): it reports where it is to a flight
 * heading for it, and stays hidden until that flight lands.
 */
@Composable
fun Modifier.coverLanding(key: String, angle: Float): Modifier {
    val flights = LocalCoverFlights.current ?: return this
    val incoming = flights.flight?.takeIf { it.to == key && !it.landed }
    return onGloballyPositioned { coordinates ->
        val f = flights.flight
        if (f != null && f.to == key && f.target == null) {
            val origin = coordinates.positionInRoot()
            f.targetAngle = angle
            f.target = Rect(origin.x, origin.y, origin.x + coordinates.size.width, origin.y + coordinates.size.height)
        }
    }.graphicsLayer { alpha = if (incoming != null) 0f else 1f }
}

/** The key of a book's cover on its details page. */
fun detailsCoverKey(bookId: String) = "details/$bookId"

/** The key of a book's cover in a library row or on a shelf page ([origin] tells which). */
fun shelfCoverKey(bookId: String, origin: String) = "shelf/$origin/$bookId"

private const val FLIGHT_MS = 620
private const val LANDING_WAIT_MS = 1_000L
