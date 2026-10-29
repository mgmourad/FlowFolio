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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.model.FlowNotice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * Dynamic Flow's notices in the app (ADR-0045), over every screen, the reader's included: a glass card that drops from
 * the top with the Dynamic Flow glyph, says what happened, and leaves after a few seconds or when tapped. "Ready" opens
 * the book when tapped. The phone's own notification says the same outside the app.
 */
@Composable
internal fun FlowNoticeHost(notices: Flow<FlowNotice>, onOpen: (String) -> Unit) {
    var shown by remember { mutableStateOf<FlowNotice?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(notices) {
        notices.collect { notice ->
            shown = notice
            visible = true
        }
    }
    LaunchedEffect(shown) {
        if (shown != null) {
            delay(SHOWN_MS)
            visible = false
        }
    }
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible,
            enter = slideInVertically(tween(Motion.SLOW_MS, easing = Motion.Spring)) { -it * 2 } +
                fadeIn(tween(Motion.MEDIUM_MS)),
            exit = slideOutVertically(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) { -it * 2 } +
                fadeOut(tween(Motion.MEDIUM_MS))
        ) {
            shown?.let { notice ->
                NoticeCard(notice) {
                    visible = false
                    if (notice is FlowNotice.Done) onOpen(notice.bookId)
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(notice: FlowNotice, onTap: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = MAX_WIDTH)
            // The reader isn't a glass source (nothing behind it is blurred), so the card brings its own fill: the
            // banner reads cleanly over the reader's bars and pages as well as over the blurred library.
            .background(FlowFolio.palette.surface2.copy(alpha = FILL_ALPHA), shape)
            .glass(shape)
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(FLOW_NOTICE_TAG),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlowFolioGlyph(Glyph.DynamicFlow, contentDescription = null, size = 24.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(titleOf(notice)), style = FlowFolioType.rowLabel.merge(FlowFolio.title), maxLines = 1)
            Text(textOf(notice), style = FlowFolioType.rowDetail.merge(FlowFolio.item), maxLines = 2)
        }
    }
}

private fun titleOf(notice: FlowNotice): Int = when (notice) {
    is FlowNotice.Started -> R.string.flow_notice_started
    is FlowNotice.Done -> R.string.flow_notice_done
    is FlowNotice.Failed -> R.string.flow_notice_failed
    is FlowNotice.CarryStarted -> R.string.flow_notice_carrying
    is FlowNotice.Carried -> R.string.flow_notice_carried
}

@Composable
private fun textOf(notice: FlowNotice): String = when (notice) {
    is FlowNotice.Started -> stringResource(R.string.flow_notice_started_text, notice.title)
    is FlowNotice.Done -> stringResource(R.string.flow_notice_done_text, notice.title)
    is FlowNotice.Failed -> stringResource(R.string.flow_notice_failed_text, notice.title)
    is FlowNotice.CarryStarted -> stringResource(R.string.flow_notice_carrying_text, notice.title)
    is FlowNotice.Carried -> stringResource(R.string.flow_notice_carried_text, notice.carried, notice.total)
}

internal const val FLOW_NOTICE_TAG = "flow_notice"
private const val SHOWN_MS = 4_500L
private const val FILL_ALPHA = 0.94f
private val MAX_WIDTH = 520.dp
