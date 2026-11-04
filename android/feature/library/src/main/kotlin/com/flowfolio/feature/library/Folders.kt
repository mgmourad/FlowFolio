package com.flowfolio.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Selection
import com.flowfolio.core.designsystem.jiggle
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.model.Book

/**
 * A folder in the Folders view (owner, 2026-10-26). Folders are not stored on their own: they are the [Book.folder]
 * paths of books imported with a folder, so a folder is there while it holds books, at any depth.
 */
data class FolderEntry(
    /** The whole path, the imported folder's name first ("Books/Physics"). */
    val path: String,
    val name: String,
    /** Books in it or in any folder inside it. */
    val bookCount: Int,
    /** Folders directly inside it. */
    val folderCount: Int
) {
    /** Its id in select mode, apart from book ids. */
    val selectionId: String get() = FOLDER_PREFIX + path

    companion object {
        const val FOLDER_PREFIX = "folder:"
    }
}

/** The folders directly inside [parent] (null: the imported folders themselves), by name. */
fun subfolders(books: List<Book>, parent: String?): List<FolderEntry> {
    val prefix = parent?.let { "$it/" } ?: ""
    val under = books.mapNotNull { b -> b.folder?.takeIf { parent == null || it.startsWith(prefix) } }
    return under.groupBy { it.removePrefix(prefix).substringBefore('/') }
        .filterKeys { it.isNotEmpty() }
        .map { (name, paths) ->
            val path = prefix + name
            FolderEntry(
                path = path,
                name = name,
                bookCount = paths.size,
                folderCount = paths.mapNotNull { p ->
                    p.removePrefix("$path/").takeIf { p.startsWith("$path/") }?.substringBefore('/')
                }.distinct().size
            )
        }
        .sortedBy { it.name.lowercase() }
}

/** Whether [book] is in the folder [path] or any folder inside it. */
fun Book.isUnder(path: String): Boolean = folder == path || folder?.startsWith("$path/") == true

/** The Folders chip on the library: the imported folders, each opening its own page. */
internal fun LazyListScope.folderRoots(state: LibraryUiState, actions: LibraryActions) {
    val roots = subfolders(state.items.map { it.book }, null)
    if (roots.isEmpty()) {
        item(key = "nofolders") { NoFolders(actions.onImportFolder) }
        return
    }
    itemsIndexed(roots, key = { _, f -> "folder-" + f.path }) { i, folder ->
        Box(Modifier.padding(horizontal = 18.dp, vertical = 5.dp).animateItem().reveal(2 + i)) {
            FolderRow(
                folder,
                FolderActions(
                    onOpen = actions.onOpenFolder,
                    onRemove = actions.onRemoveFolder,
                    onMakeShelves = actions.onFolderToShelves
                )
            )
        }
    }
}

/** What a folder in the Folders view can do. */
internal data class FolderActions(
    val onOpen: (String) -> Unit,
    /** Asks to remove the folder's books from the library. */
    val onRemove: (FolderEntry) -> Unit,
    /** Makes a shelf from this folder and from every folder inside it. */
    val onMakeShelves: (FolderEntry) -> Unit = {},
    val selection: Selection? = null
)

/** A folder as a list row: its glyph, name and what it holds; press and hold for its menu. */
@Composable
internal fun FolderRow(folder: FolderEntry, actions: FolderActions) {
    FolderPress(folder, actions) {
        FlowFolioCard(Modifier.fillMaxWidth(), radius = 18.dp, padding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FolderBadge(Modifier.size(ROW_BADGE))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(
                        folder.name,
                        style = FlowFolioType.rowLabel.merge(FlowFolio.item),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(contents(folder), style = FlowFolioType.rowDetail.merge(FlowFolio.hint), maxLines = 1)
                }
                Text("›", style = FlowFolioType.sectionTitle.merge(FlowFolio.hint))
            }
        }
    }
}

/** A folder as a tile, the size of a book tile, with its name and what it holds under it. */
@Composable
internal fun FolderTile(folder: FolderEntry, actions: FolderActions) {
    Column(Modifier.width(TILE_CELL)) {
        FolderPress(folder, actions) {
            FolderBadge(Modifier.width(TILE_BOOK_WIDTH).height(TILE_BOOK_HEIGHT))
        }
        Text(
            folder.name,
            style = FlowFolioType.rowLabel.merge(FlowFolio.title),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(TILE_BOOK_WIDTH).padding(top = 10.dp)
        )
        Text(
            contents(folder),
            style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(TILE_BOOK_WIDTH).padding(top = 2.dp)
        )
    }
}

/** The folder glyph on a soft panel in the theme's colours. */
@Composable
private fun FolderBadge(modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(FlowFolio.palette.item.copy(alpha = BADGE_SHADE)),
        contentAlignment = Alignment.Center
    ) {
        FlowFolioGlyph(Glyph.Folder, contentDescription = null, color = FlowFolio.palette.item, size = 34.dp)
    }
}

/** Tap opens the folder (or chooses it in select mode); press and hold offers its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderPress(folder: FolderEntry, actions: FolderActions, content: @Composable () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val selection = actions.selection
    val selecting = selection?.active == true
    Box(
        Modifier
            .jiggle(selecting, folder.selectionId)
            .semantics { contentDescription = folder.name }
            .testTag(LibraryTestTags.folder(folder.path))
            .combinedClickable(
                onClick = {
                    if (selection != null &&
                        selecting
                    ) {
                        selection.toggle(folder.selectionId)
                    } else {
                        actions.onOpen(folder.path)
                    }
                },
                onLongClick = { if (!selecting) menu = true }
            )
    ) {
        content()
        selection?.let { SelectionMark(it, folder.selectionId) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            selection?.let {
                HoldEntry(R.string.action_select) {
                    menu = false
                    it.startWith(folder.selectionId)
                }
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open), style = FlowFolio.title) },
                onClick = {
                    menu = false
                    actions.onOpen(folder.path)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_folder_to_shelves), style = FlowFolio.title) },
                onClick = {
                    menu = false
                    actions.onMakeShelves(folder)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_remove_folder), style = FlowFolio.subItem) },
                onClick = {
                    menu = false
                    actions.onRemove(folder)
                }
            )
        }
    }
}

@Composable
private fun contents(folder: FolderEntry): String = listOfNotNull(
    pluralStringResource(R.plurals.book_count, folder.bookCount, folder.bookCount),
    folder.folderCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.folder_count, it, it) }
).joinToString(" · ")

@Composable
private fun NoFolders(onImportFolder: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.folders_empty),
            style = FlowFolioType.sub.merge(FlowFolio.hint),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        com.flowfolio.core.designsystem.FlowFolioGhostButton(stringResource(R.string.import_folder), onImportFolder)
    }
}

private val ROW_BADGE = 52.dp
private const val BADGE_SHADE = 0.14f
