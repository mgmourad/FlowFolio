# ADR-0027: Bundle seven reading fonts for EPUBs

- **Status:** Accepted (product owner, 2026-10-21: "Approve the seven fonts as listed, with Matthew Butterick's Charter
  files, and the M3 plan")
- **Date:** 2026-10-21
- **Deciders:** product owner, lead engineer
- **Phase:** 2 (milestone M3)

## Context

M3 lets the reader choose the typeface for EPUB text. The owner chose seven (2026-10-20): Charter, Literata, Atkinson
Hyperlegible Next, EB Garamond, Source Sans 3, Lexend and Andika. FlowFolio works offline and everything ships in the
app (ADR-0009), so the fonts are bundled; ADR-0003 forbids GPL-family licences in the app.

## Options considered

1. **Bundle the files** (chosen): works offline, the same everywhere; about 7.7 MB in the APK.
2. **Google downloadable fonts** through Play services: smaller APK, but needs Play services and a network the first
   time, and Charter is not on Google Fonts.
3. **Fewer fonts**: smaller, but not what the owner chose.

## Decision

Bundle all seven in `:core:designsystem` (`res/font/reading_*`), each as a font family (`font_*.xml`) with regular
and bold, upright and italic, so the text engine's bold and italic runs use real faces:

| Font | Files | Size | Licence | Source |
|---|---|---|---|---|
| Literata | variable roman + italic | 1.8 MB | SIL OFL 1.1 | google/fonts `ofl/literata` |
| Charter | regular, italic, bold, bold italic (TTF) | 145 KB | Bitstream Charter licence (permissive; keep the notice; acknowledge the trademark on unmodified copies) | Matthew Butterick, "Charter 210112.zip" |
| Atkinson Hyperlegible Next | variable roman + italic | 0.2 MB | SIL OFL 1.1 | google/fonts `ofl/atkinsonhyperlegiblenext` |
| EB Garamond | variable roman + italic | 1.6 MB | SIL OFL 1.1 | google/fonts `ofl/ebgaramond` |
| Source Sans 3 | variable roman + italic | 1.0 MB | SIL OFL 1.1 | google/fonts `ofl/sourcesans3` |
| Lexend | variable roman (no italic exists) | 0.2 MB | SIL OFL 1.1 | google/fonts `ofl/lexend` |
| Andika | regular, italic, bold, bold italic | 2.7 MB | SIL OFL 1.1 | google/fonts `ofl/andika` |

Licence texts ship in `assets/third_party_licenses/fonts/`. Literata is the default. Variable fonts get their bold
through `fontVariationSettings` (`'wght' 700`) in the family files.

Line spacing is set as an exact line height (`LineHeightSpan.Standard`, the chosen spacing times the text size), not
as a multiple of each font's built-in line height: those differ widely (Literata's is tall, Charter's short), and a
plain multiplier made Literata set about half again as many pages as Charter.

## Consequences

- Positive: the owner's fonts, offline, with real bold and italics.
- Negative: about 7.7 MB more in the APK. Andika's four static files (2.7 MB) could become two if size matters.
- Lexend has no italic; Android slants it.
- Charter covers Latin only; books in other scripts fall back to the system font for those characters.
