# ADR-0019: Paginate reflowable books natively, with a WebView only for equations, SVG and tables

- **Status:** Accepted (product owner, 2026-10-20)
- **Date:** 2026-10-20
- **Deciders:** product owner, lead engineer
- **Phase:** 2

## Context

Phase 2 must paginate reflowable books (EPUB) and turn their pages with the same curl as PDFs. Pagination has to stay
correct at every text size. The same engine will show FlowFolio (reflow) mode, the product's default, for the EPUB 3
that the Phase 3 converter writes (`conversion-spec.md`). That output is full of MathML equations, SVG figures and
tables with merged cells. Phase 1 already relies on exact text positions for selection, highlights, the magnifier and
Translate. Phase 1 also set the bar for smoothness and memory (ADR-0013).

The spike ([`docs/spikes/2026-10-epub-pagination.md`](../spikes/2026-10-epub-pagination.md)) measured three engines on
the Fold.

## Options considered

1. **A. WebView with CSS columns, pages as snapshots.**
   - Pros: the book's full CSS, MathML and SVG for free; fixed-layout books too.
   - Cons:
     - paginating a whole long book takes 1.4-12 s (Accessible EPUB 3 to Linear Algebra);
     - a snapshot costs 17-52 ms and must be taken on the main thread;
     - thousands of lines and wide pieces are clipped at page edges in math-heavy books;
     - 40 MB more memory;
     - text positions only through JavaScript.
2. **B. Native layout** (our XHTML parser and Android `StaticLayout`, drawn on a Canvas).
   - Pros:
     - 0 lost or repeated characters at five text sizes across the corpus;
     - 0.06-0.8 s per book and 0-17 ms to the first page;
     - pages drawn in about 1 ms off the main thread;
     - exact text positions.
   - Cons: we implement the CSS subset ourselves, and it cannot draw MathML or complex SVG.
3. **C. Hybrid:** B, plus a hidden WebView that measures and renders MathML, SVG and complex tables as images
   ("islands") placed in the native flow.
   - Pros: B's speed and correctness, with Chromium's fidelity where native code would be weakest.
   - Cons:
     - two engines;
     - islands cost time in math-heavy books: 5 s to measure 10,000 in one book, and about 200 ms per page to render;
     - selection inside an island needs care.

## Decision

**Option C.** Native layout handles all text: paragraphs, headings, lists, asides, images and simple tables. A
WebView, with no network access and scripts stripped, measures and renders islands. Islands are:
- measured per chapter as it is paginated;
- cached by size per book, text size and width;
- rendered ahead of the page turn, never during it.

Pages reach the curl as 16-bit GPU images through Phase 1's byte-budgeted page cache. The curl and the reader's
gestures, bars, panels and annotations need no change.

**Proposed change to `conversion-spec.md` (Phase 3):** for each equation, the converter records its rendered size and
baseline in the sidecar, and may include an SVG rendering. That lets FlowFolio-mode books paginate without the WebView
measuring step. This change is decided with the Phase 3 plan.

**Fixed-layout EPUBs** use the WebView, one page per spine item. The milestone for this (Phase 2 M4 or Phase 8) is
decided in M4.

## Consequences

- **Positive:**
  - Pagination is provably correct: a test reproduces the text exactly at five sizes, for every corpus book, in CI.
  - Re-pagination after a text-size change is fast enough to keep the reader on the same sentence.
  - Annotations keep exact character positions, as on PDFs.
  - Memory stays within the ADR-0013 budget.
- **Negative / accepted risks:**
  - We own a CSS subset. Books that rely on unusual CSS look plainer than in a browser-based reader. Side-by-side
    previews against the WebView, over the corpus, keep this honest.
  - Islands make the first visit to a math-heavy chapter slower. Caching and look-ahead hide it, and the converter's
    sizes (above) remove it for our own books.
  - WebView updates (Chromium) can change how islands render. They are images, so pagination does not change.
- **Follow-ups:**
  - **M2:** the engine in `:core:epub` or a new `:core:reflow` module, decided in M2's plan.
  - **M3:** the book's CSS for fonts, alignment and spacing.
  - **M4:** selection across islands.
  - **Phase 3:** the sidecar change above.
