package com.flowfolio.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider

// Shared pieces of the three widgets (owner, 2026-11-12: clean, the app's own look, little empty space).

/** The widget's card: the theme's surface, rounded like the launcher's own, tapping does [onClick]. */
@Composable
internal fun WidgetCard(colors: WidgetColors, onClick: Action, content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().cornerRadius(CARD_RADIUS).background(colors.background).clickable(onClick)
            .padding(CARD_PADDING),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** A picture from [WidgetArt] at its own size, tinted [tint] (text and glyphs are drawn in white for this). */
@Composable
internal fun ArtImage(
    art: WidgetArt.Art,
    tint: ColorProvider?,
    description: String? = null,
    modifier: GlanceModifier = GlanceModifier
) {
    Image(
        ImageProvider(art.bitmap),
        contentDescription = description,
        colorFilter = tint?.let { ColorFilter.tint(it) },
        modifier = modifier.size(art.width, art.height)
    )
}

/** A thin, rounded progress bar [width] wide: the track, and [fraction] of it in the accent. */
@Composable
internal fun ProgressLine(fraction: Float, width: Dp, colors: WidgetColors) {
    Box(GlanceModifier.width(width).height(BAR).cornerRadius(BAR).background(colors.track)) {
        val done = width * fraction.coerceIn(0f, 1f)
        if (done.value > 0f) {
            Box(
                GlanceModifier.width(done.coerceAtLeast(BAR)).height(BAR).cornerRadius(BAR).background(colors.accent)
            ) {}
        }
    }
}

/** The book's cover width that fills [height] once [WidgetArt.book]'s shadow margin is added. */
internal fun coverWidthFor(height: Dp): Dp = height / BOOK_TOTAL_HEIGHT

/** The width [WidgetArt.book] takes for a cover [width] wide, its shadow margin included. */
internal fun bookFootprint(width: Dp): Dp = width * BOOK_TOTAL_WIDTH

internal val CARD_PADDING = 14.dp
private val CARD_RADIUS = 28.dp
private val BAR = 5.dp
private const val BOOK_TOTAL_HEIGHT = 1.5f + 0.14f * 2
private const val BOOK_TOTAL_WIDTH = 1f + 0.14f * 2
