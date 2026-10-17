package com.flowfolio.feature.library

import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.EDGE_WIDTH
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.LibrarySort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PDF_TYPES = arrayOf("application/pdf")

/** Library route: wires the ViewModel, file pickers and snackbar messages to [LibraryContent]. */
@Composable
fun LibraryScreen(
    onOpenBook: (Book) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var relinkBookId by rememberSaveable { mutableStateOf<String?>(null) }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onFilePicked(it.toString()) }
    }
    val relinkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = relinkBookId
        if (uri != null && id != null) viewModel.onRelinkPicked(id, uri.toString())
        relinkBookId = null
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it.text(context)) }
    }

    LibraryContent(
        state = state,
        snackbar = snackbar,
        onImport = { importPicker.launch(PDF_TYPES) },
        onOpen = { item ->
            if (item.availability == Availability.MISSING) {
                relinkBookId = item.book.id
                relinkPicker.launch(PDF_TYPES)
            } else {
                viewModel.onBookOpened(item.book.id)
                onOpenBook(item.book)
            }
        },
        onDelete = viewModel::onRequestDelete,
        onLocate = { book ->
            relinkBookId = book.id
            relinkPicker.launch(PDF_TYPES)
        },
        onSort = viewModel::onSortChange,
        onSettings = onOpenSettings
    )
    LibraryDialogs(state.dialog, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(
    state: LibraryUiState,
    snackbar: SnackbarHostState,
    onImport: () -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onDelete: (Book) -> Unit,
    onLocate: (Book) -> Unit,
    onSort: (LibrarySort) -> Unit,
    onSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title), style = FlowFolio.title) },
                actions = {
                    SortMenu(state.sort, onSort)
                    IconButton(onClick = onSettings) {
                        FlowFolioGlyph(Glyph.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FlowFolio.palette.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImport,
                icon = { FlowFolioGlyph(Glyph.Add, contentDescription = null, color = FlowFolio.palette.title) },
                text = { Text(stringResource(R.string.action_import), style = FlowFolio.title) },
                containerColor = FlowFolio.palette.background,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .border(EDGE_WIDTH, FlowFolio.palette.edge, MaterialTheme.shapes.large)
                    .testTag(LibraryTestTags.IMPORT)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                state.loading -> Unit

                state.items.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.library_empty),
                        style = MaterialTheme.typography.bodyLarge.merge(FlowFolio.hint),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag(LibraryTestTags.EMPTY)
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 128.dp),
                    modifier = Modifier.fillMaxSize().testTag(LibraryTestTags.GRID)
                ) {
                    items(state.items, key = { it.book.id }) { item -> BookTile(item, onOpen, onDelete, onLocate) }
                }
            }
        }
    }
}

@Composable
private fun SortMenu(current: LibrarySort, onSort: (LibrarySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            FlowFolioGlyph(Glyph.Sort, contentDescription = stringResource(R.string.action_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                LibrarySort.RECENT to R.string.sort_recent,
                LibrarySort.TITLE to R.string.sort_title
            ).forEach { (sort, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(label),
                            style = if (sort == current) FlowFolio.title else FlowFolio.item
                        )
                    },
                    onClick = {
                        open = false
                        onSort(sort)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookTile(
    item: LibraryItem,
    onOpen: (LibraryItem) -> Unit,
    onDelete: (Book) -> Unit,
    onLocate: (Book) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .padding(8.dp)
            .combinedClickable(onClick = { onOpen(item) }, onLongClick = { menu = true })
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(COVER_ASPECT)) {
            Cover(item.book)
            if (item.availability == Availability.MISSING) {
                Badge(Modifier.align(Alignment.TopEnd).padding(4.dp), containerColor = FlowFolio.palette.subItem) {
                    Text(stringResource(R.string.badge_missing))
                }
            } else if (item.book.passwordProtected) {
                Badge(Modifier.align(Alignment.TopEnd).padding(4.dp), containerColor = FlowFolio.palette.subItem) {
                    Text(stringResource(R.string.badge_locked))
                }
            }
        }
        Text(
            item.book.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.merge(FlowFolio.item),
            modifier = Modifier.padding(top = 6.dp)
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (item.book.storage is BookStorage.Linked) {
                DropdownMenuItem(text = { MenuText(R.string.action_locate) }, onClick = {
                    menu = false
                    onLocate(item.book)
                })
            }
            DropdownMenuItem(text = { MenuText(R.string.action_delete, warn = true) }, onClick = {
                menu = false
                onDelete(item.book)
            })
        }
    }
}

/** A menu entry: the title colour, or the sub-item colour for destructive entries. */
@Composable
private fun MenuText(label: Int, warn: Boolean = false) {
    Text(stringResource(label), style = if (warn) FlowFolio.subItem else FlowFolio.title)
}

@Composable
private fun Cover(book: Book) {
    val image by produceState<ImageBitmap?>(null, book.coverPath) {
        value =
            book.coverPath?.let { path ->
                withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
            }
    }
    val bitmap = image
    if (bitmap != null) {
        Image(
            bitmap,
            contentDescription = book.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            FlowFolioGlyph(Glyph.Book, contentDescription = null, size = 48.dp)
        }
    }
}

private const val COVER_ASPECT = 0.72f

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
