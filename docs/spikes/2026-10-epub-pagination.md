# Spike: EPUB pagination engine and EPUB parsing

- **Date:** 2026-10-20
- **Phase:** 2. This is the plan's step 1, in [`phase2-plan.md`](../plans/phase2-plan.md).
- **Code:** `android/spikes/epub-pagination/`. It is built only with `-Pspikes`, and `run-spike.ps1` runs it. Raw
  results stay out of git.
- **Device:** the reference Fold (SM-F976U), Android 17, unfolded. One spread page measures 1128 x 2504 px. The body
  text is 18 sp, which is 54 px.
- **Build:** release, debug-signed, not minified.
- **Corpus:**
  - IDPF samples, CC BY-SA 3.0: Linear Algebra (MathML), Moby-Dick, Accessible EPUB 3, The Waste Land, Georgia (CFI),
    Children's Literature and Page Blanche (fixed layout).
  - Our generated books (`tools/testepubs/`): a 60-chapter textbook shaped like the converter's output, with 991
    printed pages, 1,440 MathML, SVG and table pieces, and a page list; a right-to-left Arabic book; and 8 hostile
    files.

## Candidates

- **A. Web.** Android's WebView splits each chapter into CSS columns. The curl gets each page as a snapshot of the
  WebView.
- **B. Native.** Our own XHTML-to-blocks parser and Android `StaticLayout`, with high-quality line breaking,
  hyphenation and justification. Pages are cut by our rules: whole lines only, two lines or more at the bottom and top
  of a page (orphans and widows), and headings kept with what follows. Pages are drawn on a Canvas.
- **C. Hybrid.** B, plus "islands": a hidden WebView measures and renders MathML, SVG and tables, which are then
  placed in the native flow as images. Inline equations sit on the text baseline.
- **R. Readium:** dropped before measuring (see "EPUB parsing" below).

## Results on the Fold

### Correctness: lost or repeated text across page breaks

**B and C: 0 errors** in every book at 80, 100, 130, 170 and 220% font sizes. The check compares the text of all
pages, joined, with the source text, block by block. It also confirms no line is clipped at the page bottom. The
largest run was the textbook at 220%: 15,615 pages.

**A: Chromium never loses text**, but lines and pieces of content cross page edges. We counted line boxes that cross a
column edge or are clipped at the bottom:

| Book | Font size | Line boxes | Crossing or clipped |
|---|---|---|---|
| Linear Algebra | 100% | 141,124 | 4,475 |
| Linear Algebra | 170% | 155,516 | 10,099 |
| Synthetic textbook | 100% | 114,695 | 50 |
| Synthetic textbook | 170% | 192,967 | 2,243 |
| Moby-Dick | any | | 0-18 |

Most are wide tables and equations cut off at the column edge, where the reader would lose the right side.

### Speed

All times are in ms. "Book" is the time to paginate the whole book; "first chapter" is time to the first page of a
chapter, which is what a reader waits for.

| Book (100%) | A, book | A, first chapter | B, book | B, first chapter | C, book (layout + measuring pieces) |
|---|---|---|---|---|---|
| Moby-Dick (1,378 B pages) | 5,461 | 36 | 347 | 2 | 520 |
| Synthetic textbook (3,307 C pages) | 3,182 | 77 | 736 | 13 | 552 + 2,038 |
| Linear Algebra (10,281 MathML pieces) | 12,293 | 31 | 182 | 0 | 329 + 5,342 |
| Accessible EPUB 3 | 1,401 | 106 | 63 | 2 | 86 + 52 |

- **Parsing** (our parser, whole book): 4-454 ms. Linear Algebra is the slowest, with 94 chapters and 10,000
  pieces.
- **Drawing a page:**
  - B: 1 ms at P50 and 5 ms at most, off the main thread;
  - C: 2-7 ms at P50, with its pieces;
  - A: a snapshot takes **17-52 ms on the main thread**.
- **Rendering pieces in C** costs about 200 ms per page in Linear Algebra, which is dense with inline math. It costs
  about 20 ms per page in the textbook. This is rendered once and cached, but it has to happen ahead of the turn.

### Turning, on our `:core:pagecurl`, 60 turns of the textbook

| Engine | Frames | Janky | P50 | P95 | P99 | Total memory (PSS) | Graphics |
|---|---|---|---|---|---|---|---|
| B (native, 16-bit page images) | 3,484 | 1.55% | 6 ms | 9 ms | 14 ms | 303 MB | 141 MB |
| A (web snapshots) | 3,548 | 1.63% | 5 ms | 8 ms | 13 ms | 342 MB | 165 MB |

