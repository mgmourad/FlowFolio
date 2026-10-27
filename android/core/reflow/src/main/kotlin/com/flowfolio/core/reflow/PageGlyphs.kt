package com.flowfolio.core.reflow

import android.text.Layout
import android.text.StaticLayout

/**
 * A page's text as the reader selects it (M4): the characters of its lines in reading order, a box per character in
 * pixels from the page box's top left (left, top, right, bottom; -1s for a break), and each character's chapter
 * offset. Blocks are separated by a [BREAK] without a box, whose offset is the next block's first, so offsets never
 * decrease.
 */
class PageGlyphs(val chapter: Int, val text: String, val boxes: FloatArray, val offsets: IntArray) {
    val length: Int get() = text.length

    /** The first character at or after chapter offset [offset], or [length] when none is. */
    fun indexAtOrAfter(offset: Int): Int {
        var lo = 0
        var hi = offsets.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (offsets[mid] < offset) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** The characters of this page within chapter offsets [first]..[last], or null when the page has none of them. */
    fun indicesOf(first: Int, last: Int): IntRange? {
        val from = indexAtOrAfter(first)
        val to = indexAtOrAfter(last + 1) - 1
        return if (from <= to && from < length) from..to else null
    }

    companion object {
        /** Floats per character in [boxes]. */
        const val FIELDS = 4

        /** Stands between blocks and for an image or island (no box). */
        const val BREAK = '\n'
    }
}

/** [page]'s characters and where each is drawn, laid out by [paginator] (see [PageGlyphs]). */
fun ReflowBook.pageGlyphs(page: Page, paginator: Paginator): PageGlyphs {
    val chapter = chapters.getOrNull(page.chapter)
    val out = GlyphSink()
    page.slices.forEachIndexed { n, slice ->
        val block = chapter?.blocks?.getOrNull(slice.block)
        // Between blocks: a break, unless an image or island (a break itself) is on either side.
        val afterLines = n > 0 && page.slices[n - 1] is Slice.Lines
        if (afterLines && slice is Slice.Lines && block != null) out.addBreak(block.start + firstChar(slice))
        if (slice is Slice.Lines && block is Block.Text) {
            val justified = block.style.justify && spec.justify
            slice.glyphs(paginator.indentPx(block).toFloat(), block.start, justified, out)
        } else if (block != null) {
            // An image or island: one place in the text, without a box (it is not selected as text).
            out.addBreak(block.start)
        }
    }
    return PageGlyphs(page.chapter, out.text.toString(), out.boxes.toArray(), out.offsets.toArray())
}

private fun firstChar(slice: Slice): Int = if (slice is Slice.Lines) slice.layout.getLineStart(slice.first) else 0

/**
 * This slice's characters, line by line: each one's box (pixels from the page box) and chapter offset. [justified]:
 * the block is set justified (the book's style and the reader's setting), as the layout was built.
 */
private fun Slice.Lines.glyphs(indent: Float, blockStart: Int, justified: Boolean, out: GlyphSink) {
    val l = layout
    val source = l.text
    val top0 = l.getLineTop(first)
    for (line in first until last) {
        val top = y + l.getLineTop(line) - top0
        val bottom = y + l.getLineBottom(line) - top0
        val start = l.getLineStart(line)
        val end = l.getLineEnd(line)
        val rtl = l.getParagraphDirection(line) == Layout.DIR_RIGHT_TO_LEFT
        val lineEdge = if (rtl) l.getLineLeft(line) else l.getLineRight(line)
        val stretch = if (rtl || !justified) 0f else l.stretchPerSpace(line, start, end)
        var shift = 0f
        var x = l.getPrimaryHorizontal(start)
        for (i in start until end) {
            if (source[i] == ' ') shift += stretch
            val next = if (i + 1 < end) l.getPrimaryHorizontal(i + 1) + shift else lineEdge
            out.text.append(source[i])
            out.boxes.add(indent + minOf(x, next), top, indent + maxOf(x, next), bottom)
            out.offsets.add(blockStart + i)
            x = next
        }
    }
}

/** A page's glyphs as they are read: characters, boxes and chapter offsets. */
private class GlyphSink {
    val text = StringBuilder()
    val boxes = FloatArrayList()
    val offsets = IntArrayList()

    /** A break without a box at chapter [offset]. */
    fun addBreak(offset: Int) {
        text.append(PageGlyphs.BREAK)
        boxes.addNone()
        offsets.add(offset)
    }
}

/**
 * How much wider than its natural width justification sets each word space of [line]: `getPrimaryHorizontal` ignores
 * justification, so without this every box after the first space of a justified line sits too far left. The slack
 * (the column's width less the line's natural width) is shared equally among the spaces before the trailing ones, as
 * inter-word justification does; a paragraph's last line and a hard break are not justified.
 */
private fun StaticLayout.stretchPerSpace(line: Int, start: Int, end: Int): Float {
    val source = text
    val justified = line < lineCount - 1 && end > start && source[end - 1] != '\n'
    var visible = end
    while (visible > start && source[visible - 1] == ' ') visible--
    val spaces = (start until visible).count { source[it] == ' ' }
    if (!justified || spaces == 0) return 0f
    val natural = if (visible < end) getPrimaryHorizontal(visible) else getLineMax(line)
    return ((width - natural) / spaces).coerceAtLeast(0f)
}

/** A growable float array, so a page's boxes are not boxed one by one. */
private class FloatArrayList {
    private var data = FloatArray(INITIAL)
    private var size = 0

    fun add(a: Float, b: Float, c: Float, d: Float) {
        ensure(PageGlyphs.FIELDS)
        data[size++] = a
        data[size++] = b
        data[size++] = c
        data[size++] = d
    }

    fun addNone() = add(NONE, NONE, NONE, NONE)

    private fun ensure(more: Int) {
        if (size + more > data.size) data = data.copyOf(maxOf(data.size * 2, size + more))
    }

    fun toArray(): FloatArray = data.copyOf(size)
}

private class IntArrayList {
    private var data = IntArray(INITIAL)
    private var size = 0

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    fun toArray(): IntArray = data.copyOf(size)
}

private const val INITIAL = 1024
private const val NONE = -1f
