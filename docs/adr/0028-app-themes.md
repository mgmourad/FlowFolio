# ADR-0028: App themes (FlowFolio, Rustic, Material You) and a Rustic paper

- **Status:** Accepted (product owner, 2026-10-22: "I want to add two themes overall to this app: this classic wood
  'Rustic' theme, material you theme (with several color choices) ... themes should be consistent across menus,
  buttons, selections")
- **Date:** 2026-10-22
- **Deciders:** product owner, lead engineer
- **Phase:** 2 (between M2 and M3)
- **Extends:** ADR-0014 and ADR-0015 (the design language), which become the "FlowFolio" theme

## Context

Every screen takes its colours, lights and shadows from one `Palette` of tokens (ADR-0015), in a light and a dark
variant, chosen by the System / Dark / Light preference. The owner wants two more looks for the whole app: a classic
wood "Rustic" theme built on #48120c, and Material You with several colour choices. The reader also gets a Rustic
paper: #48120c pages with #f6e4ca text.

## Options considered

1. **One palette family per theme** (chosen): a theme is a light and a dark `Palette`, plus its surface texture. All
   components already read the palette, so every menu, button and selection follows.
2. **Material 3's `ColorScheme` everywhere**: would drop the design language's own tokens (glows, card gradients,
   shelf wood) that the FlowFolio theme needs.
3. **The material-color-utilities library** for Material You's seed colours: exact HCT tones, but a new dependency
   for a few dozen colours.

## Decision

- `UserSettings.appStyle`: FLOWFOLIO (the mock, unchanged), RUSTIC, MATERIAL. The existing System / Dark / Light choice
  applies to all three; the "Neon glow" switch applies to the lights each theme has.
- **Rustic:** dark is the wood itself (#48120c and deeper walnut), cream text (#f6e4ca), honey-brass accents, warm
  candle-light glows, wood-grain texture on the background and a darker plank shelf. Light is parchment with walnut ink
  and the same brass accents, with soft warm shadows.
- **Material You:** a Material 3 colour scheme mapped onto the palette (primary to titles and buttons, surfaces and
  containers to backgrounds and cards, outline to hairlines). Flat tonal cards with low shadows, no glows, no grain.
  Colours from the wallpaper (Android 12 and later), or one of six seeds (blue, teal, green, amber, rose, violet). The
  seed schemes are generated in the app from the seed's hue at Material 3's tones (CIE L* as the tone, LCh chroma
  reduced to fit sRGB), an approximation of HCT without a new dependency (option 3 declined).
- The reader's bars and panels, which follow the paper's lightness, take the light or dark palette **of the current
  theme**.
- **Rustic paper:** `PaperStyle.RUSTIC`, paper #48120c, ink #f6e4ca, a dark paper. With the Rustic theme, "Auto" paper
  is Rustic in dark mode and Sepia in light mode.

## Consequences

- Positive: the whole app changes look together; no screen code changes colour on its own.
- Negative: Material You seed colours are close to, not identical with, Google's tonal palettes.
- Each new token or texture must be given a value in all three themes.
