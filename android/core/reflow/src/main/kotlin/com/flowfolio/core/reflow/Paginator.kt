package com.flowfolio.core.reflow

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import java.util.Locale

/** The colours for syntax-highlighted code (Phase 4 M4), as ARGB ints. */
data class CodeColors(val keyword: Int, val string: Int, val comment: Int, val number: Int)

/**
 * The page box text is laid out in (pixels, margins already removed), the body text size, and how text is set: the
 * reader's font (M3), line spacing as a multiple of the size, justification and hyphenation.
 */
data class PageSpec(
    val width: Int,
    val height: Int,
    val bodyPx: Float,
    val locale: Locale = Locale.getDefault(),
    /** The typeface for headings (the app's display face); null for the body's. */
    val heading: Typeface? = null,
    /** The typeface for body text, a family with bold and italic; null for the system serif. */
    val body: Typeface? = null,
    val lineSpacing: Float = DEFAULT_LINE_SPACING,
    /** Paragraphs the book sets flush on both sides are justified; off sets every line ragged. */
    val justify: Boolean = true,
    val hyphenate: Boolean = true,
    /** The colours for syntax-highlighted code (Phase 4 M4); null draws code in one colour. */
    val code: CodeColors? = null
) {
    companion object {
        /** Line height in ems: the distance from one line's baseline to the next, as a multiple of the text size. */
        const val DEFAULT_LINE_SPACING = 1.5f
    }
}

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
        typeface = spec.body ?: Typeface.SERIF
        color = Color.BLACK
        textLocale = spec.locale
    }
    private val bold = Typeface.create(
        spec.heading ?: spec.body ?: Typeface.SERIF,
        if (spec.heading !=
            null
        ) {
            HEADING_WEIGHT
        } else {
            BOLD_WEIGHT
        },
        false
    )

    fun layout(block: Block.Text): StaticLayout {
        if (block.style.preformatted) return codeLayout(block)
        val p = TextPaint(paint).apply {
            textSize = spec.bodyPx * block.style.scale
            if (block.style.bold) typeface = bold
        }
        val width = (spec.width - indentPx(block)).coerceAtLeast(1)
        // Every line exactly [PageSpec.lineSpacing] times the text size apart, whatever the font: fonts' built-in line
        // heights differ widely (Literata's is tall, Charter's short), so a multiplier would set far fewer lines a page
        // in some fonts than in others. The span goes on a copy: the book's text is shared between layouts.
        val text = SpannableString(block.text).apply {
            setSpan(LineHeightSpan.Standard(lineHeightPx(p.textSize)), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, p, width)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(
                if (spec.hyphenate) Layout.HYPHENATION_FREQUENCY_NORMAL else Layout.HYPHENATION_FREQUENCY_NONE
            )
            .setJustificationMode(
                if (block.style.justify && spec.justify) {
                    LineBreaker.JUSTIFICATION_MODE_INTER_WORD
                } else {
                    LineBreaker.JUSTIFICATION_MODE_NONE
                }
            )
            .setAlignment(if (block.style.centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()
    }

    /**
     * Code: a monospaced face a little smaller than the text, its lines as written, never hyphenated or justified. A
     * line wider than the column scales the whole listing down to fit (so a chart drawn with characters keeps its
     * shape), down to [MIN_CODE_SCALE]; past that, long lines wrap.
     */
    private fun codeLayout(block: Block.Text): StaticLayout {
        val p = TextPaint(paint).apply {
            typeface = CodeFace.typeface
            textSize = spec.bodyPx * CODE_SCALE * block.style.scale
        }
        val width = (spec.width - indentPx(block) - codePadPx()).coerceAtLeast(1)
        val widest = block.text.split('\n').maxOf { p.measureText(it) }
        // A little to spare: the laid-out line can measure a pixel or two wider than the plain string did.
        if (widest > width) p.textSize = maxOf(p.textSize * width / widest * FIT_MARGIN, spec.bodyPx * MIN_CODE_SCALE)
        val text = SpannableString(block.text).apply {
            setSpan(
                LineHeightSpan.Standard((p.textSize * CODE_LINE_SPACING).toInt().coerceAtLeast(1)),
                0,
                length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )
            // A line too long even at the smallest size wraps: its continuation is set in, as editors show a soft
            // wrap, so it doesn't read as a row of its own.
            if (block.text.split('\n').any { p.measureText(it) > width }) {
                val hang = (p.measureText("m") * WRAP_INDENT_CHARS).toInt()
                setSpan(LeadingMarginSpan.Standard(0, hang), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            }
            highlight(this)
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, p, width)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setJustificationMode(LineBreaker.JUSTIFICATION_MODE_NONE)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()
    }

    /** Colours [text]'s code tokens with [spec.code], when highlighting is on (Phase 4 M4). */
    private fun highlight(text: SpannableString) {
        val colors = spec.code ?: return
        val source = text.toString()
        val language = CodeHighlighter.detect(source) ?: return
        for (token in CodeHighlighter.tokenize(source, language)) {
            val color = when (token.type) {
                CodeTokenType.KEYWORD -> colors.keyword
                CodeTokenType.STRING -> colors.string
                CodeTokenType.COMMENT -> colors.comment
                CodeTokenType.NUMBER -> colors.number
            }
            text.setSpan(ForegroundColorSpan(color), token.start, token.end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }
    }

    private fun lineHeightPx(textSize: Float): Int = (textSize * spec.lineSpacing).toInt().coerceAtLeast(1)

    /** Where a block's text starts: its indent, and inside a code panel its padding. */
    fun indentPx(block: Block.Text) =
        (spec.bodyPx * block.style.indentEm).toInt() + if (block.style.preformatted) codePadPx() else 0

    /** A code panel's padding, each side of its text. */
    fun codePadPx() = (spec.bodyPx * CODE_PAD_EM).toInt()

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
            // A short listing (a diagram drawn with characters) moves whole to the next page rather than break.
            if (keptWhole(block, layout)) close(block.start)
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

        /** A short listing (a diagram drawn with characters) that won't fit the page moves whole to the next. */
        private fun keptWhole(block: Block.Text, layout: StaticLayout) = block.style.preformatted && y > 0f &&
            y + layout.height > spec.height && layout.height <= spec.height * KEEP_CODE_SHARE

        /** A heading needs its own lines plus two lines of what follows. */
        private fun headingNeeds(layout: StaticLayout, block: Block.Text) =
            layout.height + spec.bodyPx * block.style.spaceAfterEm + 2 * lineHeightPx(spec.bodyPx)

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
            val scale = if (block is Block.Image && block.fullWidth) {
                // A table spans the column; its height may run past the page (it is zoomed to read).
                minOf(1f, spec.width.toFloat() / w0.coerceAtLeast(1))
            } else {
                minOf(1f, spec.width.toFloat() / w0.coerceAtLeast(1), spec.height.toFloat() / h0.coerceAtLeast(1))
            }
            val w = (w0 * scale).toInt()
            val h = (h0 * scale).toInt()
            if (y > 0f && y + h > spec.height) close(block.start)
            slices += Slice.Box(index, y, w, h)
            y += h + spec.bodyPx * BOX_SPACE_EM
        }
    }

    companion object {
        private const val BOLD_WEIGHT = 700
        private const val HEADING_WEIGHT = 600
        const val MIN_LINES = 2
        const val BOX_SPACE_EM = 0.8f
        const val PLACEHOLDER_EMS = 4f
        const val CODE_SCALE = 0.85f
        const val MIN_CODE_SCALE = 0.55f

        /** A wrapped code line goes on this many characters in. */
        const val WRAP_INDENT_CHARS = 2
        const val CODE_LINE_SPACING = 1.3f
        const val CODE_PAD_EM = 0.6f
        private const val FIT_MARGIN = 0.98f

        /** Listings up to this share of a page are kept together; longer ones break where the page ends. */
        private const val KEEP_CODE_SHARE = 0.4f
    }
}
