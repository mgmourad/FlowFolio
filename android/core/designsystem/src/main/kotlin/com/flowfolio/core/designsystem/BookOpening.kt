package com.flowfolio.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/**
 * Where a book's cover is on screen ([bounds], in root coordinates), what it shows, and the angle it is turned at,
 * so the book-opening animation can start from it. Updated by [coverSpot]; not observable state.
 */
@Stable
class CoverSpot {
    var bounds: Rect = Rect.Zero
    var face: BookFace? = null
    var angle: Float = SHELF_ANGLE
}

/** Records this cover's place in [spot]. */
fun Modifier.coverSpot(spot: CoverSpot): Modifier = onGloballyPositioned {
    val origin = it.positionInRoot()
    spot.bounds = Rect(origin.x, origin.y, origin.x + it.size.width, origin.y + it.size.height)
}

/** Opens books with the animation; provided by [BookOpeningHost]. */
@Stable
class BookOpener internal constructor() {
    internal var opening by mutableStateOf<Opening?>(null)

    /**
     * Plays the book opening from [spot], calling [onOpen] (show the reader) once the cover covers the page, so the
     * reader appears under the cover as it swings open. Without a known cover it just opens.
     */
    fun open(spot: CoverSpot, onOpen: () -> Unit) {
        val face = spot.face
        when {
            opening != null -> Unit
            face == null || spot.bounds.isEmpty -> onOpen()
            else -> opening = Opening(face, spot.bounds, spot.angle, onOpen)
        }
    }
}

internal class Opening(val face: BookFace, val from: Rect, val angle: Float, val onOpen: () -> Unit)

val LocalBookOpener = staticCompositionLocalOf<BookOpener?> { null }

/**
 * The book opening (the mock's `openReader`, made to feel like a real book): the cover flies from where it was and
 * grows to the size of the page it will open on, turning to face the reader, while the page colour [paper] floods the
 * screen behind it (520 ms). Then the reader is shown underneath and the cover swings open on its spine, its inside
 * showing as it passes the edge-on point, with the light on it falling away, while the paper melts into the real page
 * (720 ms). [spread] says whether the reader shows two pages in a window of that size (dp), so the cover opens onto
 * the right-hand page. Only a flat image is moved and turned, so nothing is re-rasterized per frame.
 */
@Composable
fun BookOpeningHost(paper: Color, spread: (Float, Float) -> Boolean, content: @Composable () -> Unit) {
    val opener = remember { BookOpener() }
    CompositionLocalProvider(LocalBookOpener provides opener) {
        Box(Modifier.fillMaxSize()) {
            content()
            opener.opening?.let { o -> OpeningOverlay(o, paper, spread) { opener.opening = null } }
        }
    }
}

@Composable
private fun OpeningOverlay(o: Opening, paper: Color, spread: (Float, Float) -> Boolean, onDone: () -> Unit) {
    val flight = remember(o) { Animatable(0f) }
    val hinge = remember(o) { Animatable(0f) }
    LaunchedEffect(o) {
        try {
            flight.animateTo(1f, tween(FLIGHT_MS, easing = Flight))
            o.onOpen()
            hinge.animateTo(1f, tween(HINGE_MS, easing = Hinge))
        } finally {
            onDone()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val target = pageRect(w, h, o.face, wide = spread(maxWidth.value, maxHeight.value))
        val f = flight.value
        val t = hinge.value
        // The paper floods in with the flight and melts into the reader's own page in the second half of the swing.
        val paperAlpha = if (t < MELT_FROM) f else 1f - (t - MELT_FROM) / (1f - MELT_FROM)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = paperAlpha }.background(paper))
        val rect = lerp(o.from, target, f)
        CoverLeaf(
            o.face,
            paper,
            t,
            Modifier
                .place(rect)
                .graphicsLayer {
                    cameraDistance = CAMERA * density
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    // Flight: turned like the shelf book, coming round to face the reader. Swing: open on the spine.
                    rotationY = lerp(o.angle, 0f, f) - OPEN_ANGLE * t
                }
        )
    }
}

/** The cover leaf: its face up to edge-on, then its inside (the endpaper) as it carries on round. */
@Composable
private fun CoverLeaf(face: BookFace, paper: Color, t: Float, modifier: Modifier) {
    val inside = t > EDGE_ON
    Box(
        modifier.drawWithContent {
            drawContent()
            // The light on the cover falls away as it turns from the screen, then comes back on the inside.
            val turned = if (inside) 1f - (t - EDGE_ON) / (1f - EDGE_ON) else t / EDGE_ON
            drawRect(Color.Black.copy(alpha = TURN_SHADE * turned))
            // The binding: a soft crease along the spine.
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Black.copy(alpha = CREASE_ALPHA), Color.Transparent),
                    endX = size.width * CREASE_WIDTH
                )
            )
        }
    ) {
        when {
            inside -> Box(Modifier.fillMaxSize().background(paper))

            face.cover != null -> Image(
                face.cover,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )

            else -> DrawnCover(face)
        }
    }
}

/** A cover drawn from the book's colours and title, as on the shelf. */
@Composable
private fun DrawnCover(face: BookFace) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(face.colors.start, face.colors.end))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            face.title,
            color = face.colors.ink,
            fontFamily = FlowFolioType.heroTitle.fontFamily,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}

/** Lays this out at exactly [rect] (root coordinates; the host fills the root). */
private fun Modifier.place(rect: Rect): Modifier = layout { measurable, _ ->
    val width = rect.width.roundToInt().coerceAtLeast(1)
    val height = rect.height.roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(placeable.width, placeable.height) { placeable.place(rect.left.roundToInt(), rect.top.roundToInt()) }
}

/**
 * The page the book opens on, fitted like the reader fits it: the whole screen on a phone or folded, the right-hand
 * page of the spread when unfolded.
 */
private fun pageRect(w: Float, h: Float, face: BookFace, wide: Boolean): Rect {
    val aspect = face.cover?.let { it.width.toFloat() / it.height } ?: LETTER_ASPECT
    val areaLeft = if (wide) w / 2f else 0f
    val areaWidth = w - areaLeft
    val fitWidth = minOf(areaWidth, h * aspect)
    val fitHeight = fitWidth / aspect
    val left = if (wide) areaLeft else (w - fitWidth) / 2f
    val top = (h - fitHeight) / 2f
    return Rect(left, top, left + fitWidth, top + fitHeight)
}

/** A shelf book's resting angle (`rotateY(26deg)`), the default start of the flight. */
const val SHELF_ANGLE = 26f

private const val FLIGHT_MS = 520
private const val HINGE_MS = 720
private val Flight = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)
private val Hinge = CubicBezierEasing(0.45f, 0.05f, 0.25f, 1f)
private const val OPEN_ANGLE = 180f
private const val EDGE_ON = 0.5f
private const val MELT_FROM = 0.45f
private const val CAMERA = 14f
private const val TURN_SHADE = 0.45f
private const val CREASE_ALPHA = 0.28f
private const val CREASE_WIDTH = 0.06f
private const val LETTER_ASPECT = 8.5f / 11f
