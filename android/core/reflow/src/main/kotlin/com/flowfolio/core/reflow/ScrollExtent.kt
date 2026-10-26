package com.flowfolio.core.reflow

/**
 * How far down [page]'s box Scroll shows it (pixels from the box's top), so that pages stacked in one column read
 * as continuous text: to its last line or box, plus the space the paginator would have left before what follows
 * (wider between chapters). Pages still estimated, and empty pages, keep the whole box.
 */
fun ReflowBook.scrollExtent(page: Int): Int {
    val p = pages.getOrNull(page)
    val last = p?.slices?.lastOrNull()
    if (p == null || last == null) return if (p == null) 0 else spec.height
    val blocks = chapters[p.chapter].blocks
    val bottom: Float
    var gap = 0f
    when (last) {
        is Slice.Lines -> {
            bottom = last.y + last.layout.getLineBottom(last.last - 1) - last.layout.getLineTop(last.first)
            if (last.last == last.layout.lineCount) gap = (blocks[last.block] as Block.Text).style.spaceAfterEm
        }

        is Slice.Box -> {
            bottom = last.y + last.h
            gap = Paginator.BOX_SPACE_EM
        }
    }
    val next = pages.getOrNull(page + 1)
    val first = next?.slices?.firstOrNull()
    // A page's first block gets no space above it (it starts the page); in a column it needs it back.
    if (first is Slice.Lines && first.first == 0 && next.chapter == p.chapter) {
        gap += (chapters[next.chapter].blocks[first.block] as Block.Text).style.spaceBeforeEm
    }
    if (next == null || next.chapter != p.chapter) gap = maxOf(gap, CHAPTER_GAP_EM)
    return (bottom + gap * spec.bodyPx).toInt().coerceAtLeast(1)
}

/** Space between chapters in a continuous column (Scroll), in ems of body text. */
private const val CHAPTER_GAP_EM = 3f
