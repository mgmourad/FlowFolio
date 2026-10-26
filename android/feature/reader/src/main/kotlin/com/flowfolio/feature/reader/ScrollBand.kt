package com.flowfolio.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity

/**
 * One EPUB page in Scroll's continuous column: only its band of text ([ScrollBands]), so that its lines run straight
 * on from the page above with no page margins between them. The page image is the usual one, drawn moved up.
 */
@Composable
internal fun ScrollBand(page: Int, image: ImageBitmap?, bands: ScrollBands) {
    val height = with(LocalDensity.current) { bands.heights.getOrElse(page) { 1 }.toDp() }
    val paper = PAPER
    val mirrored = LocalMirroredPages.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds()
            .reportBandBounds(page, bands.top, image)
            .drawBehind {
                drawRect(paper)
                if (image != null) {
                    scale(if (mirrored) -1f else 1f, 1f) {
                        drawImage(image, Offset((size.width - image.width) / 2f, -bands.top.toFloat()))
                    }
                }
            }
    )
}

/**
 * Reports the whole page's place on screen, not just its band's, since links and selection are given as fractions
 * of the whole page: the page starts [top] pixels above its band.
 */
@Composable
private fun Modifier.reportBandBounds(page: Int, top: Int, image: ImageBitmap?): Modifier {
    val bounds = LocalPageBounds.current
    if (bounds == null || image == null) return this
    DisposableEffect(bounds, page) { onDispose { bounds.remove(page) } }
    return onGloballyPositioned {
        val at = it.positionInRoot()
        val left = at.x + (it.size.width - image.width) / 2f
        bounds.put(page, Rect(left, at.y - top, left + image.width, at.y - top + image.height))
    }
}
