# ADR-0021: Draw each chapter's islands once, in the background, into an on-disk image cache

- **Status:** Accepted (product owner, 2026-10-20: "draw each chapter's islands once in the background into an on-disk
  image cache, so page turns don't use the WebView")
- **Date:** 2026-10-20
- **Deciders:** product owner, lead engineer
- **Phase:** 2 (milestone 2)

Amends the island part of ADR-0019, which stays in force for everything else.

## Context

ADR-0019 lays out and draws "islands" (MathML, SVG, complex tables) with a hidden WebView. In milestone 2 the WebView
measured a chapter's islands when it was first shown and drew the islands of the pages about to be shown, on every
page turn. Measured on the Fold (debug build, spreads, `docs/plans/phase2-m2.md`):

- Moby-Dick (no islands): about 335 MB, 5% janky frames.
- Linear Algebra with the island WebView switched off: about 330 MB, 8% janky frames.
- Linear Algebra with islands: 545 to 660 MB, up to 280 MB of native heap, 18 to 21% janky frames.

The cost is the WebView working on the main thread during page turns (loading, measuring and drawing pages of
islands), and the memory Chromium holds while it does. Smaller pages and unloading when idle did not change it. Island
images are also lost when the app closes, so every visit pays again, and island sizes are measured again on every
open, which moves page numbers in the first seconds.

## Options considered

1. **Keep drawing on demand, tune the WebView** (smaller pages, fewer loads). Tried; native heap and jank barely moved.
2. **Pre-render ahead in memory only.** Avoids work at the moment of the turn, but holds every image in memory or
   repeats the work on every visit.
3. **Draw each chapter's islands once, in the background, into an on-disk image cache, and keep their measured sizes
   on disk too.** Page turns only decode small images. The WebView works while the reader is still, and only until the
   book is cached for the current text size and column width.
4. **Render islands without a WebView** (a native MathML layout engine). Not available under our licence policy with
   the quality needed; out of scope.

## Decision

Option 3.

- **What is stored:** per book, per layout (text size and column width in pixels, plus a cache version), each island's
  measured size, and its image as a PNG.
  - **Equations** are drawn in black on a transparent background and tinted to the paper's ink when loaded, so one
    image serves every paper.
  - **SVG and tables** keep their own colours, so their images are drawn in the paper's ink and stored per ink.
- **Where:** the app's cache directory (`cacheDir/islands/<book id>/<layout>/`). Android may clear it, which only
  costs re-drawing.
  - The whole cache is capped (200 MB); the oldest layouts are deleted first.
- **When:** the reader's chapter is measured first; then the islands on the pages planned for display, if not yet on
  disk; then the other chapters' sizes; then every island of the book, nearest chapters first, in small batches, only
  while the reader has not turned a page for a moment.
  - Images are encoded and decoded off the main thread.
- **Reading:** pages load island images from memory, else from disk; the WebView is used only for islands not yet
  cached. Sizes found on disk lay the book out correctly at once, with no re-pagination.

## Consequences

- Positive:
  - Page turns no longer wait for the WebView once a book is cached; memory and frames should come close to books
    without islands.
  - Reopening a cached book lays it out at once, with the islands' real sizes.
- Negative / accepted risks:
  - The first reading session of a math-heavy book still runs the WebView in the background for a while (Linear
    Algebra has about 8,000 islands). It pauses while pages are being turned.
  - Disk use: tens of megabytes per math-heavy book and layout, within the cap.
  - A new text size or column width (folding, unfolding, spreads on or off, M3 text settings) is a new layout to
    cache. Several layouts per book can be kept; the cap removes the oldest.
  - Island ids come from parse order. The cache version changes whenever the parser could change them.
- Follow-ups:
  - **M2 (lead engineer):** measure memory and frames again with the cache warm and cold; record them in
    `docs/plans/phase2-m2.md`.
  - **M3 (lead engineer):** text settings will multiply layouts; revisit the cap and whether to keep only the
    current layout per book.
