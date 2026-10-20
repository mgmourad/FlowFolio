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
    private val placeholder = Paint()

    @Synchronized
    fun draw(target: Target, page: Page, colors: PageColors) {
        val chapter = book.chapters[page.chapter]
        placeholder.color = colors.faint
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
        with(target.canvas) {
            save()
            translate(target.left + target.paginator.indentPx(block), target.top + s.y - lineTop)
            clipRect(0f, lineTop, l.width.toFloat(), l.getLineBottom(s.last - 1).toFloat())
            l.draw(this)
            restore()
        }
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
            target.canvas.drawBitmap(bmp, null, rect, filter)
        } else {
            target.canvas.drawRect(rect, placeholder)
        }
    }
}
