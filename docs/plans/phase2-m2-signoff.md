# Phase 2, milestone 2 sign-off: the paginated EPUB reader

- **Status:** Signed off by the product owner, 2026-10-20. Decisions: EPUB links and footnote popovers come after the
  riffle scrubber (M2b, ADR-0022); EPUB memory is investigated in M3 as proposed.
- **Plans:** [`phase2-plan.md`](phase2-plan.md) (M2) and [`phase2-m2.md`](phase2-m2.md); ADR-0019 (engine), ADR-0020
  (parser), ADR-0021 (island image cache, accepted 2026-10-20)
- **Checked on:** the reference Galaxy Z Fold, unfolded (2256 x 2504) and folded (cover screen 1080 x 2520), with the
  IDPF samples (Moby-Dick, *A First Course in Linear Algebra*, Accessible EPUB 3) and our generated RTL sample
- **Commits:** 750290d, ae37704, 598a4da, 1fcf629, 459fe05, a0b5db1, dc99b16, 46b2c91; CI green on every push

## Summary

EPUBs now open in the reader with the same page turns, spreads, papers and navigation as PDFs. Pages come from our own
native layout engine. Equations, SVG and complex tables are drawn by a hidden WebView into an on-disk cache. With
that cache warm, a math textbook turns pages as smoothly and with as much memory as a plain novel.

One planned M2 item is **not built**: tapping links and footnotes in EPUBs. It is listed below, under decisions for
the owner.

## What was built

- **`:core:reflow`, the engine (ADR-0019).**
  - Parses XHTML into text, image and island blocks. Footnotes are kept out of the flow, and anchors are recorded.
  - Cuts pages with Android's `StaticLayout`. Text is justified and hyphenated. Widows, orphans and headings keep
    their lines together. Headings are set in Fraunces.
  - Saves positions as a locator (chapter and character offset, Room v10 `books.last_locator`). Any change of size,
    fold state or layout keeps the reader on the same text.
  - Lays out one chapter again on its own (`repaginate`) when its islands get their sizes.
- **The reader.**
  - EPUBs use Curl, Slide and Scroll, single pages and spreads (cover alone, print pairing), the five papers, right
    to left, Contents, Go to page by the book's printed page numbers (page list), and the scrubber.
  - Highlights, bookmarks, selection and zoom are hidden for EPUBs until M4.
- **Islands (MathML, SVG, complex tables).**
  - A hidden WebView lays them out and draws them. It serves only the book's own files, has no network and runs
    no book scripts.
  - MathML's `mfenced` is rewritten for MathML Core. Brackets around matrices are drawn as borders, because phone
    fonts cannot stretch them.
  - Wide equations are scaled down to fit the column.
  - **Disk cache (ADR-0021):** sizes and PNGs are kept per book and layout (200 MB cap). Equations are stored in black
    and tinted to the paper. Every island is drawn ahead while the reader is still. A cached book opens laid out at
    once.

## What was checked

| Check | Result |
|---|---|
| Pagination is exact at five text sizes | **Pass.** `PaginationOnDeviceTest` on the Fold: joined page text equals the chapter text for every corpus book at 80–220%, and no page clips a line. Each book takes 0.03–0.9 s. |
| Unit tests | **Pass.** All 142 JVM and Robolectric tests, including new ones for locators, anchors, islands, chapter relayout, the island store and the page-to-locator save. |
| Turn styles | **Pass.** Curl, Slide and Scroll on Moby-Dick. Switching style keeps the text. |
| Folded, unfolded, fold/unfold | **Pass.** The cover screen shows single pages. Folding and unfolding keep the same text, in Moby-Dick and Linear Algebra. |
| Islands | **Pass.** Inline and block MathML, matrices with brackets, and tables, on Paper, Sepia and Night; also on the cover screen. |
| Right to left, images, mixed content | **Pass.** RTL sample: Arabic shaping, mirrored spreads, swipes go forward. Accessible EPUB 3: a full-page image, definition lists, code. |
| Position | **Pass.** The locator survives every relayout. A cached book opens directly on the saved spread. |

Bugs found and fixed by these checks:
- stale page images after a chapter relayout;
- wide equations cut off on the cover screen;
- a rare WebView hang on the first measure after a cold start;
- "1–1" shown for two screen pages within one printed page;
- the `startPage` debug extra being ignored for EPUBs.

## What was measured

Debug build, unfolded, spreads, `tools/measure-reader.ps1`, 300 turns unless noted. The memory target is 292 MB
absolute (ADR-0013 as revised in CLAUDE.md issue 14), with 10% growth or less.

| Book | Total memory (turn 50 / last) | Janky frames | Verdict |
|---|---|---|---|
| Moby-Dick (100 turns) | 333 / 338 MB | 5.4% | ~40 MB over |
| Linear Algebra, island cache warm | 395 / 401 MB | 4.6% | ~110 MB over |
| Linear Algebra, first visit (cache empty) | 502 / 607 MB | 6.5% | over while caching |
| Linear Algebra before ADR-0021 | 545–660 MB | 18–21% | replaced |

- **Frames:** with the cache warm, a turn draws 63 frames in either book. Jank is now the same as for a plain novel.
  These are debug-build numbers. The exit criterion asks for a release build, which is measured at M5 close-out.
- **Memory is not yet within budget, even for plain text.**
  - Moby-Dick is about 40 MB over. Graphics (page images and the design's GPU caches) are 158 MB of that, against
    139 MB for PDFs in Phase 1.
  - Linear Algebra with a warm cache adds about 60 MB more: Java heap 67 MB against 19 MB for Moby-Dick, probably
    the parsed book and its ~8,000 island spans (not yet profiled).
  - On a first visit, memory peaks while islands are drawn and falls once they are collected.
- **First visit:** the whole of Linear Algebra caches in about 3 minutes of not turning pages: 10,000 files, 78 MB.

## Open items

**For the owner to decide:**
1. **EPUB links and footnote popovers** were planned for M2 and are not built. The engine already has the links and
   footnote text. Wiring up taps, internal jumps, web-link confirmation and the popover is about one slice of work.
   Options: do it now before M3 starts (recommended, it belongs with navigation), or move it to M4.
2. **Memory over budget for EPUBs** (40 to 110 MB). The proposal is to investigate in M3, together with text settings,
   which change what is cached anyway. Candidate fixes:
   - page images in an EPUB-sized budget;
   - lighter span objects for islands;
   - dropping the parsed text of chapters far from the reader.
   If the budget cannot be met, a revised ADR-0013 target for reflowed books would come back to you.

**Carried into M3 (typography):**
- The "Aa" text settings and pinch to resize. Each text size and column width is a new island cache layout; the
  200 MB cap and "keep only the current layout" need revisiting.
- Gappy justified short lines in lists.
- The reader's plain white error and notice pages.
- Scroll shows each page's margins as a gap mid-text; it needs continuous scrolling for reflowed books.
- Very wide tables of equations become very small on the cover screen; an island zoom is needed.
- While the island cache fills, `uiautomator dump` cannot get an idle window. Tests must tap by coordinates or wait.

**Carried into M4 and M5:** highlights, bookmarks, selection and zoom on EPUBs (M4); release-build frame
measurements and the robustness corpus run (M5).
