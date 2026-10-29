package com.flowfolio.core.reflow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan
import android.text.style.UpdateAppearance

/** A link in the text; [href] is resolved in the book (`OEBPS/ch02.xhtml#fn3`) or absolute (`https://...`). */
class LinkSpan(val href: String) :
    CharacterStyle(),
    UpdateAppearance {
    override fun updateDrawState(tp: TextPaint) {
        tp.isUnderlineText = true
    }
}

/**
 * An inline island (an equation in running text). Until the WebView has measured it, it takes a few ems of space; once
 * measured it has its real size and sits on the baseline, and once rendered it draws its image.
 */
class IslandSpan(val id: Int, val kind: String, xml: String) : ReplacementSpan() {
    /** The markup, kept packed (M3 EPUB memory): read only when the WebView lays the island out. */
    internal val packed = PackedText(xml)
    val xml: String get() = packed.text()

    /** Measured size in pixels and the baseline from its top; 0 until measured. */
    @Volatile var width = 0

    @Volatile var ascent = 0

    @Volatile var descent = 0

    @Volatile var image: Bitmap? = null

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null && ascent > 0) {
            fm.ascent = minOf(fm.ascent, -ascent)
            fm.top = fm.ascent
            fm.descent = maxOf(fm.descent, descent)
            fm.bottom = fm.descent
        }
        return if (width > 0) width else (paint.textSize * PLACEHOLDER_EMS).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        // Into its box, which is smaller than the image when the island was scaled down to fit the column.
        val bmp = image ?: return
        dst.set(x, (y - ascent).toFloat(), x + width, (y + descent).toFloat())
        canvas.drawBitmap(bmp, null, dst, filter)
    }

    private val filter = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    private companion object {
        const val PLACEHOLDER_EMS = 3f
    }
}

/**
 * An image set in the line of text (a converted book's equation drawn inline, `<img class="ff-inline">`): sized in ems
 * of the text, so lines are laid out without reading the image; [image] is loaded before its page is drawn.
 */
class InlineImageSpan(val path: String, val heightEm: Float, val widthEm: Float, val dropEm: Float) :
    ReplacementSpan() {
    @Volatile var image: Bitmap? = null

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val size = paint.textSize
        if (fm != null) {
            val ascent = ((heightEm - dropEm) * size).toInt()
            val descent = (dropEm * size).toInt()
            fm.ascent = minOf(fm.ascent, -ascent)
            fm.top = minOf(fm.top, fm.ascent)
            fm.descent = maxOf(fm.descent, descent)
            fm.bottom = maxOf(fm.bottom, fm.descent)
        }
        return (widthEm * size).toInt().coerceAtLeast(1)
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val bmp = image?.takeIf { !it.isRecycled } ?: return
        val size = paint.textSize
        val bottomY = y + dropEm * size
        dst.set(x, bottomY - heightEm * size, x + widthEm * size, bottomY)
        // Drawn in the line's own colour, so the equation reads like the words around it on any paper.
        filter.colorFilter = inkFilter(paint.color)
        canvas.drawBitmap(bmp, null, dst, filter)
    }

    private val filter = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
}

/**
 * A superscript ([raised]) or subscript, set smaller and shifted as print does: the shift is taken from the full size,
 * so 10⁷ and v₀ read at the heights a typesetter gives them (Android's own spans keep the full size).
 */
class ScriptSpan(private val raised: Boolean) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) = apply(tp)

    override fun updateMeasureState(tp: TextPaint) = apply(tp)

    private fun apply(tp: TextPaint) {
        val size = tp.textSize
        tp.baselineShift += if (raised) -(size * RAISE).toInt() else (size * LOWER).toInt()
        tp.textSize = size * SCALE
    }

    private companion object {
        const val SCALE = 0.7f
        const val RAISE = 0.35f
        const val LOWER = 0.15f
    }
}

/**
 * Draws line art printed in dark ink on light paper in [ink] instead: each pixel takes [ink]'s colour, and its
 * darkness becomes its opacity, so the paper shows through and anti-aliased edges stay smooth. Transparent pixels
 * stay transparent (the source alpha carries over). One filter per colour, as pages use one or two inks.
 */
fun inkFilter(ink: Int): ColorFilter = inkFilters.getOrPut(ink) {
    val r = Color.red(ink).toFloat()
    val g = Color.green(ink).toFloat()
    val b = Color.blue(ink).toFloat()
    ColorMatrixColorFilter(
        floatArrayOf(
            0f, 0f, 0f, 0f, r,
            0f, 0f, 0f, 0f, g,
            0f, 0f, 0f, 0f, b,
            -LUMA_R, -LUMA_G, -LUMA_B, 1f, 0f
        )
    )
}

private val inkFilters = java.util.concurrent.ConcurrentHashMap<Int, ColorFilter>()
private const val LUMA_R = 0.299f
private const val LUMA_G = 0.587f
private const val LUMA_B = 0.114f
