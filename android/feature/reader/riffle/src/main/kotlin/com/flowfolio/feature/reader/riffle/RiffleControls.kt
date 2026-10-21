package com.flowfolio.feature.reader.riffle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.UiFamily
import kotlinx.coroutines.delay

/**
 * The nudge arrows (spec 3.2): for a few seconds after the ribbon is let go, ‹ and › beside it step one unit back or
 * forward; each tap starts the few seconds again. The chrome's countdown waits while they show.
 */
@Composable
internal fun NudgeArrows(state: RiffleScrubberState, events: RiffleEvents) {
    var token by remember { mutableStateOf(0) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(state.nudges, token) {
        if (state.nudges == 0) return@LaunchedEffect
        shown = true
        delay(NUDGE_MS)
        shown = false
    }
    DisposableEffect(shown) {
        events.onPause(ChromePause.NUDGE, shown)
        onDispose { if (shown) events.onPause(ChromePause.NUDGE, false) }
    }
    AnimatedVisibility(shown, enter = fadeIn(), exit = fadeOut()) {
        // Placed (not just drawn) beside the ribbon, inside the strip's bounds, so the arrows receive their taps.
        Row(
            Modifier.offset {
                val g = state.geometry
                if (g == null) {
                    IntOffset.Zero
                } else {
                    val half = (ARROWS_WIDTH.dp.toPx() / 2)
                    IntOffset(
                        (state.pinX - half).toInt(),
                        ((g.bottom + g.ribbonBottom) / 2 - ARROW.dp.toPx() / 2).toInt()
                    )
                }
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            val back = if (state.book.rightToLeft) 1 else -1
            Arrow("‹", stringResource(R.string.riffle_previous)) {
                token++
                events.onInteraction()
                events.onGoTo((state.currentUnit + back).coerceIn(0, state.book.unitCount - 1))
            }
            // Room for the ribbon between the arrows.
            Spacer(Modifier.width(RIBBON_GAP.dp))
            Arrow("›", stringResource(R.string.riffle_next)) {
                token++
                events.onInteraction()
                events.onGoTo((state.currentUnit - back).coerceIn(0, state.book.unitCount - 1))
            }
        }
    }
}

@Composable
private fun Arrow(glyph: String, label: String, onClick: () -> Unit) {
    val palette = FlowFolio.palette
    Box(
        Modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(palette.surface2, CircleShape)
                .border(1.dp, palette.edge, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                glyph,
                color = palette.item,
                style = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            )
        }
    }
}

/**
 * The go-to-page field (spec 3.2): a printed page number ("216", or "xiv" in front matter when the book has a page
 * list), above the strip header. A number the book does not have says so instead of jumping.
 */
@Composable
internal fun GoToPageField(state: RiffleScrubberState, events: RiffleEvents) {
    if (!state.goToOpen) return
    var text by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    DisposableEffect(Unit) {
        events.onPause(ChromePause.GO_TO_PAGE, true)
        onDispose { events.onPause(ChromePause.GO_TO_PAGE, false) }
    }
    val close = { state.goToOpen = false }
    Popup(
        popupPositionProvider = AboveAnchor,
        onDismissRequest = close,
        properties = PopupProperties(focusable = true)
    ) {
        val palette = FlowFolio.palette
        val style =
            TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = palette.text)
        Row(
            Modifier
                .background(palette.surface, RoundedCornerShape(16.dp))
                .border(1.dp, palette.edge, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.riffle_go_to),
                color = palette.hint,
                style = style.copy(fontWeight = FontWeight.SemiBold)
            )
            LabelField(state.book, text, focus, style, onText = {
                text = it.take(MAX_LABEL)
                wrong = false
            }) {
                val page = state.book.pageOfLabel(text)
                if (page == null) {
                    wrong = true
                } else {
                    close()
                    events.onGoTo(state.book.unitOf(page))
                }
            }
            if (wrong) {
                Text(
                    stringResource(R.string.riffle_no_such_page),
                    color = palette.subItem,
                    style = style.copy(fontSize = 12.sp)
                )
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}

/** The text field of [GoToPageField]: one line, a number keyboard for numbered books, Go submits. */
@Composable
private fun LabelField(
    book: BookGeometry,
    text: String,
    focus: FocusRequester,
    style: TextStyle,
    onText: (String) -> Unit,
    onGo: () -> Unit
) {
    BasicTextField(
        value = text,
        onValueChange = onText,
        modifier = Modifier.width(72.dp).focusRequester(focus),
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(FlowFolio.palette.title),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = if (numbersOnly(book)) KeyboardType.Number else KeyboardType.Ascii,
            imeAction = ImeAction.Go
        ),
        keyboardActions = KeyboardActions(onGo = { onGo() })
    )
}

/** The field opens centred in the upper part of the window, clear of the keyboard, which covers the strip. */
private object AboveAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ) = IntOffset(
        ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0),
        (windowSize.height * FIELD_HEIGHT_FRACTION).toInt()
    )
}

/** A number keyboard, unless the book's first pages are numbered with letters (roman front matter). */
private fun numbersOnly(book: BookGeometry): Boolean =
    (0 until minOf(book.pageCount, FRONT_MATTER_PAGES)).all { book.label(it).all(Char::isDigit) }

private const val NUDGE_MS = 3_000L
private const val ARROW = 48
private const val RIBBON_GAP = 20
private const val ARROWS_WIDTH = ARROW * 2 + RIBBON_GAP
private const val MAX_LABEL = 12
private const val FIELD_HEIGHT_FRACTION = 0.22f
private const val FRONT_MATTER_PAGES = 40
