package com.flowfolio.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * The window a feature introduces itself with the first time it is used (owner, M5): its glyph, what it is for in a
 * sentence or two, and one button to get on with it.
 *
 * It is shown once per feature and then never again, which is what makes it worth reading; the caller decides that
 * by remembering what has been seen, so this only draws.
 */
@Composable
fun FeatureSplash(
    glyph: Glyph,
    title: String,
    body: String,
    dismiss: String,
    onDismiss: () -> Unit,
    neverShow: String? = null,
    onNeverShow: (() -> Unit)? = null
) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = SPLASH_WIDTH)
                .fillMaxWidth()
                .clip(shape)
                .background(p.background, shape)
                .border(1.dp, p.edge, shape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(SPLASH_GLYPH_PLATE)
                    .clip(CircleShape)
                    .background(p.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                FlowFolioGlyph(glyph, contentDescription = null, color = p.title, size = SPLASH_GLYPH)
            }
            Text(
                title,
                style = FlowFolioType.sectionTitle.merge(FlowFolio.title),
                textAlign = TextAlign.Center
            )
            Text(
                body,
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                textAlign = TextAlign.Center
            )
            FlowFolioPrimaryButton(text = dismiss, onClick = onDismiss)
            if (neverShow != null && onNeverShow != null) {
                FlowFolioGhostButton(text = neverShow, onClick = onNeverShow)
            }
        }
    }
}

/**
 * A quiet line of advice beside the thing it is about (owner, M5), for features a reader could otherwise walk past.
 * It carries its own dismiss, since a tip that cannot be got rid of stops being a tip.
 */
@Composable
fun TipCard(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.surface.copy(alpha = TIP_FILL), shape)
            .border(1.dp, p.title.copy(alpha = TIP_EDGE), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FlowFolioGlyph(Glyph.Hint, contentDescription = null, color = p.title, size = 18.dp, glow = false)
        Text(
            text,
            style = FlowFolioType.sub.merge(FlowFolio.item),
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
                .padding(4.dp)
        ) {
            FlowFolioGlyph(Glyph.Back, contentDescription = null, color = p.hint, size = 14.dp, glow = false)
        }
    }
}

private val SPLASH_WIDTH = 360.dp
private val SPLASH_GLYPH_PLATE = 64.dp
private val SPLASH_GLYPH = 32.dp
private const val TIP_FILL = 0.7f
private const val TIP_EDGE = 0.35f
