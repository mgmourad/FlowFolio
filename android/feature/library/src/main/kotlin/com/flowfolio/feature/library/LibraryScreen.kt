package com.flowfolio.feature.library

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.LocalChromePadding
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.LibrarySort

private val PDF_TYPES = arrayOf("application/pdf")

/** What the library screen can ask for. [onOpen] gets the row the book was tapped in, for the cover flight. */
data class LibraryActions(
    val onImport: () -> Unit = {},
    val onOpen: (LibraryItem, String) -> Unit = { _, _ -> },
    val onResume: (LibraryItem) -> Unit = {},
    val onDelete: (Book) -> Unit = {},
    val onLocate: (Book) -> Unit = {},
    val onAddToShelf: (Book) -> Unit = {},
    val onRename: ((Book) -> Unit)? = null,
    /** Opens a whole shelf ([ShelfPageViewModel.LOOSE] for the books on no shelf). */
    val onOpenShelf: (Long) -> Unit = {},
    val onSort: (LibrarySort) -> Unit = {},
    val onFilter: (LibraryFilter) -> Unit = {},
    val onQuery: (String) -> Unit = {}
)

/** Library route: wires the ViewModels, file pickers, snackbar messages and shelf dialogs to [LibraryContent]. */
@Composable
fun LibraryScreen(
    onOpenDetails: (Book, String) -> Unit,
    onRead: (Book) -> Unit,
    onOpenShelf: (Long) -> Unit = {},
    scrollToShelf: Long? = null,
    onScrolledToShelf: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
    shelves: ShelvesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var relinkBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var shelving by remember { mutableStateOf<Book?>(null) }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onFilePicked(it.toString()) }
    }
    val relinkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = relinkBookId
        if (uri != null && id != null) viewModel.onRelinkPicked(id, uri.toString())
        relinkBookId = null
    }
    val locate = { book: Book ->
        relinkBookId = book.id
        relinkPicker.launch(PDF_TYPES)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it.text(context)) }
    }

    LibraryContent(
        state = state,
        snackbar = snackbar,
        scrollToShelf = scrollToShelf,
        onScrolledToShelf = onScrolledToShelf,
        actions = LibraryActions(
            onImport = { importPicker.launch(PDF_TYPES) },
            onOpen = { item, origin ->
                if (item.availability == Availability.MISSING) locate(item.book) else onOpenDetails(item.book, origin)
            },
            onResume = { item ->
                if (item.availability == Availability.MISSING) {
                    locate(item.book)
                } else {
                    viewModel.onBookOpened(item.book.id)
                    onRead(item.book)
                }
            },
            onDelete = viewModel::onRequestDelete,
            onLocate = locate,
            onAddToShelf = { shelving = it },
            onOpenShelf = onOpenShelf,
            onSort = viewModel::onSortChange,
            onFilter = viewModel::onFilter,
            onQuery = viewModel::onQuery
        )
    )
    LibraryDialogs(state.dialog, viewModel)
    shelving?.let { ShelveBookFlow(it, state.shelves, shelves) { shelving = null } }
}

/** The library as in the mock: header, chips, "Continue reading" and a shelf row per shelf, on the app background. */
@Composable
fun LibraryContent(
    state: LibraryUiState,
    snackbar: SnackbarHostState,
    actions: LibraryActions,
    scrollToShelf: Long? = null,
    onScrolledToShelf: () -> Unit = {}
) {
    val chrome = LocalChromePadding.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    var searching by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
    val list = rememberLazyListState()
    // Shelves screen -> "show this shelf": scroll its row into view once the rows are there.
    LaunchedEffect(scrollToShelf, state.loading, state.shelves) {
        val target = scrollToShelf ?: return@LaunchedEffect
        if (state.loading) return@LaunchedEffect
        val index = rowIndex(state, searching, target)
        if (index != null) list.animateScrollToItem(index)
        onScrolledToShelf()
    }
    Box(Modifier.fillMaxSize().chromeStart()) {
        LazyColumn(
            Modifier.fillMaxSize().testTag(LibraryTestTags.GRID),
            state = list,
            contentPadding = PaddingValues(
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding() + chrome.calculateBottomPadding() + LIST_END
            )
        ) {
            item(key = "header") {
                LibraryHeader(
                    state = state,
                    searching = searching,
                    onSearch = {
                        searching = !searching
                        if (!searching) actions.onQuery("")
                    },
                    actions = actions,
                    modifier = Modifier.reveal(0)
                )
            }
            if (searching) item(key = "search") { SearchField(state.query, actions.onQuery) }
            if (state.importing) {
                item(key = "importing") {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
                        color = FlowFolio.palette.title,
                        trackColor = FlowFolio.palette.edge
                    )
                }
            }
            libraryBody(state, actions)
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = insets.calculateBottomPadding() + chrome.calculateBottomPadding())
        )
    }
}

