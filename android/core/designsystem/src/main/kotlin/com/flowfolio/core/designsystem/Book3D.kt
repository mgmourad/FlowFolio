// Values here are the mock's CSS copied verbatim (shadow offsets, gradient stops, angles), each cited beside its
// use; naming every one would hide the correspondence with docs/design/FlowFolio_Mock.html (ADR-0015).
@file:Suppress("MagicNumber")

package com.flowfolio.core.designsystem

import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** What a 3D book shows. [cover] is the rendered first page; without it a cover is drawn from [colors] and [title]. */
@Immutable
data class BookFace(
    val title: String,
    val pageCount: Int,
    val cover: ImageBitmap? = null,
    val colors: BookColors = BookColors.Default
)

/** The mock's per-book colours: a cover gradient [start] to [end] and the [ink] used on it and the spine. */
@Immutable
data class BookColors(val start: Color, val end: Color, val ink: Color) {
    companion object {
        val Default = BookColors(Color(0xFF2747B8), Color(0xFF0B1640), Color(0xFFFFD27A))
    }
}

/**
 * The mock's 3D book (`.book-wrap`): a cover in perspective with its spine showing, page edges, and a soft contact
 * shadow. It rests at `rotateY(26deg)` and, while pressed (or when [lifted]), turns to 8 degrees and rises 10 dp.
 * Geometry is the mock's CSS projected exactly (perspective 1000), with spine depth from the real page count.
 */
