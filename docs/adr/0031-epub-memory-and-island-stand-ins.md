# ADR-0031: EPUB memory measures and island stand-ins across text sizes

- **Status:** Accepted (lead engineer, 2026-10-22; owner: "test overall the memory usage of the app and apply any
  improvements ... without sacrificing any design or functionality")
- **Date:** 2026-10-22
- **Deciders:** lead engineer
- **Phase:** 2 (M3, items 4 and 5 of `docs/plans/phase2-m3.md`)
- **Extends:** ADR-0013 (reader memory budget), ADR-0019 (EPUB islands), ADR-0021 (island image cache)

## Context

At M2 sign-off EPUBs were 40 to 110 MB over the reader budget (292 MB absolute, CLAUDE.md known issue 14). Measured
again on the Fold (debug build, unfolded, spreads, 100 turns, 2026-10-22): Moby-Dick 338 MB, *A First Course in Linear
Algebra* 572 MB at turn 50 and 468 MB at turn 100, the University Physics PDF 287 MB, the library 291-294 MB. A
heap dump and `meminfo`/`gfxinfo` showed where it went:

- every EPUB created the island WebView, books without islands too (about 30 MB);
- the source of every island stayed in memory as strings: 10,281 equations, 22 million characters, in the textbook;
- island images in memory had a 32 MB budget, though they are only needed while their pages are drawn;
- each EPUB page was drawn into a new 32-bit bitmap, copied to 16 bits, then to the GPU;
- every screen decoded its own copy of each cover and kept a CPU copy beside the GPU one;
- a text size change made every equation wait for the WebView again (M3 item 4).

The graphics line (about 156 MB on any screen) is mostly the window's four full-screen buffers, which the system
allocates; it is not the app's to cut.

## Decision

1. **The island WebView is created only for books that have islands.**
2. **Island markup is packed** (`PackedText`, deflate at its fastest level) once the book is open, in the background,
   so opening is not slowed; it is unpacked one chapter at a time when the WebView needs it. Short markup stays as it
   is.
3. **Island images in memory: 16 MB** (more than a full page of islands, so one page's are never evicted while it is
   drawn); the rest load again from the disk cache.
4. **EPUB pages are drawn straight into one reused 16-bit page bitmap**, then copied to the GPU.
5. **Covers are cached for all screens**, decoded once, kept on the GPU only (32 MB budget), and decoded again when the
   file changes.
6. **Islands per layout (stand-ins):** when a text size has no stored islands, the nearest stored size's equations
   stand in: their sizes, scaled, lay the book out, and their images, scaled, are shown, until this size's are measured
   and drawn (which then replace them, chapter by chapter, as before). Only equations stand in: they do not wrap, so
   they scale; tables and SVG depend on the column and are drawn again. The 200 MB cache cap and least-recently-used
   trimming stay; a stand-in layout counts as used.

## Consequences

- Positive: see the results in `docs/plans/phase2-m3-memory.md`; no design or feature changes; page turns are smoother
  (fewer janky frames) because less is allocated per page.
- Negative: stand-in equations are a little soft for the seconds before they are drawn again; packed markup costs a
  few milliseconds to unpack per chapter when the WebView works on it.
