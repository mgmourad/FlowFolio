# Phase 2, milestone 2: the paginated EPUB reader

- **Status:** Started 2026-10-20. The owner said "proceed with M2 automatically".
- **Parent plan:** [`phase2-plan.md`](phase2-plan.md). **Engine:** ADR-0019 (hybrid native pagination).

## Goal

Open EPUB books in the reader with the same page turns as PDFs: Curl, Slide and Scroll, single pages and spreads, the
glass bars, papers, the scrubber, Contents and Go to page. Pages come from our native reflow engine. Equations, SVG
and complex tables are rendered by a hidden WebView. The reading position survives re-pagination.

## Design

- **`:core:reflow`**, a new module, holds the engine from the spike, made product-quality:
  - **XHTML to blocks:** text with spans (emphasis, superscript and subscript, code, links), headings, lists, asides,
    images, and islands. Footnotes are kept apart for the popover. Anchors (`id`) and page-break markers record their
    position in the text.
  - **Pagination** with `StaticLayout`: high-quality line breaking and hyphenation, justification, widows and orphans,
    and headings kept with what follows.
    - Each page records where it starts: the spine item and a character offset.
    - Chapters start on a new page.
  - **Drawing a page** into a bitmap, in the paper's ink and paper colours. Images are decoded at display size and
    cached.
  - **Locators:** a spine index and a character offset, encoded as a string. A locator maps to a page after any
    re-pagination.
- **Reader integration** (`:feature:reader`):
  - `ReaderRepository.openEpub` opens the archive and package.
  - The reader keeps a format-neutral `BookPages`:
    - for PDFs, the PDFium document, as today;
    - for EPUBs, the paginated book.
  - The curl, spreads, page cache (ADR-0013 budget), turn styles, bars, papers and scrubber are unchanged.
  - **Pagination runs off the main thread** whenever the page pane changes size (fold, unfold, rotation, spreads on or
    off). The reader stays on the same locator.
  - **Contents** come from the nav or NCX, mapped to pages through anchors. **Printed page numbers and Go to page**
    use the EPUB page list.
  - **Position:** Room v10 adds `books.last_locator` for EPUBs. PDFs keep `last_page`.
  - **Not yet for EPUBs (M4):** text selection, highlights, ribbons and zoom. Their controls are hidden for EPUBs, not
    broken.
- **Islands** (MathML, SVG, complex tables):
  - A hidden WebView in the reader's window measures each chapter's islands when the chapter is paginated, and renders
    them ahead of the page turn.
  - It has no network and runs no book scripts (only our own measuring and `mfenced` scripts), and serves only the
    book's own files.
  - Each island is captured on its own page, which avoids the spike's capture bugs.
- **Body text:** 18 sp in a serif typeface with the paper's colours. The book's own CSS and the text settings come in
  M3.

## Checks

- **Correctness:** an instrumented test paginates the corpus at five text sizes and checks that the pages' text joins
  back to the chapter text exactly.
- **Unit tests:** locators and the mapping of anchors and the page list to pages.
- **On the Fold:**
  - Moby-Dick, Linear Algebra (MathML), Accessible EPUB 3 and the synthetic textbook;
  - folded and unfolded, in single pages and spreads;
  - Curl, Slide and Scroll;
  - screenshots, then `measure-reader.ps1` for frames and memory.
- **A position check:** the reader stays on the same text after folding and unfolding.

## Progress (2026-10-20)

- **Built:** `:core:reflow` and the reader integration (750290d); islands, per-chapter relayout, ink-coloured islands,
  `mfenced` and matrix brackets, Fraunces headings (ae37704); JVM tests for locators, anchors, islands and chapter
  relayout; the `startPage` debug extra for EPUBs.
- **Checked on the Fold (unfolded, spreads):** Moby-Dick (justified, hyphenated text, contents jumps); Linear Algebra
  (inline and block MathML, matrices with brackets, Paper and Night papers); the position survives a layout change
  and every chapter relayout. `PaginationOnDeviceTest` passes for the corpus at five sizes.
- **Device checks (2026-10-20, all on the Fold):**
  - Curl, Slide and Scroll on Moby-Dick; switching turn style keeps the text. Scroll lays the book out as single pages.
  - RTL sample (Arabic shaping, first page alone on the left, left-to-right swipe goes forward); Accessible EPUB 3
    (images, definition lists, code faces).
  - Folded (cover screen) and fold/unfold keep the reader on the same text, with Moby-Dick and Linear Algebra.
- **Bugs found and fixed by these checks:** stale page images after a chapter was laid out again (page numbers move,
  so the page key now carries a layout counter); equations wider than the narrow cover column were cut off (they are
  measured at full width and scaled down); the first island measure after a cold start sometimes hung (the WebView
  now takes its width before loading, with one retry after 10 s); a spread inside one printed page read "1–1".
- **Memory and frames (debug build, unfolded, spreads; `measure-reader.ps1`; ADR-0013 absolute target 292 MB):**

  | Book, turns | Total PSS (turn 50 / last) | Native heap | Janky frames |
  |---|---|---|---|
  | Moby-Dick (no islands), 100 | 333 / 338 MB | 59 MB | 5.4% |
  | Linear Algebra, island WebView switched off, 100 | 332 / 325 MB | 59 MB | 8.2% |
  | Linear Algebra, islands on, 300 | 485–620 / 545–660 MB | 160–280 MB | 18–21% |

  | Linear Algebra, island disk cache warm (ADR-0021), 300 | 395 / 401 MB | 71 MB | 4.6% |
  | Linear Algebra, island disk cache empty (first visit), 300 | 502 / 607 MB | 82–250 MB | 6.5% |

  Text pages alone are near budget (about 40 MB over 292 MB, mostly page images at 158 MB of graphics; to look at in
  M3). Before the cache, islands cost up to about 300 MB more and tripled janky frames: the WebView laid out and drew
  islands during page turns. **ADR-0021 (accepted 2026-10-20)** draws each chapter's islands once into an on-disk
  cache (sizes and PNGs; equations in black, tinted when shown). With the cache warm, Linear Algebra matches books
  without islands (401 MB, 4.6% janky; a turn draws 63 frames in either book). On a first visit frames stay smooth;
  memory peaks higher while islands are drawn (native heap falls back to about 145 MB once collected), and the book
  is fully cached after about 3 minutes still on a page (Linear Algebra: 10,000 files, 78 MB). Frame times are from
  the debug build (CLAUDE.md issue 30); the comparison between rows holds.
- **Noted for M3:** justified short lines in lists look gappy; the reader's error and notice pages are plain white;
  Scroll shows each page's margins as a gap mid-text (continuous scroll for reflowed books); a very wide table of
  equations becomes very small on the cover screen (island zoom).

## Out of scope for M2

The book's CSS and fonts, the text settings and pinch to resize (M3); annotations, selection and zoom on EPUBs (M4);
fixed-layout EPUBs (decided in M4).
