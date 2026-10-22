// The miniature's proportions (offsets in pads and lines) are drawn by eye, as fractions of its size.
@file:Suppress("MagicNumber")

package com.flowfolio.feature.library

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Palette
import com.flowfolio.core.designsystem.paletteFamily
import com.flowfolio.core.designsystem.swatch
import com.flowfolio.core.model.AppStyle
import com.flowfolio.core.model.MaterialSeed

/**
 * The app's themes as three small previews (ADR-0028), each drawn in its own palette at the current light or dark
 * mode: background, a card with a title and lines of text, a button. The chosen one is ringed.
 */
@Composable
internal fun ThemeCards(selected: AppStyle, seed: MaterialSeed, onSelect: (AppStyle) -> Unit) {
    val dark = FlowFolio.palette.dark
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppStyle.entries.forEach { style ->
            val palette = paletteFamily(style, seed).of(dark)
            val on = style == selected
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier
                    .weight(1f)
                    .selectable(on, role = Role.RadioButton) { onSelect(style) }
                    .testTag(LibraryTestTags.THEME_PREFIX + style.name),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(PREVIEW_ASPECT)
                        .clip(shape)
                        .border(
                            if (on) 2.5.dp else 1.dp,
                            if (on) FlowFolio.palette.title else FlowFolio.palette.edge,
                            shape
                        )
                ) {
                    Canvas(Modifier.matchParentSize()) { preview(palette) }
                }
                Text(
                    stringResource(style.label),
                    style = FlowFolioType.pill.merge(if (on) FlowFolio.title else FlowFolio.hint),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/** Material You's colours: the wallpaper's (Android 12 and later) and six seeds, as round swatches. */
@Composable
internal fun SeedSwatches(selected: MaterialSeed, onSelect: (MaterialSeed) -> Unit) {
    val dark = FlowFolio.palette.dark
    val seeds = MaterialSeed.entries.filter {
        it != MaterialSeed.WALLPAPER ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        seeds.forEach { seed ->
            val on = seed == selected
            val color = if (seed == MaterialSeed.WALLPAPER) {
                paletteFamily(AppStyle.MATERIAL, seed).of(dark).title
            } else {
                seed.swatch(dark)
            }
            val label = stringResource(seed.label)
            // The seeds share the row; each is at most SWATCH wide, so narrow cards still show all seven.
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .widthIn(max = SWATCH)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .border(if (on) 3.dp else 0.dp, FlowFolio.palette.text, CircleShape)
                        .padding(if (on) 5.dp else 0.dp)
                        .clip(CircleShape)
                        .background(color)
                        .selectable(on, role = Role.RadioButton) { onSelect(seed) }
                        .semantics { contentDescription = label }
                        .testTag(LibraryTestTags.SEED_PREFIX + seed.name),
                    contentAlignment = Alignment.Center
                ) {
                    // The wallpaper's swatch is marked with a small ring, as the system's own picker does.
                    if (seed == MaterialSeed.WALLPAPER) {
                        Box(Modifier.size(10.dp).border(2.dp, FlowFolio.palette.background, CircleShape))
                    }
                }
            }
        }
    }
}

/** A miniature screen in [p]: background, a card with a title and text, a button and an accent dot. */
private fun DrawScope.preview(p: Palette) {
    drawRect(Brush.verticalGradient(listOf(p.surface, p.backgroundDeep)))
    val pad = size.width * PAD
    val cardTop = size.height * CARD_TOP
    val cardHeight = size.height * CARD_HEIGHT
    val radius = CornerRadius(size.width * CARD_RADIUS)
    drawRoundRect(
        Brush.verticalGradient(listOf(p.cardStart, p.cardEnd), startY = cardTop, endY = cardTop + cardHeight),
        Offset(pad, cardTop),
        Size(size.width - 2 * pad, cardHeight),
        radius
    )
    val line = size.height * LINE
    // The title, then two lines of text.
    drawRoundRect(
        p.title,
        Offset(pad * 2, cardTop + pad),
        Size(size.width * TITLE_WIDTH, line * 1.4f),
        CornerRadius(line)
    )
    drawRoundRect(
        p.text.copy(alpha = TEXT_ALPHA),
        Offset(pad * 2, cardTop + pad * 2.6f),
        Size(size.width * TEXT_WIDTH, line),
        CornerRadius(line)
    )
    drawRoundRect(
        p.hint.copy(alpha = TEXT_ALPHA),
        Offset(pad * 2, cardTop + pad * 3.6f),
        Size(size.width * HINT_WIDTH, line),
        CornerRadius(line)
    )
    // A button, and an icon dot in the accent.
    val buttonTop = size.height * BUTTON_TOP
    drawRoundRect(
        Brush.verticalGradient(
            listOf(p.buttonTop, p.buttonBottom),
            startY = buttonTop,
            endY =
                buttonTop + size.height * BUTTON_HEIGHT
        ),
        Offset(pad, buttonTop),
        Size(size.width * BUTTON_WIDTH, size.height * BUTTON_HEIGHT),
        CornerRadius(size.height)
    )
    drawCircle(p.glyph, size.width * DOT, Offset(size.width - pad * 1.8f, buttonTop + size.height * BUTTON_HEIGHT / 2))
    drawRoundRect(p.shelfTop, Offset(0f, size.height * SHELF_TOP), Size(size.width, size.height * (1 - SHELF_TOP)))
}

private val AppStyle.label: Int
    get() = when (this) {
        AppStyle.FLOWFOLIO -> R.string.style_flowfolio
        AppStyle.RUSTIC -> R.string.style_rustic
        AppStyle.MATERIAL -> R.string.style_material
    }

private val MaterialSeed.label: Int
    get() = when (this) {
        MaterialSeed.WALLPAPER -> R.string.seed_wallpaper
        MaterialSeed.BLUE -> R.string.seed_blue
        MaterialSeed.TEAL -> R.string.seed_teal
        MaterialSeed.GREEN -> R.string.seed_green
        MaterialSeed.AMBER -> R.string.seed_amber
        MaterialSeed.ROSE -> R.string.seed_rose
        MaterialSeed.VIOLET -> R.string.seed_violet
    }

private val SWATCH = 40.dp
private const val PREVIEW_ASPECT = 0.78f
private const val PAD = 0.08f
private const val CARD_TOP = 0.12f
private const val CARD_HEIGHT = 0.42f
private const val CARD_RADIUS = 0.08f
private const val LINE = 0.045f
private const val TITLE_WIDTH = 0.42f
private const val TEXT_WIDTH = 0.6f
private const val HINT_WIDTH = 0.45f
private const val TEXT_ALPHA = 0.85f
private const val BUTTON_TOP = 0.62f
private const val BUTTON_HEIGHT = 0.11f
private const val BUTTON_WIDTH = 0.5f
private const val DOT = 0.05f
private const val SHELF_TOP = 0.86f
