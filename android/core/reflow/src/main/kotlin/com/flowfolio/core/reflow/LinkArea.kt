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

/** Where an island (block or inline) is drawn on a page, in pixels from the page box's top left. */
data class IslandArea(val id: Int, val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Where [page]'s islands are drawn, laid out by [paginator]: block islands and the ones inline in its text. */
fun ReflowBook.islandAreas(page: Page, paginator: Paginator): List<IslandArea> {
    val chapter = chapters.getOrNull(page.chapter) ?: return emptyList()
    return page.slices.flatMap { slice ->
        when (slice) {
            is Slice.Box -> listOfNotNull(
                (chapter.blocks.getOrNull(slice.block) as? Block.Island)?.let {
                    val left = (spec.width - slice.w) / 2f
                    IslandArea(it.id, left, slice.y, left + slice.w, slice.y + slice.h)
                }
            )

            is Slice.Lines -> {
                val block = chapter.blocks.getOrNull(slice.block) as? Block.Text
                block?.let { slice.islandAreas(paginator.indentPx(it).toFloat()) }.orEmpty()
            }
        }
    }
}

/** Where a book image is drawn on a page, in pixels from the page box's top left; [path] is book-internal. */
data class ImageArea(val path: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Where [page]'s images are drawn (M4: tapped, they open zoomable). */
fun ReflowBook.imageAreas(page: Page): List<ImageArea> {
    val chapter = chapters.getOrNull(page.chapter) ?: return emptyList()
    return page.slices.filterIsInstance<Slice.Box>().mapNotNull { slice ->
        (chapter.blocks.getOrNull(slice.block) as? Block.Image)?.let {
            val left = (spec.width - slice.w) / 2f
            ImageArea(it.path, left, slice.y, left + slice.w, slice.y + slice.h)
        }
    }
}

/** The inline islands on this slice's lines; [indent] is where the block's text starts. */
private fun Slice.Lines.islandAreas(indent: Float): List<IslandArea> {
    val text = layout.text as? Spanned ?: return emptyList()
    val from = layout.getLineStart(first)
    val to = layout.getLineEnd(last - 1)
    val top = layout.getLineTop(first)
    return text.getSpans(from, to, IslandSpan::class.java).mapNotNull { span ->
        val start = text.getSpanStart(span)
        val line = layout.getLineForOffset(start)
        val box = layout.box(line, start, text.getSpanEnd(span))
        if (line !in first until last || box == null) return@mapNotNull null
        IslandArea(
            span.id,
            indent + box.first,
            y + layout.getLineTop(line) - top,
            indent + box.second,
            y + layout.getLineBottom(line) - top
        )
    }
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
