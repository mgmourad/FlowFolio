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
- **Still to check on the Fold:** folded (cover screen) and fold/unfold; Slide and Scroll on EPUBs; the RTL sample;
  Accessible EPUB 3 and the synthetic textbook; frames and memory with `measure-reader.ps1` on a non-debuggable build.
- **Noted for M3:** justified short lines in lists look gappy; the reader's error and notice pages are plain white.

## Out of scope for M2

The book's CSS and fonts, the text settings and pinch to resize (M3); annotations, selection and zoom on EPUBs (M4);
fixed-layout EPUBs (decided in M4).
