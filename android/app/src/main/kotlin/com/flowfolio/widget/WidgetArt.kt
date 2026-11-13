// Drawing in coordinates: the numbers are the picture (a cover's proportions, a play triangle's corners).
@file:Suppress("MagicNumber", "LongParameterList", "LongMethod")

package com.flowfolio.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.R as DesignR
import com.flowfolio.core.designsystem.androidPath
import com.flowfolio.core.designsystem.isFilled
import com.flowfolio.core.designsystem.strokeUnits

/**
 * The widgets' pictures (owner, 2026-11-12: "exactly like it is in the app"). A home-screen widget is drawn by the
 * launcher from RemoteViews, which can use neither the app's fonts nor Compose, so the book, the words and the glyphs
 * are drawn here into bitmaps:
 * - the book as the library draws it ([com.flowfolio.core.designsystem.BookCover]): 2:3, the spine's lit edge and
 *   shade, the sheen, rounded corners and a soft shadow;
 * - text in Fraunces and Instrument Sans, in white, which the widget tints with the theme's day and night colours.
 */
internal class WidgetArt(private val context: Context) {
    /** Pixels per dp, capped: RemoteViews carries every bitmap in one parcel. */
    private val scale = context.resources.displayMetrics.density.coerceAtMost(MAX_SCALE)

    private fun px(dp: Dp): Int = (dp.value * scale).toInt().coerceAtLeast(1)

    /** A picture and the size it should be shown at. */
    class Art(val bitmap: Bitmap, val width: Dp, val height: Dp)

    private fun art(bitmap: Bitmap) = Art(bitmap, (bitmap.width / scale).dp, (bitmap.height / scale).dp)

    enum class Face { DISPLAY, UI }

    private fun typeface(face: Face): Typeface? = runCatching {
        ResourcesCompat.getFont(
            context,
            if (face ==
                Face.DISPLAY
            ) {
                DesignR.font.fraunces
            } else {
                DesignR.font.instrument_sans
            }
        )
    }.getOrNull()