@Composable
fun Book3D(
    face: BookFace,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    lifted: Boolean = false,
    restAngle: Float = REST_ANGLE,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val up = lifted || pressed
    val anim = tween<Float>(Motion.BOOK_MS, easing = Motion.EaseOut)
    val angle by animateFloatAsState(if (up) LIFT_ANGLE else restAngle, anim, label = "bookAngle")
    val rise by animateFloatAsState(if (up) 1f else 0f, anim, label = "bookRise")
    val display =
        remember(context) { runCatching { context.resources.getFont(R.font.fraunces) }.getOrDefault(Typeface.SERIF) }
    val clickable = if (onClick != null) {
        Modifier.clickable(source, indication = null, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Canvas(modifier.size(width, height).then(clickable)) {
        val w = size.width
        val h = size.height
        val depth = max(MIN_DEPTH.dp.toPx(), bookDepth(face.pageCount) * w / DEPTH_REFERENCE)
        val lift = rise * LIFT.dp.toPx()
        drawContactShadow(w, h, rise)
        val projector = Projector(w, h, depth, angle, PERSPECTIVE.dp.toPx(), lift)
        drawIntoCanvas { c ->
            val canvas = c.nativeCanvas
            drawSpine(canvas, projector, face, display, MAX_SPINE_TEXT.dp.toPx())
            drawCover(canvas, projector, face, display)
        }
    }
}

/** Spine depth in CSS px for [pageCount] pages (the mock's `Math.min(28, 9 + pages/62)`). */
fun bookDepth(pageCount: Int): Float = min(MAX_DEPTH, BASE_DEPTH + pageCount / PAGES_PER_PX)

/** Projects points of the book box (centred, x right, y down, z toward the viewer) onto the canvas. */
private class Projector(
    val w: Float,
    val h: Float,
    val d: Float,
    angleDeg: Float,
    val perspective: Float,
    val lift: Float
) {
    private val cosA = cos(Math.toRadians(angleDeg.toDouble())).toFloat()
    private val sinA = sin(Math.toRadians(angleDeg.toDouble())).toFloat()

    /** CSS `rotateY` then perspective from the element's centre. */
    fun project(x: Float, y: Float, z: Float): FloatArray {
        val xr = x * cosA + z * sinA
        val zr = -x * sinA + z * cosA
        val scale = perspective / (perspective - zr)
        return floatArrayOf(w / 2f + xr * scale, h / 2f + (y - lift) * scale)
    }

    /**
     * A matrix mapping the rectangle (0,0)-(srcW,srcH) onto a projected quad; [corners] are its top-left, top-right,
     * bottom-right and bottom-left points, in that order.
     */
    fun quad(srcW: Float, srcH: Float, vararg corners: FloatArray): Matrix {
        val src = floatArrayOf(0f, 0f, srcW, 0f, srcW, srcH, 0f, srcH)
        val dst = corners.flatMap { listOf(it[0], it[1]) }.toFloatArray()
        return Matrix().apply { setPolyToPoly(src, 0, dst, 0, CORNERS) }
    }
}

private fun DrawScope.drawContactShadow(w: Float, h: Float, rise: Float) {
    // `.book-wrap::after`: an ellipse under the book, smaller and fainter while lifted.
    val shrink = 1f - rise * (1f - LIFTED_SHADOW_SCALE)
    val left = w * 0.08f
    val right = w * 1.06f
    val cx = (left + right) / 2f
    val half = (right - left) / 2f * shrink
    val top = h + 2.dp.toPx()
    blurredShape(
        Color.Black.copy(alpha = 0.55f * (1f - rise * (1f - LIFTED_SHADOW_ALPHA))),
        6.dp.toPx(),
        BlurShape.Oval,
        Offset(cx - half, top),
        Size(2 * half, 16.dp.toPx())
    )
}

private fun drawCover(canvas: android.graphics.Canvas, p: Projector, face: BookFace, display: Typeface) {
    val hw = p.w / 2f
    val hh = p.h / 2f
    val z = p.d / 2f
    val m = p.quad(p.w, p.h, p.project(-hw, -hh, z), p.project(hw, -hh, z), p.project(hw, hh, z), p.project(-hw, hh, z))
    canvas.save()
    canvas.concat(m)
    // `border-radius: 2px 6px 6px 2px`.
    val r = p.w * 0.04f
    val clip = android.graphics.Path().apply {
        addRoundRect(
            RectF(0f, 0f, p.w, p.h),
            floatArrayOf(r / 3, r / 3, r, r, r, r, r / 3, r / 3),
            android.graphics.Path.Direction.CW
        )
    }
    canvas.clipPath(clip)
    val cover = face.cover?.asAndroidBitmap()
    if (cover != null && !cover.isRecycled) {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(cover, null, RectF(0f, 0f, p.w, p.h), paint)
    } else {
        drawGeneratedCover(canvas, p.w, p.h, face, display)
    }
    // `.cv::after`: the binding crease on the left and a diagonal gloss.
    val crease = Paint().apply {
        shader = LinearGradient(
            0f,
            0f,
            p.w * 0.09f,
            0f,
            intArrayOf(0x61000000, 0x0D000000, 0x38FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.44f, 0.61f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, p.w * 0.09f, p.h, crease)
    val gloss = Paint().apply {
        shader = LinearGradient(
            0f,
            0f,
            p.w,
            p.h,
            intArrayOf(0x40FFFFFF, 0x00FFFFFF, 0x00000000, 0x33000000),
            floatArrayOf(0f, 0.4f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, p.w, p.h, gloss)
    canvas.restore()
}

private fun drawSpine(
    canvas: android.graphics.Canvas,
    p: Projector,
    face: BookFace,
    display: Typeface,
    maxText: Float
) {
    val hw = p.w / 2f
    val hh = p.h / 2f
    val z = p.d / 2f
    // The left face: from the back edge (z = -d/2) to the front edge (z = +d/2).
    val m = p.quad(
        p.d,
        p.h,
        p.project(-hw, -hh, -z),
        p.project(-hw, -hh, z),
        p.project(-hw, hh, z),
        p.project(-hw, hh, -z)
    )
    canvas.save()
    canvas.concat(m)
    val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = face.colors.end.toArgb() }
    canvas.drawRect(0f, 0f, p.d, p.h, base)
    // `linear-gradient(90deg, rgba(0,0,0,.4), rgba(255,255,255,.1) 45%, rgba(0,0,0,.3))`.
    val shade = Paint().apply {
        shader = LinearGradient(
            0f,
            0f,
            p.d,
            0f,
            intArrayOf(0x66000000, 0x1AFFFFFF, 0x4D000000),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, p.d, p.h, shade)
    // The title, written bottom to top like a real spine.
    val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(display, SPINE_WEIGHT, false)
        color = face.colors.ink.toArgb()
        textSize = min(p.d * SPINE_TEXT_RATIO, maxText)
    }
    val label = TextUtilsEllipsize(face.title, text, p.h * 0.86f)
    canvas.save()
    canvas.translate(p.d / 2f + text.textSize * 0.35f, p.h / 2f + text.measureText(label) / 2f)
    canvas.rotate(-90f)
    canvas.drawText(label, 0f, 0f, text)
    canvas.restore()
    canvas.restore()
}

/** The mock's generated cover (`.cv`): a gradient with the title set in Fraunces. */
private fun drawGeneratedCover(canvas: android.graphics.Canvas, w: Float, h: Float, face: BookFace, display: Typeface) {
    val bg = Paint().apply {
        shader =
            LinearGradient(
                0f,
                0f,
                w * 0.42f,
                h,
                face.colors.start.toArgb(),
                face.colors.end.toArgb(),
                Shader.TileMode.CLAMP
            )
    }
    canvas.drawRect(0f, 0f, w, h, bg)
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(display, COVER_WEIGHT, false)
        color = face.colors.ink.toArgb()
        textSize = w * COVER_TITLE_RATIO
    }
    val margin = w * 0.12f
    val layout = StaticLayout.Builder.obtain(face.title, 0, face.title.length, paint, (w - margin * 1.75f).roundToInt())
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, 0.98f)
        .setMaxLines(COVER_MAX_LINES)
        .build()
    canvas.save()
    canvas.translate(margin, h - w * 0.1f - layout.height)
    layout.draw(canvas)
    canvas.restore()
}

/** Shortens [text] with an ellipsis to fit [maxWidth]. */
@Suppress("FunctionName")
private fun TextUtilsEllipsize(text: String, paint: TextPaint, maxWidth: Float): String =
    android.text.TextUtils.ellipsize(text, paint, maxWidth, android.text.TextUtils.TruncateAt.END).toString()

private const val REST_ANGLE = 26f
private const val LIFT_ANGLE = 8f
private const val LIFT = 10f
private const val PERSPECTIVE = 1000f
private const val MIN_DEPTH = 8f
private const val MAX_DEPTH = 28f
private const val BASE_DEPTH = 9f
private const val PAGES_PER_PX = 62f
private const val DEPTH_REFERENCE = 100f
private const val CORNERS = 4
private const val LIFTED_SHADOW_SCALE = 0.88f
private const val LIFTED_SHADOW_ALPHA = 0.7f
private const val SPINE_WEIGHT = 650
private const val COVER_WEIGHT = 720
private const val SPINE_TEXT_RATIO = 0.42f
private const val MAX_SPINE_TEXT = 11f
private const val COVER_TITLE_RATIO = 0.14f
private const val COVER_MAX_LINES = 4
