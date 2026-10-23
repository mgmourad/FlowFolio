package com.flowfolio.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph

/**
 * The library's totals, one per line, each after its glyph (owner, 2026-10-23): books, books in progress, ribbons,
 * annotations and time read.
 */
@Composable
internal fun LibraryTotals(state: LibraryUiState, stats: LibraryStats, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TotalLine(Glyph.Library, pluralStringResource(R.plurals.book_count, state.totalCount, state.totalCount))
        TotalLine(Glyph.Hourglass, stringResource(R.string.library_in_progress, state.inProgressCount))
        TotalLine(Glyph.Bookmark, pluralStringResource(R.plurals.ribbon_total, stats.bookmarks, stats.bookmarks))
        TotalLine(Glyph.Edit, pluralStringResource(R.plurals.annotation_total, stats.annotations, stats.annotations))
        TotalLine(Glyph.Stopwatch, stringResource(R.string.library_read_time, readTime(stats.readMillis)))
    }
}

@Composable
private fun TotalLine(glyph: Glyph, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowFolioGlyph(glyph, null, color = FlowFolio.palette.subItem, size = TOTAL_GLYPH, glow = false)
        Text(text, style = FlowFolioType.sub.merge(FlowFolio.hint), maxLines = 1)
    }
}

private val TOTAL_GLYPH = 16.dp
