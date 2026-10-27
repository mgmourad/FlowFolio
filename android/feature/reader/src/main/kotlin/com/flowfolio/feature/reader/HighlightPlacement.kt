package com.flowfolio.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.model.Annotation

/**
 * An EPUB's highlights placed in the current layout (ADR-0040): again whenever they change, the book is laid out again
 * or the reader moves (rectangles are worked out near the reader only). Once the layout's pages are final, highlights
 * whose first page moved are saved with it, so the library lists them in order and by the page last read.
 */
@Composable
internal fun PlaceHighlights(
    annotating: Annotating,
    all: List<Annotation>,
    s: ReaderState.Ready,
    viewModel: ReaderViewModel,
    vm: ReaderAnnotationsViewModel
) {
    val revision by viewModel.layoutRevision.collectAsStateWithLifecycle()
    LaunchedEffect(all, revision, s.anchorPage, s.layout) {
        val near = (s.anchorPage - NEAR_BEFORE..s.anchorPage + NEAR_AFTER).toSet()
        val placed = viewModel.place(all, near)
        annotating.all = placed.primary
        annotating.extra = placed.extra
        if (placed.exact && viewModel.pagesFinal) {
            val stored = all.associateBy { it.id }
            val moved = placed.primary.filter { it.locator != null && stored[it.id]?.page != it.page }
            if (moved.isNotEmpty()) vm.syncPages(moved.associate { it.id to it.page })
        }
    }
}

/** Pages around the reader's whose highlights get their rectangles: a spread, the turn either way, Scroll's column. */
private const val NEAR_BEFORE = 3
private const val NEAR_AFTER = 4
