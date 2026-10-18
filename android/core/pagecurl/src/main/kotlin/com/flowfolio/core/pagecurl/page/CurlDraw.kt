/*
 * Vendored from oleksandrbalan/pagecurl 1.5.1 (https://github.com/oleksandrbalan/pagecurl), by Oleksandr Balan.
 * Licensed under the Apache License, Version 2.0 (see core/pagecurl/LICENSE). Modified by The FlowFolio Authors
 * (ADR-0011): package renamed, experimental opt-in removed, book-spread back pages added. See core/pagecurl/NOTICE.
 */
package com.flowfolio.core.pagecurl.page

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.flowfolio.core.pagecurl.config.PageCurlConfig
import com.flowfolio.core.pagecurl.utils.Polygon
import com.flowfolio.core.pagecurl.utils.lineLineIntersection
import com.flowfolio.core.pagecurl.utils.rotate
import java.lang.Float.max
import kotlin.math.atan2

internal fun Modifier.drawCurl(
    config: PageCurlConfig,
    posA: Offset,
    posB: Offset,
    backPage: ImageBitmap? = null,
    gradientShadow: Boolean = false,
): Modifier = drawWithCache {
    // Fast-check if curl is in left most position (gesture is fully completed)
    // In such case do not bother and draw nothing
    if (posA == size.toRect().topLeft && posB == size.toRect().bottomLeft) {
        return@drawWithCache drawNothing()
    }

    // Fast-check if curl is in right most position (gesture is not yet started)
    // In such case do not bother and draw the full content
    if (posA == size.toRect().topRight && posB == size.toRect().bottomRight) {
        return@drawWithCache drawOnlyContent()
    }

    // Find the intersection of the curl line ([posA, posB]) and top and bottom sides, so that we may clip and mirror
    // content correctly
    val topIntersection = lineLineIntersection(
        Offset(0f, 0f), Offset(size.width, 0f),
        posA, posB
    )
    val bottomIntersection = lineLineIntersection(
        Offset(0f, size.height), Offset(size.width, size.height),
        posA, posB
    )

    // Should not really happen, but in case there is not intersection (curl line is horizontal), just draw the full
    // content instead
    if (topIntersection == null || bottomIntersection == null) {
        return@drawWithCache drawOnlyContent()
    }

    // Limit x coordinates of both intersections to be at least 0, so that page do not look like teared from the book
    val topCurlOffset = Offset(max(0f, topIntersection.x), topIntersection.y)
    val bottomCurlOffset = Offset(max(0f, bottomIntersection.x), bottomIntersection.y)

    // That is the easy part, prepare a lambda to draw the content clipped by the curl line
    val drawClippedContent = prepareClippedContent(topCurlOffset, bottomCurlOffset)
    // That is the tricky part, prepare a lambda to draw the back-page with the shadow
    val drawCurl = prepareCurl(config, topCurlOffset, bottomCurlOffset, backPage, blurShadow = !gradientShadow)
    val drawFoldShading = if (gradientShadow) prepareFoldShading(config, topCurlOffset, bottomCurlOffset) else null

    onDrawWithContent {
        drawClippedContent()
        drawCurl()
        drawFoldShading?.invoke(this)
    }
}

/**
 * The simple method to draw the whole unmodified content.
 */
private fun CacheDrawScope.drawOnlyContent(): DrawResult =
    onDrawWithContent {
        drawContent()
    }

/**
 * The simple method to draw nothing.
 */
private fun CacheDrawScope.drawNothing(): DrawResult =
    onDrawWithContent {
        /* Empty */
    }

private fun CacheDrawScope.prepareClippedContent(
    topCurlOffset: Offset,
    bottomCurlOffset: Offset,
): ContentDrawScope.() -> Unit {
    // Make a quadrilateral from the left side to the intersection points
    val path = Path()
    path.lineTo(topCurlOffset.x, topCurlOffset.y)
    path.lineTo(bottomCurlOffset.x, bottomCurlOffset.y)
    path.lineTo(0f, size.height)
    return result@{
        // Draw a content clipped by the constructed path
        clipPath(path) {
            this@result.drawContent()
        }
    }
}

