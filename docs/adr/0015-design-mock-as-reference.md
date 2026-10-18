# ADR-0015: Use the owner's interactive mock as the design reference

- **Status:** Accepted (product owner, 2026-10-17)
- **Date:** 2026-10-17
- **Deciders:** product owner, lead engineer
- **Phase:** 1 (milestone 5) and later
- **Supersedes in part:** ADR-0014: the colour table and the system-font choice. ADR-0014's design-system structure
  (palette, lit text styles, own toggle and glyphs) and Night pages remain.

## Context

The owner supplied an interactive HTML mock (`docs/design/FlowFolio_Mock.html`) covering:

- every main screen: splash, library, shelves, book details, reader, bookmarks, settings;
- both themes;
- the fonts, glow and shadow values, motion curves and components.

It refines the palette from the earlier reference image. The owner asked for it to guide every milestone and phase.

## Decision

- The mock is the design reference. When the app and the mock disagree, the mock wins, except for these owner
  decisions:
  - **Page turns:** our 3D page curl stays (the mock's flip is not used). Slide and Scroll are offered as options.
  - **Fold layout:** the mock is a slab phone. On the Fold it applies one to one when folded. When unfolded:
    - a left rail replaces the bottom bar;
    - wide screens get more columns or two panes;
    - the reader keeps its full-bleed spread.
  - **Mock vs earlier decisions:**
    - Reader chrome: the mock's glass bars replace the print-style chrome.
    - Conversion: the mock's converter endpoint is replaced by on-device conversion (ADR-0009).
    - Reflow-only controls stay hidden until Phase 3.
- **Fonts:** Fraunces (variable: opsz, wght, SOFT, WONK) for display and Instrument Sans (variable: wdth, wght)
  for UI. Both SIL OFL 1.1, bundled from `github.com/google/fonts` (1.17 MB for four files).
- **Palette:** the mock's tokens, verbatim:

  | Token | Dark | Light |
  |---|---|---|
  | Background | `#1d0a47` | `#fdeac6` |
  | Background, deep | `#0f0428` | `#f1d39c` |
  | Surface | `#26105a` | `#fff4de` |
  | Surface 2 | `#311770` | `#fbe6c0` |
  | Title | `#ff8a00` | `#8b4d12` |
  | Item | `#00f0ff` | `#0f5f59` |
  | Hint | `#9b9aa6` | `#4e4842` |
  | Sub-item | `#ff2d5a` | `#c8102e` |
  | Accent | `#c02f9a` | `#4a2bb0` |
  | Text | `#ece8ff` | `#2e2219` |

  Toggles, cards, buttons, shelves, paper and effects follow the mock's CSS variables.

## Consequences

- **Positive:** one unambiguous visual target.
- **Negative / accepted risks:**
  - About 1.2 MB of fonts.
  - Visual effects (blur, 3D books, glow) cost GPU time and memory. Measured per screen; the reader keeps its
    ADR-0013 budget.
- **Follow-ups:** the slices in `docs/plans/phase1-m5-design.md`, and an ADR for the online metadata lookup.
