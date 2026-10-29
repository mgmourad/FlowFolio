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
internal fun rememberDynamicFlowSwitch(screen: FlowSwitching): (Book) -> Unit {
    var book by remember { mutableStateOf<Book?>(null) }
    val asking by screen.flowCarry.collectAsStateWithLifecycle()
    DynamicFlowSwitch(book, asking, screen::hasNotes, screen::setFlow, screen::setFlowCarry) { book = null }
    return { book = it }
}

/** What a screen whose books can switch to Dynamic Flow offers its switch (the library and shelf pages). */
internal interface FlowSwitching {
    /** Whether switching asks first, or what was answered for good (ADR-0045). */
    val flowCarry: StateFlow<FlowCarry>

    /** Whether [book] has highlights, notes or bookmarks in its Dynamic Flow ([inFlow]) or its PDF. */
    suspend fun hasNotes(book: Book, inFlow: Boolean): Boolean

    /** Turns [book]'s Dynamic Flow on or off, bringing the changes across or not. */
    fun setFlow(book: Book, on: Boolean, carry: Boolean)

    fun setFlowCarry(carry: FlowCarry)
}

// The book being switched and the prompt's own parameters.
@Suppress("LongParameterList")
@Composable
private fun DynamicFlowSwitch(
    book: Book?,
    carry: FlowCarry,
    hasNotes: suspend (Book, Boolean) -> Boolean,
    onSwitch: (Book, Boolean, Boolean) -> Unit,
    onRemember: (FlowCarry) -> Unit,
    onClose: () -> Unit
) {
    DynamicFlowPrompt(
        toFlow = book?.let { it.flowMode != true },
        carry = carry,
        hasNotes = { inFlow -> book?.let { hasNotes(it, inFlow) } ?: false },
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
