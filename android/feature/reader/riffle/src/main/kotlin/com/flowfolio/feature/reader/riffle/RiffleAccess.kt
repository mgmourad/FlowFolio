package com.flowfolio.feature.reader.riffle

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.roundToInt

/**
 * The strip for screen readers and keyboards (spec 6): a slider over the book's units whose state reads "Page 216 of
 * 842, Chapter 7 · Membrane Transport", with actions for the next and previous spread and chapter, Go to page, and one
 * per bookmark. Focused, the arrow keys step one unit (mirrored for right-to-left books) and Page Up and Page Down ten.
 */
@Composable
internal fun Modifier.riffleAccess(state: RiffleScrubberState, events: RiffleEvents): Modifier {
    val book = state.book
    val unit = state.currentUnit
    val last = (book.unitCount - 1).coerceAtLeast(0)
    val go = { to: Int ->
        val target = to.coerceIn(0, last)
        if (target != unit) events.onGoTo(target)
        true
    }
    val chapters = book.sections.filter { it.kind == SectionKind.CHAPTER }.map { book.unitOf(it.firstPage) }
    val description = listOf(
        stringResource(R.string.riffle_state, book.label(book.pagesOf(unit).first), book.pageCount),
        RiffleLabels.section(book, unit)
    ).filter { it.isNotEmpty() }.joinToString(", ")
    val spreads = book.unitCount < book.pageCount
    val next = if (spreads) R.string.riffle_next_unit else R.string.riffle_next
    val previous = if (spreads) R.string.riffle_previous_unit else R.string.riffle_previous
    val actions = listOf(
        CustomAccessibilityAction(stringResource(next)) { go(unit + 1) },
        CustomAccessibilityAction(stringResource(previous)) { go(unit - 1) },
        CustomAccessibilityAction(stringResource(R.string.riffle_next_chapter)) {
            chapters.firstOrNull { it > unit }?.let(go) ?: false
        },
        CustomAccessibilityAction(stringResource(R.string.riffle_previous_chapter)) {
            chapters.lastOrNull { it < unit }?.let(go) ?: false
        },
        CustomAccessibilityAction(stringResource(R.string.riffle_open_go_to)) {
            state.goToOpen = true
            true
        }
    ) + state.flags.map { flag ->
        val label = listOfNotNull(stringResource(R.string.riffle_bookmark, book.label(flag.page)), flag.note)
        CustomAccessibilityAction(label.joinToString(", ")) {
            events.onFlag(flag)
            true
        }
    }
    val forward = if (book.rightToLeft) Key.DirectionLeft else Key.DirectionRight
    val back = if (book.rightToLeft) Key.DirectionRight else Key.DirectionLeft
    return semantics {
        stateDescription = description
        progressBarRangeInfo =
            ProgressBarRangeInfo(unit.toFloat(), 0f..last.toFloat(), steps = (last - 1).coerceAtLeast(0))
        setProgress { value -> go(value.roundToInt()) }
        customActions = actions
    }.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (event.key) {
            forward -> go(unit + 1)
            back -> go(unit - 1)
            Key.PageDown -> go(unit + KEY_JUMP)
            Key.PageUp -> go(unit - KEY_JUMP)
            else -> false
        }
    }.focusable()
}

private const val KEY_JUMP = 10
