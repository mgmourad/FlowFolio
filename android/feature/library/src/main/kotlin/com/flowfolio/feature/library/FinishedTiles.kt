package com.flowfolio.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The Finished chip's books (owner, M3T2): tiles only, latest finished first, wrapping to as many in a row as fit.
 * A tile opens the book's details; holding it opens its menu, as on a shelf.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FinishedTiles(items: List<LibraryItem>, actions: LibraryActions, modifier: Modifier = Modifier) {
    val finished = items.sortedByDescending { it.book.finishedAtMillis }
    if (finished.isEmpty()) {
        NoMatch()
        return
    }
    FlowRow(
        modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        finished.forEach { item ->
            ShelfBook(item, FINISHED_ORIGIN, actions, width = FINISHED_TILE_WIDTH)
        }
    }
}

private const val FINISHED_ORIGIN = "finished"
private val FINISHED_TILE_WIDTH = 96.dp
