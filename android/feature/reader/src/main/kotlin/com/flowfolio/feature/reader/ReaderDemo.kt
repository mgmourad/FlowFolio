package com.flowfolio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGhostButton
import com.flowfolio.core.designsystem.FlowFolioPalette
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioType

/** The guide id the reader remembers having shown (owner, 2026-11-07). */
internal const val READER_DEMO = "reader_demo"

/**
 * The demo shown the first time a reader opens a book (owner, 2026-11-07): the tap zones for turning pages and the
 * menus, and the riffle bar. "Got it" dismisses; "Never show tips again" turns every first-use tip off.
 */
@Composable
internal fun BoxScope.ReaderDemo(onDone: () -> Unit, onNeverShow: () -> Unit) {
    FlowFolioPalette(dark = true) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM))) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = DEMO_TEXT_WIDTH)
                    .padding(horizontal = 28.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.reader_demo_title),
                    style = FlowFolioType.sectionTitle.merge(FlowFolio.title),
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.reader_demo_body),
                    style = FlowFolioType.sub.merge(FlowFolio.hint),
                    textAlign = TextAlign.Center
                )
            }
            Zone(
                stringResource(R.string.reader_demo_back),
                Modifier.align(Alignment.CenterStart).fillMaxHeight().width(ZONE)
            )
            Zone(
                stringResource(R.string.reader_demo_forward),
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(ZONE)
            )
            Zone(
                stringResource(R.string.reader_demo_menus),
                Modifier.align(Alignment.Center).fillMaxHeight(CENTRE_SHARE).fillMaxWidth(CENTRE_SHARE)
            )
            Zone(
                stringResource(R.string.reader_demo_riffle),
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(RIFLE_ZONE)
            )
            Column(
                Modifier.align(
                    Alignment.BottomCenter
                ).padding(horizontal = 28.dp, vertical = 24.dp).widthIn(max = DEMO_TEXT_WIDTH),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowFolioPrimaryButton(text = stringResource(R.string.reader_demo_got_it), onClick = onDone)
                FlowFolioGhostButton(text = stringResource(R.string.reader_demo_never), onClick = onNeverShow)
            }
        }
    }
}

/** One marked tap zone: a faint fill and edge with its label, so the reader sees where to press. */
@Composable
private fun BoxScope.Zone(label: String, modifier: Modifier) {
    val p = FlowFolio.palette
    Box(
        modifier
            .clip(ZONE_SHAPE)
            .background(p.ink.copy(alpha = ZONE_FILL))
            .border(1.dp, p.ink.copy(alpha = ZONE_EDGE), ZONE_SHAPE),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = FlowFolioType.rowLabel.merge(FlowFolio.title),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(8.dp)
        )
    }
}

private val ZONE_SHAPE = RoundedCornerShape(14.dp)
private val ZONE = 88.dp
private const val CENTRE_SHARE = 0.44f
private val RIFLE_ZONE = 72.dp
private val DEMO_TEXT_WIDTH = 440.dp
private const val SCRIM = 0.82f
private const val ZONE_FILL = 0.16f
private const val ZONE_EDGE = 0.55f
