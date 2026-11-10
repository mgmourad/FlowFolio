package com.flowfolio.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.BookCover
import com.flowfolio.core.designsystem.CoverBadge
import com.flowfolio.core.designsystem.CoverSpot
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioMenu
import com.flowfolio.core.designsystem.FlowFolioMenuDivider
import com.flowfolio.core.designsystem.FlowFolioMenuHeader
import com.flowfolio.core.designsystem.FlowFolioMenuItem
import com.flowfolio.core.designsystem.FlowFolioPill
import com.flowfolio.core.designsystem.FlowFolioProgressBar
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalCoverFlights
import com.flowfolio.core.designsystem.ShelfPlank
import com.flowfolio.core.designsystem.coverLanding
import com.flowfolio.core.designsystem.coverSpot
import com.flowfolio.core.designsystem.detailsCoverKey
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.designsystem.shelfCoverKey
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.LibraryView
import kotlin.math.roundToInt

/**
 * The book's menu (ADR-0052), from a long press or the Continue reading card's ⋯: open it or its details; favourite,
 * finished, shelf; its Notebook pages; Dynamic Flow for a PDF; its reading goal; then removing it, in red.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
internal fun BookMenu(item: LibraryItem, origin: String, open: Boolean, actions: LibraryActions, onClose: () -> Unit) {
    val book = item.book
    fun run(action: () -> Unit): () -> Unit = {
        onClose()
        action()
    }
    FlowFolioMenu(expanded = open, onDismissRequest = onClose) {
        FlowFolioMenuItem(stringResource(R.string.action_open), run { actions.onResume(item) }, glyph = Glyph.Book)
        FlowFolioMenuItem(
            stringResource(R.string.action_details),
            run { actions.onOpen(item, origin) },
            glyph = Glyph.Help
        )
        FlowFolioMenuDivider()
        actions.onFavorite?.let { fav ->
            FlowFolioMenuItem(
                stringResource(if (book.favorite) R.string.action_favorite_remove else R.string.action_favorite_add),
                run { fav(book) },
                glyph = if (book.favorite) Glyph.StarFilled else Glyph.Star
            )
        }
        actions.onFinished?.let { fin ->
            FlowFolioMenuItem(
                stringResource(if (book.finished) R.string.action_mark_unfinished else R.string.action_mark_finished),
                run { fin(book) },
                glyph = Glyph.Check
            )
        }
        FlowFolioMenuItem(
            stringResource(R.string.action_add_to_shelf),
            run { actions.onAddToShelf(book) },
            glyph = Glyph.Shelves
        )
        actions.selection?.let { sel ->
            FlowFolioMenuItem(
                stringResource(R.string.action_select),
                run {
                    sel.startWith(book.id)
                },
                glyph = Glyph.Select
            )
        }
        if (actions.onAnnotations != null || actions.onBookmarks != null || actions.onDictionary != null) {
            FlowFolioMenuDivider()
            FlowFolioMenuHeader(stringResource(R.string.menu_notebook))
            actions.onAnnotations?.let {
                FlowFolioMenuItem(stringResource(R.string.menu_highlights), run { it(book) }, glyph = Glyph.Highlight)
            }
            actions.onBookmarks?.let {
                FlowFolioMenuItem(stringResource(R.string.action_bookmarks), run { it(book) }, glyph = Glyph.Bookmark)
            }
            actions.onDictionary?.let {
                FlowFolioMenuItem(stringResource(R.string.menu_words), run { it(book) }, glyph = Glyph.Translate)
            }
        }
        FlowFolioMenuDivider()
        actions.onFlow?.takeIf { book.format == BookFormat.PDF }?.let { flow ->
            FlowFolioMenuItem(
                stringResource(if (book.flowMode == true) R.string.action_flow_off else R.string.action_flow_on),
                run { flow(book) },
                glyph = Glyph.DynamicFlow
            )
        }
        actions.onSetBookGoal?.let {
            FlowFolioMenuItem(
                stringResource(R.string.action_set_read_goal),
                run {
                    it(book)
                },
                glyph = Glyph.ReadingGoal
            )
        }
        actions.onRename?.let {
            FlowFolioMenuItem(stringResource(R.string.action_rename), run { it(book) }, glyph = Glyph.Edit)
        }
        if (book.storage is BookStorage.Linked) {
            FlowFolioMenuItem(
                stringResource(R.string.action_locate),
                run {
                    actions.onLocate(book)
                },
                glyph = Glyph.Folder
            )
        }
        FlowFolioMenuDivider()
        FlowFolioMenuItem(
            stringResource(R.string.action_remove_from_library),
            run { actions.onDelete(book) },
            glyph = Glyph.Trash,
            destructive = true
        )
    }
}

/** Sort and view (ADR-0052): the library's six orders, then Shelf, Grid or List; both remembered. */
@Composable
internal fun SortViewMenu(sort: LibrarySort, view: LibraryView, actions: LibraryActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        FlowFolioIconButton(
            onClick = { open = true },
            tooltip = stringResource(R.string.library_sort_view),
            modifier = Modifier.testTag(LibraryTestTags.SORT)
        ) {
            FlowFolioGlyph(Glyph.Sort, null, size = 20.dp)
        }
        FlowFolioMenu(expanded = open, onDismissRequest = { open = false }) {
            FlowFolioMenuHeader(stringResource(R.string.library_sort_by))
            LibrarySort.entries.forEach { s ->
                FlowFolioMenuItem(stringResource(s.label), {
                    open = false
                    actions.onSort(s)
                }, checked = s == sort)
            }
            FlowFolioMenuDivider()
            FlowFolioMenuHeader(stringResource(R.string.library_view_as))
            LibraryView.entries.forEach { v ->
                FlowFolioMenuItem(stringResource(v.label), {
                    open = false
                    actions.onView(v)
                }, glyph = v.glyph, checked = v == view)
            }
        }
    }
}

