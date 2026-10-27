# ADR-0041: EPUB chapters start on their own spread; only the reader's chapter is laid out first

- **Status:** Accepted (product owner, 2026-10-27: "Build the 'chapters start on their own spread' fix")
- **Date:** 2026-10-27
- **Deciders:** product owner, lead engineer
- **Phase:** 2 (M5)
- **Amends:** ADR-0019 (EPUB pagination), in how a re-layout is ordered

## Context

Phase 2's exit criterion asks that after a text-size change the reader's page shows within 300 ms. M3 lays out every
chapter up to the reader's before showing anything, so every page number up to the reader's is exact. This mattered
for spreads: pages pair by their global index (`Spreads`), so the spread holding the reader's text depends on how
many pages come before it. If the earlier chapters' page count were only estimated, the right page might become the
left one when the count came in.

On the reference Fold (2026-10-27, release build), that took 400-600 ms deep in a long book: Moby-Dick at 41%, and
*A First Course in Linear Algebra* at 53%. Nearly all of it went to laying out the chapters before the reader's;
drawing the two pages took 30-70 ms.

## Decision

1. **In a spread, every chapter after the first starts on a left page**, as chapters in many printed books start on a
   fresh spread. When a chapter would start on a right page, a blank page comes before it. The first chapter is left
   alone, so the cover can stand alone on the right ("cover on its own").
2. **A re-layout lays out only the reader's chapter before showing pages.** Every other chapter gets pages estimated
   from the reader's chapter (text per page) until the background layout replaces them, before and after the reader
   alike. In a spread the reader's chapter starts on a left page whatever comes before it, so the pages shown never
   re-pair. Only the page numbers settle a moment later. In single-page layout nothing pairs, so the same order
   applies without blanks.
3. **`ReflowBook` keeps each chapter's pages apart** and joins them, with the blanks, whenever any chapter's pages
   change: the background layout, a chapter laid out again for its islands. Blanks are made once per layout, so a
   page image can follow its page to a new number.

4. **The pages in view don't wait on their equations one by one.** After a layout, the equation images of the pages
   in view are read on all cores before those pages are drawn. While a page is drawn again with newly finished
   equations, its earlier drawing from the same layout stays on screen (`PageKey.layout`).

## Results

On the reference Fold, optimised build, a size step up and back down (2026-10-27):
- Moby-Dick at 41%: pages shown 18-45 ms after the change (was 414-617 ms).
- *Linear Algebra* at 53%, a spread full of equations: 223-387 ms, median about 270 ms (was 406-829 ms). Most of the
  rest is the WebView measuring the chapter's equations at the new size.

## Consequences

- Re-layout time no longer grows with the reader's place in the book: it is one chapter's layout plus drawing two pages.
- Spreads show a blank page before about half the chapters. A book with many very short spine items has many blanks.
- The page count and "Page N of M" in a spread include the blanks; single-page layout has none, so the two counts
  differ more than before.
- A ribbon put on a blank page is stored with the next page's text and shows there.
- PDF spreads are unchanged.
