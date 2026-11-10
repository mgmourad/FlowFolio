package com.flowfolio.feature.library

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.CoverBadge
import com.flowfolio.core.designsystem.color
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.LibraryView
import com.flowfolio.core.model.StatsBand

/**
 * The top of the library under its header (ADR-0052): the stats band (unless hidden), Continue reading, the folded
 * reading-goal card, and the chips.
 */
internal fun LazyListScope.libraryTop(
    state: LibraryUiState,
    actions: LibraryActions,
    stats: LibraryStats,
    wide: Boolean,
    gutter: Dp
) {
    val goal = GoalStatus(stats.libraryGoal, stats.periodReadMillis)
    if (state.statsBand != StatsBand.HIDDEN) {
        item(key = "stats") {
            StatsBandCard(
                state,
                stats,
                wide,
                actions,
                Modifier.padding(horizontal = gutter, vertical = 8.dp).reveal(REVEAL_STATS)
            )
        }
    }
    state.continueReading?.let { hero ->
        item(key = "hero") {
            ContinueReadingCard(
                hero,
                actions,
                wide,
                goal,
                Modifier.padding(horizontal = gutter, vertical = 8.dp).reveal(REVEAL_HERO)
            )
        }
    }
    if (!wide) {
        item(key = "goal") {
            GoalCard(
                goal,
                actions.onReadingGoal,
                Modifier.padding(horizontal = gutter, vertical = 4.dp).reveal(REVEAL_GOAL)
            )
        }
    }
    item(key = "chips") {
        FilterChips(state.filters, state.filter, actions.onFilter, gutter, Modifier.reveal(REVEAL_CHIPS))
    }
}

/**
 * Each shelf in the library (ADR-0052): its heading with "N books ›", its first row of books standing on a plank,
 * then "Not on a shelf" last. Every key is the row's own ([ShelfRow.key]), so no two items share one.
 */
internal fun LazyListScope.shelfSections(state: LibraryUiState, actions: LibraryActions, perRow: Int, gutter: Dp) {
    val rows = shelfRows(state.items, state.shelves, state.sortRank)
    rows.forEachIndexed { i, row ->
        val page = row.shelf?.id ?: row.special ?: ShelfPageViewModel.LOOSE
        item(key = row.key + "-head") {
            val name = row.shelf?.name ?: stringResource(
                if (state.shelves.isEmpty()) R.string.shelf_all_books else R.string.shelf_loose
            )
            ShelfHeading(
                name,
                row.items.size,
                onOpen = { actions.onOpenShelf(page) },
                modifier = Modifier.padding(horizontal = gutter).reveal(ROWS_REVEAL_ORDER + i),
                color = row.shelf?.color?.color,
                tag = LibraryTestTags.shelfCount(page)
            )
        }
        val origin = row.shelf?.id?.toString() ?: LOOSE_ORIGIN
        shelfRow(row.key, row.items, perRow, gutter, origin, actions, ROWS_REVEAL_ORDER + i)
    }
}

/**
 * The list index of [shelfId]'s shelf heading, matching the items [LibraryContent] emits; null when it has none. A
 * shelf is two items in the library: its heading and its one row of books.
 */
internal fun rowIndex(state: LibraryUiState, shelfId: Long, wide: Boolean): Int? {
    val rows = shelfRows(state.items, state.shelves, state.sortRank)
    val row = rows.indexOfFirst { it.shelf?.id == shelfId }
    if (state.view != LibraryView.SHELF || state.filter != LibraryFilter.ALL || row < 0) return null
    val before = listOf(
        true,
        state.importing,
        state.statsBand != StatsBand.HIDDEN,
        state.continueReading != null,
        !wide,
        true
    ).count { it }
    return before + row * 2
}

/** The marks a cover carries: Dynamic Flow on, a ribbon in it, a favourite. */
internal fun LibraryItem.badges(): Set<CoverBadge> = buildSet {
    if (book.flowMode == true && book.format == BookFormat.PDF) add(CoverBadge.DYNAMIC_FLOW)
    if (hasBookmarks) add(CoverBadge.BOOKMARKED)
    if (isFavorite) add(CoverBadge.FAVORITE)
}

/** "Missing" or "Locked", when the book cannot simply be opened. */
internal fun LibraryItem.statusLabel(): Int? = when {
    availability == Availability.MISSING -> R.string.badge_missing
    book.passwordProtected -> R.string.badge_locked
    else -> null
}

private const val REVEAL_STATS = 1
private const val REVEAL_HERO = 2
private const val REVEAL_GOAL = 3
private const val REVEAL_CHIPS = 4
