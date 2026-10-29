package com.flowfolio.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.DynamicFlowPrompt
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.FlowCarry
import kotlinx.coroutines.flow.StateFlow

/**
 * Turning a PDF's Dynamic Flow on or off from a book's menu or the list's swipe (ADR-0045): the same questions as the
 * reader's switch (convert the changes, or not, and don't show this again). Returns what the menu calls with the book.
 */
@Composable
internal fun rememberDynamicFlowSwitch(
    carry: StateFlow<FlowCarry>,
    onSwitch: (Book, Boolean, Boolean) -> Unit,
    onRemember: (FlowCarry) -> Unit
): (Book) -> Unit {
    var book by remember { mutableStateOf<Book?>(null) }
    val asking by carry.collectAsStateWithLifecycle()
    DynamicFlowSwitch(book, asking, onSwitch, onRemember) { book = null }
    return { book = it }
}

@Composable
private fun DynamicFlowSwitch(
    book: Book?,
    carry: FlowCarry,
    onSwitch: (Book, Boolean, Boolean) -> Unit,
    onRemember: (FlowCarry) -> Unit,
    onClose: () -> Unit
) {
    DynamicFlowPrompt(
        toFlow = book?.let { it.flowMode != true },
        carry = carry,
        onSwitch = { on, bring -> book?.let { onSwitch(it, on, bring) } },
        onRemember = onRemember,
        onClose = onClose
    )
}

/** "Ribbon · p. 12"; a Dynamic Flow ribbon has no printed page: "Ribbon · Dynamic Flow". */
@Composable
internal fun ribbonKind(item: BookmarkItem): String = if (item.label.isEmpty()) {
    stringResource(
        R.string.bookmarks_kind_flow
    )
} else {
    stringResource(R.string.bookmarks_kind, item.label)
}