The curl is equally smooth with both, because a page is an image either way. In this run A's snapshots fell in the
300 ms pauses between turns. When the reader turns faster, they will land on the main thread during the next turn.
A holds 40 MB more. Neither spike app was tuned for memory; the product would use Phase 1's page cache.

### Fidelity

These come from preview pages in the raw results, which are not committed.

- **Text, both A and B/C:** justified with hyphenation. B looks as good as A for body text.
- **A** applies the book's whole CSS: italic centred captions, borders, the book's fonts, and full SVG and MathML.
- **B** applies a subset (headings, emphasis, lists, asides and images). It does not yet use the book's CSS. That
  work is needed in M2/M3, where the book's fonts, alignment, sizes and colours will be read from its stylesheet.
- **C pieces are rendered faithfully by Chromium:** matrices, fractions, square roots and SVG. The snapshots in the
  spike had two capture bugs: neighbouring pieces bled into each other, and the last table was clipped. Rendering
  each piece in its own page fixes both.
- **Right to left (Arabic):** B and C lay it out correctly; A does too.
- **Fixed layout (Page Blanche):** only A can show it. B and C skip it.

### Robustness: our parser and the hostile corpus

| File | Result |
|---|---|
| DRM (`encryption.xml` with AES) | Refused. Font obfuscation is still allowed. |
| No `container.xml` | Refused |
| Entity expansion ("billion laughs") | Refused before anything expanded: DOCTYPE processing is off |
| Zip bomb (512 MB in 0.5 MB) | The entry is refused by size before inflating, and inflated bytes are also counted |
| Missing chapter | Reported as missing; the book still opens |
| Broken markup | The text read before the break is kept |
| Path traversal (`../../evil.txt`) | Android's `ZipFile` throws `ZipException` itself. The spike reported this as a crash; the product must catch it and refuse the book. |
| Script, event handlers, remote image and frame | Scripts and handlers stripped. 2 remote requests blocked (and the spike has no INTERNET permission). |

Every IDPF and generated book opened, with its contents, page list, reading direction and fixed-layout flag read
correctly. Children's Literature has 92 page-list entries and the textbook has 991.

### EPUB parsing: Readium

Readium Kotlin toolkit 3.4.0 (BSD-3-Clause) needs Google's `desugar_jdk_libs`. That library is **GPL-2.0 with the
Classpath Exception**, and ADR-0003 allows no GPL code in the app. On 2026-10-20 the owner chose to drop Readium
rather than make an exception. Our own parser covered the whole corpus in about 250 lines (see ADR-0020).

## Conclusions

1. **Engine: C, the hybrid (ADR-0019).** Native layout is:
   - always correct across page breaks;
   - 4 to 60 times faster than WebView columns at paginating a whole book (4x for the textbook, 16x for Moby-Dick);
   - drawn off the main thread;
   - lighter on memory;
   - built on exact text positions, which selection, highlights, the magnifier and Translate already rely on.

   A WebView renders only what native layout shouldn't attempt (MathML, SVG and complex tables), as cached images.
2. **Pieces are the main cost** in math-heavy books. Linear Algebra needs 5 s to measure all 10,000 pieces. The
   product will:
   - measure them per chapter, as each chapter is paginated (about 60 ms per chapter on average);
   - cache their sizes per book and text size;
   - render them ahead of the turn, off the turn's frames.
   - **For the converter's own output (Phase 3)**, the converter can include each equation's rendered size, or an
     SVG, in the sidecar. Then FlowFolio-mode books would not need the WebView to measure at all. This change to
     `conversion-spec.md` is proposed in ADR-0019.
3. **Fixed-layout EPUBs** are pages already, so a WebView showing one page per spine item is the natural route. They
   are rare in textbooks; whether they are built in Phase 2 (M4) or Phase 8 is decided when we get there.
4. **EPUB parsing: our own code (ADR-0020),** hardened as above, with the path-traversal exception handled.

## Not yet measured

- **The book's own CSS in the native engine**, against the corpus. That is M2/M3 work, judged by side-by-side previews
  with candidate A.
- **Memory in the product page cache** (ADR-0013 budget) and **re-pagination after a text-size change** in the real
  reader: these are M2/M3 exit checks.
- **Selection across pieces** (an equation inside a highlight): M4.
