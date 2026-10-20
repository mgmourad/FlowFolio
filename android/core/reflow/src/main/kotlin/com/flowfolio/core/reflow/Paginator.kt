package com.flowfolio.core.reflow

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.util.Locale

/** The page box text is laid out in (pixels, margins already removed), and the body text size. */
data class PageSpec(val width: Int, val height: Int, val bodyPx: Float, val locale: Locale = Locale.getDefault())

/** A laid-out piece of one block on one page. [y] is from the top of the page box. */
sealed interface Slice {
    val block: Int
    val y: Float

    class Lines(
        override val block: Int,
        override val y: Float,
        val layout: StaticLayout,
        val first: Int,
        val last: Int
    ) : Slice

    /** An image or island, already scaled to fit the page. */
    class Box(override val block: Int, override val y: Float, val w: Int, val h: Int) : Slice
}

/** One page of one chapter. [start] and [end] are chapter offsets: the page shows `start until end`. */
class Page(val chapter: Int, val slices: List<Slice>, val start: Int, val end: Int)

/**
 * Cuts a chapter into pages of [spec]. Rules: whole lines only; at least two lines of a paragraph at the bottom of a
 * page and at the top of the next (orphans and widows); a heading never ends a page. [boxSize] gives an image's or
 * island's size in pixels (null when unknown: a placeholder size is used).
 */
class Paginator(private val spec: PageSpec, private val boxSize: (Block) -> Pair<Int, Int>?) {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SERIF
        color = Color.BLACK
        textLocale = spec.locale
    }
    private val bold = Typeface.create(Typeface.SERIF, Typeface.BOLD)

    fun layout(block: Block.Text): StaticLayout {
        val p = TextPaint(paint).apply {
            textSize = spec.bodyPx * block.style.scale
            if (block.style.bold) typeface = bold
        }
        val width = (spec.width - indentPx(block)).coerceAtLeast(1)
        return StaticLayout.Builder.obtain(block.text, 0, block.text.length, p, width)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .setJustificationMode(
                if (block.style.justify) {
                    LineBreaker.JUSTIFICATION_MODE_INTER_WORD
                } else {
                    LineBreaker.JUSTIFICATION_MODE_NONE
                }
            )
            .setAlignment(if (block.style.centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, LINE_SPACING)
            .setIncludePad(false)
            .build()
    }

    fun indentPx(block: Block.Text) = (spec.bodyPx * block.style.indentEm).toInt()

    fun paginate(chapterIndex: Int, chapter: Chapter): List<Page> = PageBuilder(chapterIndex, chapter).build()

    /** Fills pages one block at a time. */
    private inner class PageBuilder(private val chapterIndex: Int, private val chapter: Chapter) {
        private val pages = ArrayList<Page>()
        private var slices = ArrayList<Slice>()
        private var start = 0
        private var y = 0f

        fun build(): List<Page> {
            chapter.blocks.forEachIndexed { index, block ->
                when (block) {
                    is Block.Text -> text(index, block)
                    else -> box(index, block)
                }
            }
            if (slices.isNotEmpty() || pages.isEmpty()) close(chapter.length)
            return pages
        }

        private fun close(end: Int) {
            pages += Page(chapterIndex, slices, start, end)
            slices = ArrayList()
            start = end
            y = 0f
        }

        private fun text(index: Int, block: Block.Text) {
            val layout = layout(block)
            if (y > 0f) y += spec.bodyPx * block.style.spaceBeforeEm
            if (block.style.heading && y > 0f && y + headingNeeds(layout, block) > spec.height) close(block.start)
            var line = 0
            while (line < layout.lineCount) {
                val take = linesThatFit(layout, line)
                if (take == 0) {
                    close(block.start + layout.getLineStart(line))
                    continue
                }
                slices += Slice.Lines(index, y, layout, line, line + take)
                y += layout.getLineBottom(line + take - 1) - layout.getLineTop(line)
                line += take
                if (line < layout.lineCount) close(block.start + layout.getLineStart(line))
            }
            y += spec.bodyPx * block.style.spaceAfterEm
        }

        /** A heading needs its own lines plus two lines of what follows. */
        private fun headingNeeds(layout: StaticLayout, block: Block.Text) =
            layout.height + spec.bodyPx * block.style.spaceAfterEm + 2 * spec.bodyPx * LINE_SPACING

        /** How many lines from [from] go on this page; 0 means start a new page first. */
        private fun linesThatFit(layout: StaticLayout, from: Int): Int {
            val top = layout.getLineTop(from)
            var fit = from
            while (fit < layout.lineCount && y + (layout.getLineBottom(fit) - top) <= spec.height) fit++
            val remaining = layout.lineCount - from
            var take = fit - from
            if (take < remaining) {
                when {
                    take < MIN_LINES && y > 0f -> take = 0

                    // an orphan: move the paragraph's start over
                    // A widow: leave two lines for the next page.
                    remaining - take < MIN_LINES && take > MIN_LINES -> take -= MIN_LINES - (remaining - take)
                }
            }
            // A line taller than an empty page still has to go somewhere.
            return if (take == 0 && y == 0f) 1 else take
        }

        private fun box(index: Int, block: Block) {
            val (w0, h0) = boxSize(block) ?: (spec.width to (spec.bodyPx * PLACEHOLDER_EMS).toInt())
            val scale =
                minOf(1f, spec.width.toFloat() / w0.coerceAtLeast(1), spec.height.toFloat() / h0.coerceAtLeast(1))
            val w = (w0 * scale).toInt()
            val h = (h0 * scale).toInt()
            if (y > 0f && y + h > spec.height) close(block.start)
            slices += Slice.Box(index, y, w, h)
            y += h + spec.bodyPx * BOX_SPACE_EM
        }
    }

    companion object {
        const val LINE_SPACING = 1.4f
        const val MIN_LINES = 2
        const val BOX_SPACE_EM = 0.8f
        const val PLACEHOLDER_EMS = 4f
    }
}
