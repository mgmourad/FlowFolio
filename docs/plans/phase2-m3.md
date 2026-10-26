# Phase 2, milestone 3: typography

- **Status:** Approved by the owner 2026-10-21 (fonts as listed, Butterick's Charter files, and the plan); ADR-0027.
- **Plan:** [`phase2-plan.md`](phase2-plan.md) (M3, goal 3); carried items from [`phase2-m2-signoff.md`](phase2-m2-signoff.md).

## 1. The reading fonts (owner's choice, 2026-10-20), for approval

They ship inside the app, so ADR-0003 applies: no GPL, licence texts shipped, sizes stated. All but Charter are the
variable or static files kept by Google Fonts (`github.com/google/fonts`, `ofl/<family>/`), as Fraunces and Instrument
Sans already are (ADR-0015).

| Font | Kind | Files we'd ship | Size | Licence | Source, version | Notes |
|---|---|---|---|---|---|---|
| Literata | Serif made for e-reading (Google Play Books) | roman + italic, variable (weight, optical size) | 1.8 MB | SIL OFL 1.1 | google/fonts `ofl/literata`; upstream googlefonts/literata 3.103 (2023-05-19) | Latin, Greek, Cyrillic. The natural default for books |
| Charter | Classic book serif (Bitstream, 1987) | regular, italic, bold, bold italic (OTF/TTF) | about 0.3 MB | **Bitstream Charter licence** (not OFL): use, modify, redistribute "for any purpose and without restriction"; keep the notice; acknowledge Bitstream's trademark on unmodified copies. SPDX `Bitstream-Charter` | Matthew Butterick's conversion of the Bitstream originals ("Charter 210112.zip", practicaltypography.com) | Permissive, compatible with our MIT licence and ADR-0003. Latin only. Alternatives: XCharter (CTAN 1.26, 2024-06-18, adds small caps and old-style figures, same licence) or Charis SIL (OFL, a Charter-like design with wide language support) |
| Atkinson Hyperlegible Next | Sans for low vision (Braille Institute) | roman + italic, variable (weight) | 0.2 MB | SIL OFL 1.1 (reserved names "Atkinson", "Hyperlegible") | google/fonts `ofl/atkinsonhyperlegiblenext`; upstream googlefonts/atkinson-hyperlegible-next (2025) | |
| EB Garamond | Old-style serif | roman + italic, variable (weight) | 1.6 MB | SIL OFL 1.1 | google/fonts `ofl/ebgaramond`; upstream octaviopardo/EBGaramond12 | Latin, Greek, Cyrillic |
| Source Sans 3 | Humanist sans (Adobe) | roman + italic, variable (weight) | 1.0 MB | SIL OFL 1.1 | google/fonts `ofl/sourcesans3`; upstream adobe-fonts/source-sans 3.052R (2023-04-04) | |
| Lexend | Sans designed to reduce visual stress | roman only, variable (weight) | 0.2 MB | SIL OFL 1.1 | google/fonts `ofl/lexend` | **No italic exists**: italics would be slanted by the system |
| Andika | Sans for beginning readers (SIL) | regular, italic, bold, bold italic | 2.7 MB | SIL OFL 1.1 | google/fonts `ofl/andika`; upstream silnrsi/font-andika (7.000, 2025-06-02) | Very wide language coverage, hence the size |

**In all, about 7.8 MB of font files** (the APK compresses them somewhat). The app's own fonts are 1 MB today. If size
matters more, Andika's four static files could become two (regular and bold, with system italics), saving about 1.4 MB.

Each family's licence text goes in `assets/third_party_licenses/fonts/` and a row in `docs/licenses.md`, as for
Fraunces. A short ADR records the choice (ADR-0027).

**For you to decide:** approve the seven fonts as listed, and for Charter, the Butterick files (recommended: the
original design, four styles) or XCharter or Charis SIL.

## 2. The milestone

1. **Text settings in "Aa"** (and Settings): typeface (the book's own, or one of the seven; Literata by default), size
   (also by pinching the page), line spacing, margins, justification on or off, hyphenation on or off.
2. **Re-pagination that keeps the reader's sentence**, showing the reader's page within 300 ms after a change (the
   phase exit criterion); the rest of the book counts its pages in the background.
3. **The book's own fonts**: embedded `@font-face` fonts load from the EPUB (sanitised) when "Book's font" is chosen.
4. **Islands per layout:** each text size and column width is a new island layout; the 200 MB cap and "keep only the
   current layout" are revisited so a size change doesn't redraw every equation.
5. **EPUB memory** (over budget at M2 sign-off, 40 to 110 MB): page images in an EPUB-sized budget, lighter spans for
   islands, dropping parsed chapters far from the reader.
6. **Carried from M2:** gappy justified short lines in lists; the reader's plain white error and notice pages; Scroll
   for reflowed books as one continuous column (no page margins mid-text); a zoom for islands too small on the cover
   screen. **Built 2026-10-26**, checked on the emulator (Pixel 7, cover-screen width) with Linear Algebra: lists and
   `<br>`-broken paragraphs set ragged; themed notice cards (missing file, damaged EPUB); Scroll continuous across
   page boundaries; tapping a shrunk derivation opens it zoomed. On the Fold, unfolded (reinstalled 2026-10-26, which
   wiped its library; Linear Algebra and Moby-Dick imported again): ragged theorem lists in the spread, continuous
   Scroll across page boundaries, the themed "File not found" card. Not yet checked: the island zoom on the cover
   screen (needs the phone folded) and the pinch inside the zoom (adb can't pinch).
7. **Checks:** the pagination test (pages joined reproduce the chapter exactly) at five sizes and in every font;
   re-pagination time on the Fold; memory before and after; screenshots of each font in each paper; CI green.

Order: 1 (fonts and settings), 2, 3, then 4 to 6; 7 throughout. The owner checks on the Fold after 1 and 2.