private fun CacheDrawScope.prepareCurl(
    config: PageCurlConfig,
    topCurlOffset: Offset,
    bottomCurlOffset: Offset,
    backPage: ImageBitmap?,
    blurShadow: Boolean,
): ContentDrawScope.() -> Unit {
    // Build a quadrilateral of the part of the page which should be mirrored as the back-page
    // In all cases polygon should have 4 points, even when back-page is only a small "corner" (with 3 points) due to
    // the shadow rendering, otherwise it will create a visual artifact when switching between 3 and 4 points polygon
    val polygon = Polygon(
        sequence {
            // Find the intersection of the curl line and right side
            // If intersection is found adds to the polygon points list
            suspend fun SequenceScope<Offset>.yieldEndSideInterception() {
                val offset = lineLineIntersection(
                    topCurlOffset, bottomCurlOffset,
                    Offset(size.width, 0f), Offset(size.width, size.height)
                ) ?: return
                yield(offset)
                yield(offset)
            }

            // In case top intersection lays in the bounds of the page curl, take 2 points from the top side, otherwise
            // take the interception with a right side
            if (topCurlOffset.x < size.width) {
                yield(topCurlOffset)
                yield(Offset(size.width, topCurlOffset.y))
            } else {
                yieldEndSideInterception()
            }

            // In case bottom intersection lays in the bounds of the page curl, take 2 points from the bottom side,
            // otherwise take the interception with a right side
            if (bottomCurlOffset.x < size.width) {
                yield(Offset(size.width, size.height))
                yield(bottomCurlOffset)
            } else {
                yieldEndSideInterception()
            }
        }.toList()
    )

    // Calculate the angle in radians between X axis and the curl line, this is used to rotate mirrored content to the
    // right position of the curled back-page
    val lineVector = topCurlOffset - bottomCurlOffset
    val angle = Math.PI.toFloat() - atan2(lineVector.y, lineVector.x) * 2

    // Prepare a lambda to draw the shadow of the back-page
    val drawShadow = if (blurShadow) prepareShadow(config, polygon, angle) else ({ })

    return result@{
        withTransform({
            // Mirror in X axis the drawing as back-page should be mirrored
            scale(-1f, 1f, pivot = bottomCurlOffset)
            // Rotate the drawing according to the curl line
            rotateRad(angle, pivot = bottomCurlOffset)
        }) {
            // Draw shadow first
            this@result.drawShadow()

            // And finally draw the back-page with an overlay with alpha
            clipPath(polygon.toPath()) {
                if (backPage != null) {
                    // FlowFolio book spread: the sheet's back is the next spread's left page, laid out as it will
                    // rest in the left pane (against the spine, vertically centred). Pre-mirror it so that the curl's
                    // own mirror transform shows it the right way round once the sheet lands.
                    drawRect(config.backPageColor)
                    withTransform({ scale(-1f, 1f, pivot = Offset(size.width / 2f, 0f)) }) {
                        val topLeft = Offset(size.width - backPage.width, (size.height - backPage.height) / 2f)
                        val flip = if (config.flipBackPage) -1f else 1f
                        val centre = Offset(topLeft.x + backPage.width / 2f, 0f)
                        withTransform({ scale(flip, 1f, pivot = centre) }) { drawImage(backPage, topLeft = topLeft) }
                        // The left page's own gutter shadow at its binding edge, so nothing pops in when it lands.
                        val gutter = size.width * BACK_GUTTER_FRACTION
                        drawRect(
                            Brush.horizontalGradient(
                                colorStops = *softStops(config.shadowColor, BACK_GUTTER_ALPHA),
                                startX = size.width,
                                endX = size.width - gutter,
                            ),
                            topLeft = Offset(size.width - gutter, 0f),
                            size = androidx.compose.ui.geometry.Size(gutter, size.height),
                        )
                    }
                } else {
                    this@result.drawContent()

                    val overlayAlpha = 1f - config.backPageContentAlpha
                    drawRect(config.backPageColor.copy(alpha = overlayAlpha))
                }
            }
        }
    }
}

/**
 * FlowFolio: cheap shading along the fold instead of the blurred shadow layer. The blur renders through an offscreen
 * GPU layer; on the reference Fold turning the shadow off saved 36-40 MB of graphics memory. Two linear gradients
 * approximate the look: a shadow cast onto the revealed page next to the fold, and shading on the folded sheet.
 */
private fun CacheDrawScope.prepareFoldShading(
    config: PageCurlConfig,
    top: Offset,
    bottom: Offset,
): ContentDrawScope.() -> Unit {
    val direction = top - bottom
    val length = direction.getDistance()
    if (length == 0f || config.shadowAlpha == 0f) return { }
    // Unit normal to the fold, pointing towards the revealed page (the side the sheet lifted from).
    var normal = Offset(-direction.y / length, direction.x / length)
    if (normal.x < 0f) normal = -normal
    val castWidth = config.shadowRadius.toPx() * CAST_WIDTH_FACTOR
    val curveWidth = config.shadowRadius.toPx() * CURVE_WIDTH_FACTOR
    val middle = (top + bottom) / 2f
    val color = config.shadowColor
    val alpha = (config.shadowAlpha * CAST_ALPHA_FACTOR).coerceAtMost(MAX_ALPHA)

    // Shadow the lifted sheet casts on the page beneath: dispersed, soft falloff away from the fold.
    val cast = band(top, bottom, normal * castWidth)
    val castBrush = Brush.linearGradient(*softStops(color, alpha), start = middle, end = middle + normal * castWidth)
    // The folded sheet as a curved surface: darker right at the fold, then a soft highlight where it faces the light.
    val curve = band(top, bottom, -normal * curveWidth)
    val curveBrush = Brush.linearGradient(
        colorStops = *arrayOf(
            0f to color.copy(alpha = alpha * CURVE_ALPHA_FACTOR),
            0.35f to color.copy(alpha = alpha * CURVE_ALPHA_FACTOR * 0.35f),
            0.6f to Color.White.copy(alpha = HIGHLIGHT_ALPHA),
            1f to Color.White.copy(alpha = 0f),
        ),
        start = middle,
        end = middle - normal * curveWidth,
    )
    return {
        drawPath(cast, castBrush)
        drawPath(curve, curveBrush)
    }
}

