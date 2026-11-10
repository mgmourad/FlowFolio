package com.flowfolio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flowfolio.core.data.Release
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.glass

/**
 * The automatic update check's notice (Phase 4 M5): a glass card that drops from the top over every screen, saying a
 * newer build is available. A tap opens Settings at the update section; the close glyph dismisses it until the next
 * check finds a build again.
 */
@Composable
internal fun UpdateBanner(release: Release, onOpen: () -> Unit, onDismiss: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible,
            enter = slideInVertically(tween(Motion.SLOW_MS, easing = Motion.Spring)) { -it * 2 } +
                fadeIn(tween(Motion.MEDIUM_MS)),
            exit = slideOutVertically(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) { -it * 2 } +
                fadeOut(tween(Motion.MEDIUM_MS))
        ) {
            val shape = RoundedCornerShape(22.dp)
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(max = MAX_WIDTH)
                    .background(FlowFolio.palette.surface2.copy(alpha = FILL_ALPHA), shape)
                    .glass(shape)
                    .clickable {
                        visible = false
                        onOpen()
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowFolioGlyph(Glyph.Settings, contentDescription = null, size = 24.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        stringResource(R.string.update_banner_title),
                        style = FlowFolioType.rowLabel.merge(FlowFolio.title),
                        maxLines = 1
                    )
                    Text(
                        stringResource(R.string.update_banner_text, release.versionName),
                        style = FlowFolioType.rowDetail.merge(FlowFolio.item),
                        maxLines = 1
                    )
                }
                FlowFolioGlyph(
                    Glyph.Back,
                    stringResource(R.string.update_banner_dismiss),
                    color = FlowFolio.palette.ink2,
                    size = 18.dp,
                    glow = false,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clickable {
                            visible = false
                            onDismiss()
                        }
                )
            }
        }
    }
}

private val MAX_WIDTH = 480.dp
private const val FILL_ALPHA = 0.9f
