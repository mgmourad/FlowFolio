package com.flowfolio.feature.library

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.data.DefaultBookTagRepository
import com.flowfolio.core.designsystem.ButtonStyle
import com.flowfolio.core.designsystem.DialogAction
import com.flowfolio.core.designsystem.DialogField
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioChip
import com.flowfolio.core.designsystem.FlowFolioDialog
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioProgressBar
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.tint
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.core.model.ReadingIntent
import com.flowfolio.core.model.pageLabel
import kotlin.math.roundToInt

/**
 * The Overview's chips (`.chips`, owner 2026-11-10): the book's shelves with their colour (tap to open one), the
 * reading intentions (one at a time), the book's own tags (tap to remove), and "+ Tag".
 */
@Composable
internal fun BookChips(state: DetailsUiState, book: Book, actions: DetailsActions, modifier: Modifier) {
    var adding by remember { mutableStateOf(false) }
    val p = FlowFolio.palette
    FlowRow(
        modifier.fillMaxWidth().testTag(LibraryTestTags.INTENT_CHIPS),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.bookShelves.forEach { shelf ->
            val dot = shelf.color?.tint(p.dark) ?: p.accent
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, p.line, RoundedCornerShape(50))
                    .clickable { actions.onShelf(shelf.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(Modifier.size(9.dp).background(dot, CircleShape))
                Text(shelf.name, style = FlowFolioType.chip.copy(color = p.ink2))
            }
        }
        ReadingIntent.entries.forEach { intent ->
            val on = intent == book.intent
            FlowFolioChip(stringResource(intent.label), on, { actions.onIntent(if (on) null else intent) })
        }
        state.tags.forEach { tag ->
            val remove = stringResource(R.string.tag_remove, tag)
            FlowFolioChip(
                "#$tag",
                selected = false,
                onClick = { actions.onRemoveTag(tag) },
                modifier = Modifier.semantics { contentDescription = remove }
            )
        }
        FlowFolioChip(stringResource(R.string.tag_add), false, { adding = true }, glyph = Glyph.Add)
    }
    if (adding) {
        TagDialog(
            suggestions = state.allTags.filter { it !in state.tags },
            onAdd = {
                actions.onAddTag(it)
                adding = false
            },
            onDismiss = { adding = false }
        )
    }
}

/** "+ Tag": a word or two of the reader's own, with the library's other tags offered as one-tap suggestions. */
@Composable
private fun TagDialog(suggestions: List<String>, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    FlowFolioDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.tag_add_title),
        body = stringResource(R.string.tag_add_body),
        glyph = Glyph.Add,
        field = DialogField(text, {
            text = it.take(DefaultBookTagRepository.MAX_LENGTH)
        }, stringResource(R.string.tag_hint)),
        extra = if (suggestions.isEmpty()) {
            null
        } else {
            {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.take(MAX_SUGGESTIONS).forEach { s -> FlowFolioChip("#$s", false, { onAdd(s) }) }
                }
            }
        },
        actions = listOf(
            DialogAction(stringResource(R.string.action_cancel)) { onDismiss() },
            DialogAction(
                stringResource(R.string.tag_add_confirm),
                ButtonStyle.PRIMARY,
                enabled = text.isNotBlank()
            ) {
                onAdd(text)
            }
        )
    )
}

/** `.flowbox`: what Dynamic Flow will do for this PDF, or that it is set to. */
@Composable
internal fun FlowBox(book: Book, modifier: Modifier) {
    val p = FlowFolio.palette
    Row(
        modifier
            .fillMaxWidth()
            .background(p.surface2, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FlowFolioGlyph(Glyph.DynamicFlow, null, color = p.accent, size = 20.dp, glow = false)
        val on = book.flowMode == true
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = p.ink)) {
                    append(stringResource(if (on) R.string.details_flow_on_title else R.string.details_flow_off_title))
                }
                append(" ")
                append(stringResource(if (on) R.string.details_flow_on_body else R.string.details_flow_off_body))
            },
            style = FlowFolioType.body.copy(fontSize = 13.5.sp, lineHeight = 20.sp, color = p.ink2)
        )
    }
}

private const val MAX_SUGGESTIONS = 8