/** Colour stops approximating a Gaussian falloff (reads like a soft blur, without an offscreen blur layer). */
private fun softStops(color: Color, alpha: Float): Array<Pair<Float, Color>> =
    Array(SOFT_STOPS) { i ->
        val t = i / (SOFT_STOPS - 1f)
        t to color.copy(alpha = alpha * kotlin.math.exp(-SOFT_FALLOFF * t * t) * (1f - t))
    }

private fun band(top: Offset, bottom: Offset, extent: Offset): Path = Path().apply {
    moveTo(top.x, top.y)
    lineTo(bottom.x, bottom.y)
    lineTo(bottom.x + extent.x, bottom.y + extent.y)
    lineTo(top.x + extent.x, top.y + extent.y)
    close()
}

private const val CAST_WIDTH_FACTOR = 3.5f
private const val CURVE_WIDTH_FACTOR = 2.2f
private const val CAST_ALPHA_FACTOR = 2.0f
private const val CURVE_ALPHA_FACTOR = 0.6f
private const val MAX_ALPHA = 0.55f
private const val HIGHLIGHT_ALPHA = 0.12f
private const val SOFT_STOPS = 8
private const val SOFT_FALLOFF = 4.5f
private const val BACK_GUTTER_FRACTION = 0.085f
private const val BACK_GUTTER_ALPHA = 0.30f

private fun CacheDrawScope.prepareShadow(
    config: PageCurlConfig,
    polygon: Polygon,
    angle: Float
): ContentDrawScope.() -> Unit {
    // Quick exit if no shadow is requested
    if (config.shadowAlpha == 0f || config.shadowRadius == 0.dp) {
        return { /* No shadow is requested */ }
    }

    // Prepare shadow parameters
    val radius = config.shadowRadius.toPx()
    val shadowColor = config.shadowColor.copy(alpha = config.shadowAlpha).toArgb()
    val transparent = config.shadowColor.copy(alpha = 0f).toArgb()
    val shadowOffset = Offset(-config.shadowOffset.x.toPx(), config.shadowOffset.y.toPx())
        .rotate(2 * Math.PI.toFloat() - angle)

    // Prepare shadow paint with a shadow layer
    val paint = Paint().apply {
        val frameworkPaint = asFrameworkPaint()
        frameworkPaint.color = transparent
        frameworkPaint.setShadowLayer(
            config.shadowRadius.toPx(),
            shadowOffset.x,
            shadowOffset.y,
            shadowColor
        )
    }

    // Hardware acceleration supports setShadowLayer() only on API 28 and above, thus to support previous API versions
    // draw a shadow to the bitmap instead
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        prepareShadowApi28(radius, paint, polygon)
    } else {
        prepareShadowImage(radius, paint, polygon)
    }
}

private fun prepareShadowApi28(
    radius: Float,
    paint: Paint,
    polygon: Polygon,
): ContentDrawScope.() -> Unit = {
    drawIntoCanvas {
        it.nativeCanvas.drawPath(
            polygon
                .offset(radius).toPath()
                .asAndroidPath(),
            paint.asFrameworkPaint()
        )
    }
}

private fun CacheDrawScope.prepareShadowImage(
    radius: Float,
    paint: Paint,
    polygon: Polygon,
): ContentDrawScope.() -> Unit {
    // Increase the size a little bit so that shadow is not clipped
    val bitmap = Bitmap.createBitmap(
        (size.width + radius * 4).toInt(),
        (size.height + radius * 4).toInt(),
        Bitmap.Config.ARGB_8888
    )
    Canvas(bitmap).apply {
        drawPath(
            polygon
                // As bitmap size is increased we should translate the polygon so that shadow remains in center
                .translate(Offset(2 * radius, 2 * radius))
                .offset(radius).toPath()
                .asAndroidPath(),
            paint.asFrameworkPaint()
        )
    }

    return {
        drawIntoCanvas {
            // As bitmap size is increased we should shift the drawing so that shadow remains in center
            it.nativeCanvas.drawBitmap(bitmap, -2 * radius, -2 * radius, null)
        }
    }
}
