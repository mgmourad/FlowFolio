package com.flowfolio.core.reflow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Paper and ink for a page (the reader's paper styles). */
data class PageColors(val paper: Int, val ink: Int, val faint: Int)

/**
 * Draws pages. Text uses the [PageColors] ink; images and islands come from [images] and [islands] (null until ready:
 * a faint box takes their place). Layouts are shared between pages and their paints recoloured per draw, so one
 * drawer draws one page at a time.
 */
class PageDrawer(
    private val book: ReflowBook,
    private val images: (Block.Image, Int, Int) -> Bitmap?,
    private val islands: (Int) -> Bitmap?
) {
    /** Where a page goes: the canvas, the top left of its page box, and the layout that made the page. */
    class Target(val canvas: Canvas, val left: Float, val top: Float, val paginator: Paginator)

    private val filter = Paint(Paint.FILTER_BITMAP_FLAG)
    private val inked = Paint(Paint.FILTER_BITMAP_FLAG)
    private val placeholder = Paint()
    private val panel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)

    @Synchronized
    fun draw(target: Target, page: Page, colors: PageColors) {
        val chapter = book.chapters[page.chapter]
        placeholder.color = colors.faint
        inked.colorFilter = inkFilter(colors.ink)
        for (s in page.slices) {
            when (s) {
                is Slice.Lines -> drawLines(target, s, chapter.blocks[s.block] as Block.Text, colors.ink)
                is Slice.Box -> drawBox(target, s, chapter.blocks[s.block])
            }
        }
    }

    private fun drawLines(target: Target, s: Slice.Lines, block: Block.Text, ink: Int) {
        val l = s.layout
        l.paint.color = ink
        val lineTop = l.getLineTop(s.first).toFloat()
        val indent = target.paginator.indentPx(block)
        if (block.style.preformatted) drawPanel(target, s, indent, ink)
        if (block.style.marker != null && s.first == 0) drawMarker(target, s, block, indent, ink)
        with(target.canvas) {
            save()
            translate(target.left + indent, target.top + s.y - lineTop)
            // A paragraph's last line has no line below it: let descenders some fonts draw below their own descent
            // (Charter's p and g) show in full rather than be cut.
            val slack = if (s.last == l.lineCount) l.paint.textSize * DESCENT_SLACK else 0f
            clipRect(0f, lineTop, l.width.toFloat(), l.getLineBottom(s.last - 1) + slack)
            l.draw(this)
            restore()
        }
    }

    /** A code listing's panel behind this slice of it: the ink, faint, with rounded corners. */
    private fun drawPanel(target: Target, s: Slice.Lines, indent: Int, ink: Int) {
        val l = s.layout
        val pad = target.paginator.codePadPx().toFloat()
        val height = (l.getLineBottom(s.last - 1) - l.getLineTop(s.first)).toFloat()
        val top = target.top + s.y
        panel.color = ink
        panel.alpha = PANEL_ALPHA
        val rect = RectF(
            target.left + indent - pad,
            top - pad * PANEL_V,
            target.left + book.spec.width,
            top + height + pad * PANEL_V
        )
        target.canvas.drawRoundRect(rect, pad * PANEL_RADIUS, pad * PANEL_RADIUS, panel)
    }

    /** A list item's bullet or number, ending a little before its first line, on that line's baseline. */
    private fun drawMarker(target: Target, s: Slice.Lines, block: Block.Text, indent: Int, ink: Int) {
        val l = s.layout
        val marker = block.style.marker ?: return
        markerPaint.set(l.paint)
        markerPaint.color = ink
        markerPaint.textAlign = Paint.Align.RIGHT
        val baseline = target.top + s.y + l.getLineBaseline(s.first) - l.getLineTop(s.first)
        val gap = l.paint.textSize * MARKER_GAP_EMS
        target.canvas.drawText(marker, target.left + indent - gap, baseline.toFloat(), markerPaint)
    }

    private companion object {
        const val DESCENT_SLACK = 0.3f
        const val PANEL_ALPHA = 18
        const val PANEL_V = 0.7f
        const val PANEL_RADIUS = 0.8f
        const val MARKER_GAP_EMS = 0.45f
    }

    private fun drawBox(target: Target, s: Slice.Box, block: Block) {
        val x = target.left + (book.spec.width - s.w) / 2f
        val rect = RectF(x, target.top + s.y, x + s.w, target.top + s.y + s.h)
        val bmp = when (block) {
            is Block.Image -> images(block, s.w, s.h)
            is Block.Island -> islands(block.id)
            is Block.Text -> null
        }
        if (bmp != null && !bmp.isRecycled) {
            target.canvas.drawBitmap(bmp, null, rect, if ((block as? Block.Image)?.ink == true) inked else filter)
        } else {
            target.canvas.drawRect(rect, placeholder)
        }
    }
}
