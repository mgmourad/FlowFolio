package com.flowfolio.core.reflow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.style.CharacterStyle
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
class IslandSpan(val id: Int, val kind: String, val xml: String) : ReplacementSpan() {
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
        image?.let { canvas.drawBitmap(it, x, (y - ascent).toFloat(), null) }
    }

    private companion object {
        const val PLACEHOLDER_EMS = 3f
    }
}
