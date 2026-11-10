package com.flowfolio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.flowfolio.core.designsystem.ButtonStyle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioButton
import com.flowfolio.core.designsystem.FlowFolioPalette
import com.flowfolio.core.designsystem.FlowFolioType

/** The guide id the reader remembers having shown (owner, 2026-11-07). */
internal const val READER_DEMO = "reader_demo"

/**
 * The demo shown the first time a reader opens a book (owner, 2026-11-07): the tap zones for turning pages and the
 * menus, and the riffle bar. "Got it" dismisses; "Never show tips again" turns every first-use tip off.
 */
@Composable
@Suppress("LongMethod") // the scrim, the words, four zones and the two buttons
internal fun BoxScope.ReaderDemo(onDone: () -> Unit, onNeverShow: () -> Unit) {
    FlowFolioPalette(dark = true) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM))) {
            // Narrower side zones on a folded screen, so the two buttons fit side by side between them.
            val folded = maxWidth < WIDE
            val side = if (folded) ZONE_FOLDED else ZONE
            val centre = maxHeight * CENTRE_SHARE
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
                Modifier.align(Alignment.CenterStart).fillMaxHeight().width(side)
            )
            Zone(
                stringResource(R.string.reader_demo_forward),
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(side)
            )
            Zone(
                stringResource(R.string.reader_demo_menus),
                Modifier.align(Alignment.Center).height(centre).fillMaxWidth(CENTRE_SHARE)
            )
            Zone(
                stringResource(R.string.reader_demo_riffle),
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(RIFLE_ZONE)
            )
            // Owner, 2026-11-10: the two buttons on one line, in the clear band between the menus zone and the
            // riffle zone and inside the side zones, so neither sits on a zone or its label.
            val band = (maxHeight - centre) / 2 - RIFLE_ZONE
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = side + 2.dp, end = side + 2.dp, bottom = RIFLE_ZONE + (band - BUTTONS) / 2),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowFolioButton(stringResource(R.string.reader_demo_got_it), onDone, small = folded)
                FlowFolioButton(
                    // Folded, the owner's own words for it: "Never show again" fits beside "Got it".
                    stringResource(if (folded) R.string.reader_demo_never_short else R.string.reader_demo_never),
                    onNeverShow,
                    style = ButtonStyle.GHOST,
                    small = folded
                )
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
        // One line at any width: a narrow folded side zone still reads "Forward", not "Forwa / rd".
        Text(
            label,
            style = FlowFolioType.rowLabel.merge(FlowFolio.title),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(2.dp)
        )
    }
}

private val ZONE_SHAPE = RoundedCornerShape(14.dp)
private val ZONE = 88.dp
private val ZONE_FOLDED = 64.dp
private val WIDE = 600.dp
private val BUTTONS = 42.dp
private const val CENTRE_SHARE = 0.44f
private val RIFLE_ZONE = 72.dp
private val DEMO_TEXT_WIDTH = 440.dp
private const val SCRIM = 0.82f
private const val ZONE_FILL = 0.16f
private const val ZONE_EDGE = 0.55f
