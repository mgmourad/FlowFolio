package com.flowfolio.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioPalette
import com.flowfolio.core.designsystem.FlowFolioType
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * An EPUB equation or table that the column shows too small (a wide matrix on the cover screen), or a picture, on its
 * own over the paper: fitted to the screen first, then pinched up to [MAX_ZOOM] times that and panned. A tap or Back
 * closes it.
 */
@Composable
internal fun FigureZoom(figure: Figure, book: ReaderViewModel, onDismiss: () -> Unit) {
    val image by rememberFigure(figure, book)
    var zoom by remember(figure) { mutableFloatStateOf(1f) }
    var pan by remember(figure) { mutableStateOf(Offset.Zero) }
    val paper = PAPER
    val title = stringResource(
        if (figure is Figure.Picture) R.string.reader_picture_zoom else R.string.reader_island_zoom
    )
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .semantics {
                paneTitle = title
                contentDescription = title
            }
            .testTag(ReaderTestTags.ISLAND_ZOOM)
            .pointerInput(figure) { detectTapGestures { onDismiss() } }
            .pointerInput(figure) {
                detectTransformGestures { centroid, move, factor, _ ->
                    val img = image ?: return@detectTransformGestures
                    val next = (zoom * factor).coerceIn(1f, MAX_ZOOM)
                    // Zoom about the fingers: the point under them stays under them.
                    val c = centroid - Offset(size.width / 2f, size.height / 2f)
                    val moved = (pan - c) * (next / zoom) + c + move
                    zoom = next
                    val shown = fitted(img, size) * zoom
                    val maxX = max(0f, (img.width * shown - size.width) / 2f)
                    val maxY = max(0f, (img.height * shown - size.height) / 2f)
                    pan = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
                }
            }
            .drawBehind {
                drawRect(paper)
                val img = image ?: return@drawBehind
                val k = fitted(img, IntSize(size.width.toInt(), size.height.toInt())) * zoom
                val w = (img.width * k).roundToInt()
                val h = (img.height * k).roundToInt()
                drawImage(
                    img,
                    dstOffset = IntOffset(
                        ((size.width - w) / 2f + pan.x).roundToInt(),
                        ((size.height - h) / 2f + pan.y).roundToInt()
                    ),
                    dstSize = IntSize(w, h),
                    filterQuality = FilterQuality.High
                )
            }
    ) {
        FlowFolioPalette(dark = LocalTone.current.dark) {
            Text(
                stringResource(R.string.reader_island_zoom_hint),
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)
            )
        }
    }
}

/** The figure's image: an island at its own size, or the picture decoded for this screen; null until loaded. */
@Composable
private fun rememberFigure(figure: Figure, book: ReaderViewModel): State<ImageBitmap?> {
    val side = with(LocalWindowInfo.current.containerSize) { maxOf(width, height) }
    return produceState(null, figure) {
        value = when (figure) {
            is Figure.Island -> book.islandImage(figure.id)
            is Figure.Picture -> book.picture(figure.path, side)
        }
    }
}

/** The scale that fits [img] inside [area] with a margin round it: up for a small island, down for a big one. */
private fun fitted(img: ImageBitmap, area: IntSize): Float =
    minOf(area.width * FIT_WIDTH / img.width, area.height * FIT_HEIGHT / img.height)

private const val MAX_ZOOM = 4f
private const val FIT_WIDTH = 0.94f
private const val FIT_HEIGHT = 0.8f
