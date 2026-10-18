package com.flowfolio.feature.reader

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.color
import com.flowfolio.core.model.BookmarkColor

/**
 * The ribbon button (`#rMark`): outlined when the page has no ribbon, in the ribbon's colour when it has one. A tap
 * opens a strip of the app's colours ([ColourStrip]) to put a ribbon on the page (or recolour it) and, when there is
 * one, to remove it.
 */
@Composable
internal fun BookmarkButton(mark: ChromeMark, actions: ChromeActions) {
    FlowFolioIconButton(
        onClick = { actions.onPanel(Panel.MARK) },
        glass = true,
        modifier = Modifier.testTag(ReaderTestTags.BOOKMARK)
    ) {
        FlowFolioGlyph(
            Glyph.Bookmark,
            stringResource(if (mark.color != null) R.string.reader_bookmarked else R.string.reader_bookmark),
            color = mark.color?.color ?: FlowFolio.palette.subItem,
            glow = mark.color != null
        )
    }
}

/** The colour choices, the current one ringed, then a remove button when the page has a ribbon. */
@Composable
internal fun ColourStrip(mark: ChromeMark, onChoose: (BookmarkColor?) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(ReaderTestTags.BOOKMARK_COLOURS),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookmarkColor.entries.forEach { c ->
            val colour = c.color
            val ring = FlowFolio.palette.text
            Box(
                Modifier
                    .size(SWATCH)
                    .semantics {
                        contentDescription = c.name
                        selected = c == mark.color
                    }
                    .clip(CircleShape)
                    .drawBehind { drawCircle(colour) }
                    .then(if (c == mark.color) Modifier.border(2.5.dp, ring, CircleShape) else Modifier)
                    .clickable { onChoose(c) }
            )
        }
        if (mark.color != null) {
            Box(
                Modifier.size(SWATCH).clip(CircleShape).clickable { onChoose(null) },
                contentAlignment = Alignment.Center
            ) {
                FlowFolioGlyph(
                    Glyph.Trash,
                    stringResource(R.string.reader_bookmark_remove),
                    color = FlowFolio.palette.hint,
                    size = 22.dp,
                    glow = false
                )
            }
        }
    }
}

private val SWATCH = 32.dp
