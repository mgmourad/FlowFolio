package com.flowfolio.feature.reader

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.glass

/**
 * An EPUB footnote or endnote in a popover instead of a jump (phase 2 plan, goal 4): a glass card next to the tapped
 * reference, below it in the upper half of the screen and above it in the lower half, with the note's bold and italics.
 * Long notes scroll. A tap outside or Back closes it; so does a page turn (the reader clears [note]).
 */
@Composable
internal fun BoxScope.FootnotePopover(note: ShownNote?, text: (String) -> CharSequence?, onDismiss: () -> Unit) {
    val shown = note ?: return
    val body = remember(shown.href) { text(shown.href)?.let(::noteText) }
    if (body == null) {
        LaunchedEffect(shown) { onDismiss() }
        return
    }
    BackHandler(onBack = onDismiss)
    Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { onDismiss() } })
    val title = stringResource(R.string.reader_footnote)
    Column(
        Modifier
            .placeNear(shown.at)
            .glass(RoundedCornerShape(CORNER))
            // Taps on the card itself stay on it.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics {
                paneTitle = title
                liveRegion = LiveRegionMode.Polite
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag(ReaderTestTags.FOOTNOTE)
    ) {
        Text(
            title.uppercase(),
            style = FlowFolioType.eyebrow.merge(FlowFolio.subItem),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(body, style = FlowFolioType.rowDetail.merge(FlowFolio.item))
    }
}

/**
 * Places the card [GAP] below [at] in the upper half of the area and [GAP] above it in the lower half, centred on it
 * but kept [MARGIN] from the edges, at most [MAX_WIDTH] wide and [MAX_HEIGHT_FRACTION] of the area tall.
 */
private fun Modifier.placeNear(at: Offset): Modifier = layout { measurable, constraints ->
    val margin = MARGIN.roundToPx()
    val gap = GAP.roundToPx()
    val width = minOf(constraints.maxWidth - 2 * margin, MAX_WIDTH.roundToPx()).coerceAtLeast(0)
    val card = measurable.measure(
        Constraints(maxWidth = width, maxHeight = (constraints.maxHeight * MAX_HEIGHT_FRACTION).toInt())
    )
    layout(constraints.maxWidth, constraints.maxHeight) {
        val x = (at.x.toInt() - card.width / 2).coerceIn(
            margin,
            (constraints.maxWidth - margin - card.width).coerceAtLeast(margin)
        )
        val below = at.y < constraints.maxHeight / 2
        val y = if (below) at.y.toInt() + gap else at.y.toInt() - gap - card.height
        card.place(x, y.coerceIn(margin, (constraints.maxHeight - margin - card.height).coerceAtLeast(margin)))
    }
}

/** The note's text with its bold and italic runs; other styling (links, sizes) is dropped. */
internal fun noteText(text: CharSequence): AnnotatedString = buildAnnotatedString {
    append(text.toString().trim())
    val spanned = text as? Spanned ?: return@buildAnnotatedString
    val lead = text.length - text.trimStart().length
    for (span in spanned.getSpans(0, text.length, StyleSpan::class.java)) {
        val style = when (span.style) {
            Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            Typeface.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            else -> null
        } ?: continue
        val start = (spanned.getSpanStart(span) - lead).coerceIn(0, length)
        val end = (spanned.getSpanEnd(span) - lead).coerceIn(start, length)
        if (end > start) addStyle(style, start, end)
    }
}

private val CORNER = 18.dp
private val MARGIN = 16.dp
private val GAP = 28.dp
private val MAX_WIDTH = 520.dp
private const val MAX_HEIGHT_FRACTION = 0.45f