/** The list index of [shelfId]'s row, matching the items [LibraryContent] emits; null when it has no row. */
private fun rowIndex(state: LibraryUiState, searching: Boolean, shelfId: Long): Int? {
    val rows = shelfRows(state.items, state.shelves)
    val row = rows.indexOfFirst { it.shelf?.id == shelfId }.takeIf { it >= 0 } ?: return null
    val hero = state.continueReading != null && state.filter == LibraryFilter.ALL && state.query.isBlank()
    // Header, search field, import progress, chips and the hero card come before the rows.
    val before = listOf(true, searching, state.importing, true, hero).count { it }
    return before + row
}

private fun androidx.compose.foundation.lazy.LazyListScope.libraryBody(state: LibraryUiState, actions: LibraryActions) {
    when {
        state.loading -> Unit

        state.libraryEmpty -> item(key = "empty") { EmptyLibrary(actions.onImport) }

        else -> {
            item(key = "chips") { FilterChips(state.filter, actions.onFilter, Modifier.reveal(1)) }
            val hero = state.continueReading
            if (hero != null && state.filter == LibraryFilter.ALL && state.query.isBlank()) {
                item(key = "hero") { ContinueReadingCard(hero, actions, Modifier.reveal(2)) }
            }
            val rows = shelfRows(state.items, state.shelves)
            if (rows.isEmpty()) item(key = "nomatch") { NoMatch() }
            items(rows, key = { "row-" + (it.shelf?.id ?: "loose") }) { row ->
                ShelfRowView(
                    row,
                    noShelves = state.shelves.isEmpty(),
                    actions = actions,
                    modifier = Modifier.reveal(ROWS_REVEAL_ORDER)
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.library_empty),
            style = FlowFolioType.prose.merge(FlowFolio.hint),
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(LibraryTestTags.EMPTY)
        )
        FlowFolioPrimaryButton(
            text = stringResource(R.string.action_import),
            onClick = onImport,
            modifier = Modifier.padding(top = 20.dp)
        )
    }
}

@Composable
private fun NoMatch() {
    Text(
        stringResource(R.string.library_no_match),
        style = FlowFolioType.sub.merge(FlowFolio.hint),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp)
    )
}

private val LIST_END = 24.dp

/** Shelf rows reveal after the header, chips and hero card (orders 0 to 2). */
private const val ROWS_REVEAL_ORDER = 3

internal fun LibraryMessage.text(context: android.content.Context): String = when (this) {
    is LibraryMessage.Imported -> context.getString(R.string.msg_imported, title)

    is LibraryMessage.Duplicate -> context.getString(R.string.msg_duplicate, title)

    LibraryMessage.NotAPdf -> context.getString(R.string.msg_not_a_pdf)

    LibraryMessage.Unreadable -> context.getString(R.string.msg_unreadable)

    is LibraryMessage.NotEnoughSpace -> context.getString(
        R.string.msg_no_space,
        Formatter.formatShortFileSize(context, bytes)
    )

    LibraryMessage.Relinked -> context.getString(R.string.msg_relinked)

    LibraryMessage.RelinkDifferentFile -> context.getString(R.string.msg_relink_different)

    LibraryMessage.RelinkFailed -> context.getString(R.string.msg_relink_failed)

    is LibraryMessage.Deleted -> context.getString(R.string.msg_deleted, title)
}
