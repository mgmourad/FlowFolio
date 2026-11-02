package com.flowfolio.feature.library

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.Glyph

/** A shortcut button on the Continue Reading card that lights up and glows when [checked] is true (owner, M3T2). */
@Composable
internal fun ToggleShortcut(
    glyph: Glyph,
    checked: Boolean,
    @StringRes label: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val p = FlowFolio.palette
    FlowFolioIconButton(onClick = onClick, modifier = modifier) {
        FlowFolioGlyph(
            glyph,
            contentDescription = stringResource(label),
            color = if (checked) p.title else p.hint.copy(alpha = OFF_ALPHA),
            glow = checked
        )
    }
}

private const val OFF_ALPHA = 0.55f
