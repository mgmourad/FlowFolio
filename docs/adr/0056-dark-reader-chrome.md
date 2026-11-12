# ADR-0056: The reader's chrome is dark glass on every paper

- **Status:** Accepted (owner, 2026-11-12). Departs from the Quiet Library mock (ADR-0052) on this one point.
- **Date:** 2026-11-12
- **Deciders:** product owner, lead engineer
- **Phase:** tightening milestone TM1

## Context

The reader's bars, dock and panels took the paper's tone (`FlowFolioPalette(dark = tone.dark)`): light glass on light
and sepia papers, as the mock draws them. The owner found the tools hard to read there: light glass over a light page
leaves too little contrast.

## Decision

The reader's chrome is **dark glass on every paper**.
- Covered: the top bar, the dock and its riffle, every panel, and the selection and highlight menus with their Words,
  note and VoiceMark windows.
- How: the dark palette, its surface at 84% as the scrim, and a pale hairline edge so the glass still reads as glass
  over dark paper.
- The selection's handles are drawn on the page, so they keep following the paper.
- Inside the chrome the dark palette's third ink is lifted 70% of the way to its second (`ReaderChromePalette`).
  Measured on `fold8_api37`, it was 2.8:1, under WCAG's 4.5:1 for text.

| Paper | Labels | Secondary text and inactive tabs |
|---|---|---|
| Light | 10.3:1 | 4.7:1 |
| Sepia | 10.7:1 | 4.9:1 |
| Black | 14.1:1 | 6.4:1 |

## Consequences

- The reader's chrome looks the same whatever the paper, and every text in it meets WCAG AA.
- The mock's light panels on light paper no longer describe the reader; the tour's reader pictures must be retaken
  (TM2).
- The lift applies only inside the reader's chrome: the app's dark theme elsewhere keeps its third ink, as the mock
  sets it.
