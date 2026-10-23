# Phase 2, M3: memory and speed (items 4 and 5)

Measured 2026-10-22 on the reference Fold, unfolded, two-page spreads, debug build, Material You dark, 100 page turns
with `android/tools/measure-reader.ps1` (total PSS after a forced GC). Decisions in ADR-0031.

## Results

| Book | Before (turn 50 / 100) | After (turn 50 / 100) | Janky frames before / after |
| --- | --- | --- | --- |
| Moby-Dick (EPUB, no islands) | 332 / 338 MB | 303 / 308 MB | 3.1% / 3.2% |
| Linear Algebra (EPUB, 10,281 equations) | 572 / 468 MB | 366 / 366 MB | 4.4% / 2.3% |
| University Physics (PDF) | 278 / 287 MB | 270 / 278 MB | 3.4% / 4.0% |
| Library screen (floor) | 291-294 MB | 280-303 MB | |

- Linear Algebra: Java heap 83 → 37 MB, native heap 190 → 70 MB at turn 50, no growth from turn 50 to 100 (was
  -18% after a 572 MB peak while equations were drawn). The "before" run started with a partly drawn island cache at
  that size, the "after" run with a warm one; the turn-100 figures compare like with like.
- Moby-Dick: the WebView is gone (about 30 MB); Java and native heaps are back near the PDF reader's.
- PDF: unchanged by this work (differences are run-to-run noise; jank 3.4-4.0% at a 6 ms median frame).
- All three are now within 20 MB of the 292 MB absolute budget (CLAUDE.md known issue 14); Moby-Dick and Linear
  Algebra are 16 and 74 MB over, against 46 and 176-280 MB before.

## Where the rest goes

- **Graphics, about 140-160 MB on every screen:** four full-screen window buffers (22 MB each, allocated by the
  system for the 2256 x 2504 panel), the app's GPU caches (about 38 MB, two thirds purgeable), page images (eight
  16-bit pages, 45 MB) and covers (0.7 MB each). Not the app's to cut further without changing the design.
- **Code, about 60-80 MB:** the app, Compose and WebView code mapped in; lower on a release build (R8, baseline
  profile).
- **Linear Algebra's Java heap:** 44 MB of live objects in a heap dump (book text and layouts, packed island markup);
  the rest of the heap's PSS is space the collector has not handed back yet.

## Opening a book

Parsing Linear Algebra takes 1.1-2.3 s on the Fold (debug). Packing the island markup costs 100-150 ms, so it runs in
the background after the first layout instead of during the open.

## Not done here

- Release-build measurements (exit criterion, at M5 close-out).
- Tall multi-line equation tables are shrunk to the column and can be small: the M2 carry-over "a zoom for islands
  too small".
