# Phase 2, milestone 3 sign-off: typography

- **Status:** Built and checked by the lead engineer, 2026-10-26; waiting for the owner's sign-off.
- **Plans:** [`phase2-m3.md`](phase2-m3.md) (M3) and [`phase2-m3-memory.md`](phase2-m3-memory.md); ADR-0027 (reading
  fonts), ADR-0030 (the book's own fonts), ADR-0031 (EPUB memory and island stand-ins)
- **Checked on:** the `fold8_api37` emulator (inner 2256 x 2504 and cover 1080 x 2520, density 480, API 37, 4 cores),
  folded and unfolded. Earlier M3 measurements were made on the reference Galaxy Z Fold.
- **Corpus:** the IDPF samples (Moby-Dick, *A First Course in Linear Algebra*, Accessible EPUB 3, Children's
  Literature, Georgia CFI, Page Blanche, The Waste Land) and our generated RTL sample and synthetic textbook.

## What M3 built

1. **Text settings in "Aa" and in Settings:**
   - a typeface: the book's own (ADR-0030) or one of seven bundled ones (ADR-0027), Literata by default;
   - size, also by pinching the page;
   - line spacing (four steps), margins (three);
   - justification and hyphenation on or off.
2. **Re-pagination that keeps the reader's sentence.** The chapters up to the reader's are laid out first, the rest in
   the background; an hourglass shows while the new pages come. Since 2026-10-26 those chapters are laid out on all
   cores at once.
3. **The book's own fonts:** embedded `@font-face` fonts load from the EPUB, sanitised, when "Book's font" is chosen.
4. **Islands per layout:** each text size and column width keeps its own equation cache, and another size's equations
   stand in, scaled, until this size's are drawn (ADR-0031).
5. **EPUB memory** brought to within about 20 MB of the 292 MB budget (see below).
6. **The M2 carry-overs:**
   - list and `<br>`-broken lines set ragged;
   - themed error cards;
   - Scroll as one continuous column;
   - a zoom for equations and tables drawn too small.

## Checks (plan item 7)

| Check | Result |
|---|---|
| Pagination exact in every font at five sizes | **Pass.** `PaginationOnDeviceTest.everyFontAndTextSettingPaginatesExactly`, plus the earlier size test: 415 layouts. Every reading font, with its bold and italic, at 80-220%, on all nine corpus books and the built sample. Also tight and loose line spacing, and justification and hyphenation off. Joined page text equals each chapter's text exactly, no line is clipped, and every page's locator leads back to it. |
| Re-pagination time (target: the reader's page within 300 ms) | **Met on the Fold earlier; not yet re-measured there.** Linear Algebra at 53% on the Fold, non-debuggable build: 206-250 ms (2026-10-22). Moby-Dick at 41% on the 4-core emulator, non-debuggable, chapters in parallel: 325-503 ms (debuggable: 419-679 ms; before the parallel layout 489-984 ms). The Fold has 8 faster cores, so its number should be lower; it needs confirming on the device. |
| Memory before and after | **Done in M3** (`phase2-m3-memory.md`, on the Fold): Moby-Dick 338 → 308 MB, Linear Algebra 468-572 → 366 MB. Growth from turn 50 to 100: 2-3%. Both are within about 20 MB of the 292 MB budget, against 46 and 176-280 MB over before. The rest is the window's buffers and code, which aren't the reader's to cut. |
| Screenshots of each font on each paper | **Done:** [`assets/phase2-m3-fonts-papers.jpg`](assets/phase2-m3-fonts-papers.jpg). The book's font and all seven fonts, each on all six papers (Auto, Paper, Sepia, Night, Black, Rustic), Moby-Dick, unfolded. Every face renders as itself and is legible on every paper. |
| Unit and static checks | **Pass:** all JVM and Robolectric tests, Spotless, detekt, lint, and the riffle screenshot tests. |
| CI | **Not run for these commits:** they are not pushed. The same checks pass locally. |

## Fixed while checking

- **Crash when changing the paper colour while reading.** The riffle scrubber's loupe drew a page thumbnail the cache
  had already recycled, because a paper change frees the old-colour thumbnails. Thumbnails are now checked as they
  are drawn, and fetched again in the new colour.
- **The folded reader's top buttons.** A PDF's six buttons needed 360 dp on the 360 dp cover screen, so the row
  squeezed the last one, the bookmark. They now share the row's width equally.
- **Panels reset on a paper switch** between light and dark (earlier on 2026-10-26).

## Open items for the owner

1. **Confirm re-pagination time on the Fold** with this build: change the text size late in a long book; the reader's
   page should come within 300 ms.
2. **Sign off M3**, or list what to change. M4 (bookmarks, highlights, notes, Share, Copy and Translate on EPUBs, the
   image viewer, right to left) follows.
