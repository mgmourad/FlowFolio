package com.flowfolio.feature.library

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.Glyph

/**
 * A shortcut button on the Continue Reading card that lights up and glows when [checked] is true (owner, M3T2).
 *
 * [checkedGlyph] is drawn in place of [glyph] while it is on, so a toggle can fill in as well as light up (the
 * favourite's star, owner M4), and [checkedColor] / [uncheckedColor] override its colours -- the finished tick
 * is the palette's green either way, bright and glowing when the book is done and muted when it is not, so it
 * never reads as a dim grey tick (owner, M4b).
 */
@Composable
internal fun ToggleShortcut(
    glyph: Glyph,
    checked: Boolean,
    @StringRes label: Int,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    checkedGlyph: Glyph = glyph,
    checkedColor: Color? = null,
    /** The colour while it is off; the dimmed hint colour when not given. */
    uncheckedColor: Color? = null,
    onClick: () -> Unit
) {
    val p = FlowFolio.palette
    FlowFolioIconButton(onClick = onClick, modifier = modifier, size = size, tooltip = stringResource(label)) {
        FlowFolioGlyph(
            if (checked) checkedGlyph else glyph,
            contentDescription = stringResource(label),
            color = if (checked) {
                checkedColor ?: p.ink
            } else {
                (uncheckedColor ?: p.ink2).copy(alpha = OFF_ALPHA)
            },
            glow = checked
        )
    }
}

private const val OFF_ALPHA = 0.55f