    /**
     * [text] set in [face] at [sizeSp] and [weight], [width] wide, at most [maxLines] lines with an ellipsis; white,
     * for the widget to tint. [tracking] spaces letters (the eyebrows' small capitals).
     */
    fun text(
        text: String,
        face: Face,
        sizeSp: Float,
        width: Dp,
        maxLines: Int = 1,
        weight: Int = REGULAR,
        tracking: Float = 0f
    ): Art {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = typeface(face)
            textSize = sizeSp * scale * context.resources.configuration.fontScale.coerceIn(1f, MAX_FONT_SCALE)
            letterSpacing = tracking
            fontVariationSettings = "'wght' $weight"
        }
        val w = px(width)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .setLineSpacing(0f, LINE_SPACING)
            .build()
        val usedWidth = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }.toInt().coerceIn(1, w)
        val bitmap = Bitmap.createBitmap(usedWidth, layout.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        layout.draw(Canvas(bitmap))
        return art(bitmap)
    }

    /** The book, [width] wide and half as tall again, with room around it for its shadow. */
    fun book(cover: Bitmap?, title: String, width: Dp): Art {
        val w = px(width)
        val h = w * 3 / 2
        val pad = (w * SHADOW_SHARE).toInt()
        val bitmap = Bitmap.createBitmap(w + pad * 2, h + pad * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val r = RectF(pad.toFloat(), pad * SHADOW_LIFT, (pad + w).toFloat(), pad * SHADOW_LIFT + h)
        val spine = w * SPINE_CORNER
        val edge = w * EDGE_CORNER
        val shape = Path().apply {
            addRoundRect(r, floatArrayOf(spine, spine, edge, edge, edge, edge, spine, spine), Path.Direction.CW)
        }
        // The soft drop shadow, as the library's `softShadow` (blur 22, offset 14, spread −12).
        canvas.drawPath(
            shape,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.TRANSPARENT
                setShadowLayer(pad * 0.8f, 0f, pad * 0.45f, Color.argb(SHADOW_ALPHA, 0, 0, 0))
            }
        )
        canvas.save()
        canvas.clipPath(shape)
        if (cover != null) {
            val src = cropToAspect(cover, w.toFloat() / h)
            canvas.drawBitmap(cover, src, r, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            plainCover(canvas, r, title)
        }
        // The sheen across, the spine's shade and its lit edge (`BookCover.spineAndSheen`).
        canvas.drawRect(
            r,
            Paint().apply {
                shader = LinearGradient(
                    r.left,
                    r.top + h * SHEEN_TILT,
                    r.right,
                    r.bottom - h * SHEEN_TILT,
                    intArrayOf(Color.argb(36, 255, 255, 255), 0, 0, Color.argb(31, 0, 0, 0)),
                    floatArrayOf(0f, 0.38f, 0.7f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )
        val lit = w * LIT_SHARE
        val shade = w * SHADE_SHARE
        canvas.drawRect(
            r.left + lit,
            r.top,
            r.left + lit + shade,
            r.bottom,
            Paint().apply {
                shader = LinearGradient(
                    r.left + lit,
                    0f,
                    r.left + lit + shade,
                    0f,
                    Color.argb(71, 0, 0, 0),
                    0,
                    Shader.TileMode.CLAMP
                )
            }
        )
        canvas.drawRect(r.left, r.top, r.left + lit, r.bottom, Paint().apply { color = Color.argb(41, 255, 255, 255) })
        canvas.restore()
        return art(bitmap)
    }

    /** A cover for a book without one: the accent, with its title. */
    private fun plainCover(canvas: Canvas, r: RectF, title: String) {
        canvas.drawRect(r, Paint().apply { color = PLAIN_COVER })
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = typeface(Face.DISPLAY)
            textSize = r.width() * 0.14f
            fontVariationSettings = "'wght' 600"
        }
        val inset = r.width() * 0.12f
        val layout = StaticLayout.Builder.obtain(title, 0, title.length, paint, (r.width() - inset * 2).toInt())
            .setMaxLines(5).setEllipsize(TextUtils.TruncateAt.END).build()
        canvas.save()
        canvas.translate(r.left + inset, r.top + inset * 1.5f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun cropToAspect(b: Bitmap, aspect: Float): Rect {
        val own = b.width.toFloat() / b.height
        return if (own > aspect) {
            val w = (b.height * aspect).toInt()
            Rect((b.width - w) / 2, 0, (b.width + w) / 2, b.height)
        } else {
            val h = (b.width / aspect).toInt()
            Rect(0, 0, b.width, h.coerceAtMost(b.height))
        }
    }

    /** [glyph] in white, [size] square, as the app draws it (stroked or filled). */
    fun glyph(glyph: Glyph, size: Dp, strokeBoost: Float = 1f): Art {
        val s = px(size)
        val bitmap = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val path = glyph.androidPath().apply { transform(Matrix().apply { setScale(s / GLYPH_BOX, s / GLYPH_BOX) }) }
        Canvas(bitmap).drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = if (glyph.isFilled) Paint.Style.FILL else Paint.Style.STROKE
                strokeWidth = glyph.strokeUnits * strokeBoost * s / GLYPH_BOX
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        )
        return art(bitmap)
    }

    /** A player button's symbol, bold and filled like a music player's: play, pause, back or forward. */
    fun control(kind: Control, size: Dp): Art {
        val s = px(size).toFloat()
        val bitmap = Bitmap.createBitmap(s.toInt(), s.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            pathEffect = android.graphics.CornerPathEffect(s * 0.06f)
        }
        val u = s / CONTROL_BOX
        fun tri(x0: Float, x1: Float, top: Float, bottom: Float) = Path().apply {
            moveTo(x0 * u, top * u)
            lineTo(x1 * u, (top + bottom) / 2 * u)
            lineTo(x0 * u, bottom * u)
            close()
        }
        when (kind) {
            Control.PLAY -> c.drawPath(tri(7f, 20f, 4f, 20f), paint)

            Control.PAUSE -> {
                c.drawRoundRect(RectF(5.5f * u, 4f * u, 10f * u, 20f * u), u, u, paint)
                c.drawRoundRect(RectF(14f * u, 4f * u, 18.5f * u, 20f * u), u, u, paint)
            }

            Control.NEXT -> {
                c.drawPath(tri(4f, 16f, 5f, 19f), paint)
                c.drawRoundRect(RectF(16.5f * u, 5f * u, 20f * u, 19f * u), u, u, paint)
            }

            Control.PREVIOUS -> {
                c.save()
                c.scale(-1f, 1f, s / 2, s / 2)
                c.drawPath(tri(4f, 16f, 5f, 19f), paint)
                c.drawRoundRect(RectF(16.5f * u, 5f * u, 20f * u, 19f * u), u, u, paint)
                c.restore()
            }
        }
        return art(bitmap)
    }

    enum class Control { PLAY, PAUSE, NEXT, PREVIOUS }

    companion object {
        const val REGULAR = 400
        const val MEDIUM = 500
        const val SEMIBOLD = 600
        const val BOLD = 700
        private const val MAX_SCALE = 2.75f
        private const val MAX_FONT_SCALE = 1.3f
        private const val LINE_SPACING = 1.04f
        private const val SHADOW_SHARE = 0.14f
        private const val SHADOW_LIFT = 0.55f
        private const val SHADOW_ALPHA = 105
        private const val SPINE_CORNER = 0.035f
        private const val EDGE_CORNER = 0.07f
        private const val SHEEN_TILT = 0.2f
        private const val LIT_SHARE = 0.026f
        private const val SHADE_SHARE = 0.08f
        private const val GLYPH_BOX = 24f
        private const val CONTROL_BOX = 24f
        private val PLAIN_COVER = Color.rgb(0xC2, 0x41, 0x0C)
    }
}
