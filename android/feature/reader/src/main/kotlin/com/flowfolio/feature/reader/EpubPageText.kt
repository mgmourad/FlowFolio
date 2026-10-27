package com.flowfolio.feature.reader

import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.PixelSize
import com.flowfolio.core.pdf.PageText
import com.flowfolio.core.reflow.PageGlyphs

/**
 * An EPUB page's text as selection reads it (M4): [text] with boxes as fractions of the page image, as PDFium gives a
 * PDF page's, and the [glyphs] it came from, whose chapter offsets turn a selection into a locator (ADR-0040).
 */
internal class EpubPageText(val text: PageText, val glyphs: PageGlyphs) {
    companion object {
        /** [glyphs] (pixels in the page box) as fractions of a [pane]-sized page whose box starts at [left], [top]. */
        fun of(glyphs: PageGlyphs, left: Int, top: Int, pane: PixelSize): EpubPageText {
            val src = glyphs.boxes
            val boxes = FloatArray(src.size) { i ->
                val v = src[i]
                when {
                    v < 0f -> v
                    i % PageGlyphs.FIELDS % 2 == 0 -> (left + v) / pane.width
                    else -> (top + v) / pane.height
                }
            }
            return EpubPageText(PageText(glyphs.text, boxes), glyphs)
        }
    }
}

/**
 * The book's highlights placed in the current layout: one each on its first page ([primary], for lists and menus),
 * and copies on the further pages a long highlight runs onto ([extra], drawing only). [exact] when every chapter is
 * laid out, so the pages are final and may be saved.
 */
internal class Placement(val primary: List<Annotation>, val extra: List<Annotation>, val exact: Boolean)
