package com.flowfolio.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * FlowFolio's colour roles (owner's design language, 2026-10-17; ADR-0014). Values were read pixel-exact from the
 * owner's reference image. Dark mode glows (each element lit in its own colour); light mode casts a soft shadow.
 */
@Immutable
data class Palette(
    val dark: Boolean,
    /** Screen, sheet and dialog background. */
    val background: Color,
    /** Thin outline around rounded surfaces (visible in light mode only). */
    val edge: Color,
    /** Titles and menu entries. */
    val title: Color,
    /** Primary items: book titles, chapter names, setting names. */
    val item: Color,
    /** Secondary text: descriptions, page numbers, placeholders. */
    val hint: Color,
    /** Sub-items and anything else: nested entries, badges, warnings. */
    val subItem: Color,
    /** Glyphs (icons). */
    val glyph: Color,
    val toggleOffThumb: Color,
    val toggleOffTrack: Color,
    val toggleOnThumb: Color,
    val toggleOnTrack: Color
)

val DarkPalette = Palette(
    dark = true,
    background = Color(0xFF1C0A4D),
    edge = Color(0xFF1C0A4D),
    title = Color(0xFFFF8400),
    item = Color(0xFF00FFFF),
    hint = Color(0xFF9E9E9E),
    subItem = Color(0xFFC70C36),
    glyph = Color(0xFFAC2793),
    toggleOffThumb = Color(0xFF363086),
    toggleOffTrack = Color(0xFF9E9E9E),
    toggleOnThumb = Color(0xFFFF8400),
    toggleOnTrack = Color(0xFF32C88C)
)

val LightPalette = Palette(
    dark = false,
    background = Color(0xFFFFEBC4),
    edge = Color(0xFFFFBA59),
    title = Color(0xFF8E5314),
    item = Color(0xFF135A5A),
    hint = Color(0xFF544E47),
    subItem = Color(0xFFC70C36),
    glyph = Color(0xFF4227AC),
    toggleOffThumb = Color(0xFF7B3729),
    toggleOffTrack = Color(0xFF9E9E9E),
    toggleOnThumb = Color(0xFFFF7600),
    toggleOnTrack = Color(0xFF32A5C8)
)

val LocalPalette = staticCompositionLocalOf { LightPalette }