/** Import (ADR-0052): files, a folder, or a folder made straight into shelves. */
@Composable
internal fun ImportMenu(actions: LibraryActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        FlowFolioIconButton(
            onClick = { open = true },
            tooltip = stringResource(R.string.action_import),
            modifier = Modifier.testTag(LibraryTestTags.IMPORT)
        ) {
            FlowFolioGlyph(Glyph.Add, null, size = 20.dp)
        }
        FlowFolioMenu(expanded = open, onDismissRequest = { open = false }) {
            FlowFolioMenuItem(stringResource(R.string.import_files_menu), {
                open = false
                actions.onImport()
            }, glyph = Glyph.Page)
            FlowFolioMenuItem(stringResource(R.string.import_folder_menu), {
                open = false
                actions.onImportFolder()
            }, glyph = Glyph.Folder)
            FlowFolioMenuItem(stringResource(R.string.import_make_shelves), {
                open = false
                actions.onImportFolderAsShelves()
            }, glyph = Glyph.Shelves)
        }
    }
}

internal val LibrarySort.label: Int
    get() = when (this) {
        LibrarySort.RECENT -> R.string.sort_recently_opened
        LibrarySort.TITLE -> R.string.sort_title
        LibrarySort.AUTHOR -> R.string.sort_author
        LibrarySort.DATE_ADDED -> R.string.sort_date_added
        LibrarySort.PROGRESS -> R.string.sort_progress
        LibrarySort.READ_TIME -> R.string.sort_read_time
    }

private val LibraryView.label: Int
    get() = when (this) {
        LibraryView.SHELF -> R.string.view_shelf
        LibraryView.GRID -> R.string.view_grid
        LibraryView.LIST -> R.string.view_list
    }

private val LibraryView.glyph: Glyph
    get() = when (this) {
        LibraryView.SHELF -> Glyph.Shelves
        LibraryView.GRID -> Glyph.Library
        LibraryView.LIST -> Glyph.Contents
    }
