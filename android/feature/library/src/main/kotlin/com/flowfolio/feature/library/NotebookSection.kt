package com.flowfolio.feature.library

import com.flowfolio.core.designsystem.Glyph

/**
 * The Notebook sections the retired tabs map to (ADR-0052): `notebook?section=` names one of these, and the Notebook
 * opens on that kind of mark ([NotebookViewModel.sectionFilter]).
 */
enum class NotebookSection(val glyph: Glyph) {
    HIGHLIGHTS(Glyph.Highlight),
    BOOKMARKS(Glyph.Bookmark),
    WORDS(Glyph.Translate)
}
