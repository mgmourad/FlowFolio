// The AMOLED theme's tokens (owner, M4): the dark FlowFolio palette over true black.
@file:Suppress("MagicNumber")

package com.flowfolio.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * AMOLED (owner, 2026-11-02): pitch black, with the least contrasting lines the layouts still read by, and every other
 * colour taken from [DarkPalette], so the app looks like FlowFolio dark with the lights behind it switched off.
 *
 * On an OLED panel a black pixel is an unlit pixel, so the point is that the backgrounds are `#000000` exactly, not
 * nearly. For that reason:
 *  - [SurfaceTexture.NONE]: the mock's grain would light every background pixel it touched, which is the whole cost
 *    this theme exists to avoid (and on pure black it reads as noise rather than paper);
 *  - the surfaces lift by a few points only, enough to tell a card from the page behind it;
 *  - [edge] is dimmer than the dark theme's, since a hairline on black already has full contrast.
 *
 * It is a dark theme in both of [PaletteFamily]'s slots: picking Light while AMOLED is on keeps the black, which is
 * what someone choosing an AMOLED theme is asking for.
 */
val AmoledPalette = Palette(
    dark = true,
    background = Color(0xFF000000),
    backgroundDeep = Color(0xFF000000),
    surface = Color(0xFF08080B),
    surface2 = Color(0xFF121218),
    // Half the dark theme's line alpha: on true black a hairline needs far less to show.
    edge = Color(0x14AA96FF),
    title = DarkPalette.title,
    item = DarkPalette.item,
    hint = DarkPalette.hint,
    subItem = DarkPalette.subItem,
    glyph = DarkPalette.glyph,
    text = DarkPalette.text,
    toggleOffTrack = DarkPalette.toggleOffTrack,
    toggleOffThumb = Color(0xFF1B1B22),
    toggleOnTrack = DarkPalette.toggleOnTrack,
    toggleOnThumb = DarkPalette.toggleOnThumb,
    cardStart = Color(0xFF0B0B10),
    cardEnd = Color(0xFF000000),
    paper = Color(0xFF000000),
    paperInk = DarkPalette.paperInk,
    paperShade = Color(0x99000000),
    shelfTop = Color(0xFF17171E),
    shelfBottom = Color(0xFF000000),
    tabBar = Color(0xCC000000),
    buttonTop = DarkPalette.buttonTop,
    buttonBottom = DarkPalette.buttonBottom,
    buttonInk = DarkPalette.buttonInk,
    titleGlow = DarkPalette.titleGlow,
    itemGlow = DarkPalette.itemGlow,
    subGlow = DarkPalette.subGlow,
    hintGlow = Glow.None,
    glyphGlow = DarkPalette.glyphGlow,
    selected = Color(0xFF141419),
    // A shadow on black can only be read as a shape, so it is nearly opaque.
    shadow = Color(0xCC000000),
    pressShadow = Color(0x80000000),
    highlight = Color(0x0AFFFFFF),
    green = DarkPalette.green,
    texture = SurfaceTexture.NONE
)
