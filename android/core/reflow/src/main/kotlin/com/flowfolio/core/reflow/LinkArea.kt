package com.flowfolio.core.reflow

import android.text.Spanned
import android.text.StaticLayout
import kotlin.math.max
import kotlin.math.min

/** A link's tappable area on a page: [href] as in its [LinkSpan], the box in pixels from the page box's top left. */
data class LinkArea(val href: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Where [page]'s links are, laid out by [paginator]: one area for each line a link covers. */
fun ReflowBook.linkAreas(page: Page, paginator: Paginator): List<LinkArea> {
    val chapter = chapters.getOrNull(page.chapter) ?: return emptyList()
    return page.slices.filterIsInstance<Slice.Lines>().flatMap { slice ->
        val block = chapter.blocks.getOrNull(slice.block) as? Block.Text
        block?.let { slice.linkAreas(paginator.indentPx(it).toFloat()) }.orEmpty()
    }
}

/** The links on this slice's lines; [indent] is where the block's text starts. */
private fun Slice.Lines.linkAreas(indent: Float): List<LinkArea> {
    val text = layout.text as? Spanned ?: return emptyList()
    val from = layout.getLineStart(first)
    val to = layout.getLineEnd(last - 1)
    val top = layout.getLineTop(first)
    val out = ArrayList<LinkArea>()
    for (span in text.getSpans(from, to, LinkSpan::class.java)) {
        val start = max(text.getSpanStart(span), from)
        val end = min(text.getSpanEnd(span), to)
        if (end <= start) continue
        for (line in max(first, layout.getLineForOffset(start))..min(last - 1, layout.getLineForOffset(end - 1))) {
            val box = layout.box(line, start, end) ?: continue
            out += LinkArea(
                span.href,
                indent + box.first,
                y + layout.getLineTop(line) - top,
                indent + box.second,
                y + layout.getLineBottom(line) - top
            )
        }
    }
    return out
}

/** The horizontal extent of `start until end` on [line], left to right whatever the text's direction; null if empty. */
private fun StaticLayout.box(line: Int, start: Int, end: Int): Pair<Float, Float>? {
    val a = max(start, getLineStart(line))
    val b = min(end, getLineEnd(line))
    if (b <= a) return null
    val x0 = getPrimaryHorizontal(a)
    val x1 = if (b >= getLineEnd(line)) getLineRight(line) else getPrimaryHorizontal(b)
    return min(x0, x1) to max(x0, x1)
}
