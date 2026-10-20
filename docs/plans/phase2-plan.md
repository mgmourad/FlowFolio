# Phase 2 plan: EPUB reading and pagination engine

- **Status:** Approved to start by the product owner, 2026-10-20 ("proceed with writing the Phase 2 plan and start it
  automatically"). Dependencies and downloads are still cleared one by one, as the working agreement requires.
- **Brief:** "Pagination engine spike and ADR. EPUB open, paginate, curl, typography settings, page-list support for
  print page numbers. Exit criteria: correct pagination at multiple font sizes, curl over reflowable content."
- **Design reference:** `docs/design/FlowFolio_Mock.html` (ADR-0015). The reader keeps Phase 1's bars, panels, papers,
  turn styles and annotations.

## Why this phase matters more than its size

This engine is the one that will display **FlowFolio (reflow) mode**, the product's default (owner decision,
2026-10-16). The Phase 3 converter will turn PDFs into EPUB 3 under `docs/conversion-spec.md`. That EPUB contains
MathML equations, SVG figures, tables with merged cells, footnote and sidebar asides, page-break markers and a page
list. So the engine is chosen for textbooks, not for novels. Ordinary EPUBs from anywhere must still work well.

## Goals

1. **Open EPUB 2 and EPUB 3 books** from the library. Import works as for PDFs: a copy, or read in place.
2. **Paginate reflowable content** into screen pages:
   - a single page on the cover screen, a two-page spread when unfolded;
   - the same page turns as PDFs (Curl, Slide, Scroll), including the curl's finger-following and the spread's left
     page.
3. **Typography settings in the "Aa" panel:**
   - font size, including pinch to change it (owner decision);
   - typeface, with the book's own fonts or ours (Fraunces, Instrument Sans) and a readable serif;
   - line spacing, margins, justification and hyphenation;
   - the paper colours from Phase 1.
   - Changing a setting re-paginates and keeps the reader on the same sentence.
4. **Navigation:** contents (nav or NCX), Go to page by printed page number (the EPUB `page-list`), a scrubber,
   internal links, and footnotes in a popover instead of a jump.
5. **Phase 1 features on EPUB:**
   - bookmarks, highlights, notes, Share and Copy, Translate;
   - right to left, from the spine's `page-progression-direction`;
   - tapping an image opens a zoomable viewer (owner decision).
   - Annotations are anchored with a locator that survives re-pagination.
6. **Robustness:** damaged or hostile EPUBs never crash the app or reach outside the book. That covers zip bombs, path
   traversal, XML entity attacks, scripts and remote resources.

## Exit criteria

- **Correct pagination at five or more font sizes:** an automated test checks that the pages, joined together,
  reproduce the chapter text exactly, with nothing lost or repeated. It runs on the corpus below.
- **Curl over reflowable content as smooth as over PDF pages** in a release build on the unfolded Fold: P50 frame
  CPU under 4 ms and 5% janky frames or fewer, measured by a new `EpubPageTurnBenchmark`.
- **Memory within ADR-0013's budget** (292 MB absolute on the reference Fold), with 10% growth or less over 300 turns.
- **Re-pagination after a font-size change** shows the reader's page in 300 ms or less. The whole book's page count
  may finish in the background.
- **Print page numbers:** where the book has a `page-list`, "Go to page" and the page indicator use the printed
  numbers.
- **Tests:** unit, Robolectric and instrumented tests for each of the above. CI stays green.

## Step 1: the pagination spike (decides the engine; ADR-0019)

The spike follows the Phase 1 renderer spike. Its code lives under `android/spikes/`, it is measured on the Fold, and
it is written up in `docs/spikes/2026-10-epub-pagination.md`.

### Candidates

| | Engine | How pages reach the curl | Strengths | Risks |
|---|---|---|---|---|
| **A. Web** | Android System WebView (Chromium) with CSS multi-column pagination, our own small implementation | Each page is drawn from the WebView into a bitmap (the curl needs page images or composables it can clip and mirror) | Full HTML and CSS, MathML Core, SVG, tables, the book's fonts; selection is built in | Snapshot cost and memory per page; WebView start-up time; pagination done in JavaScript; less control over line breaking |
| **B. Native** | Our own layout of an XHTML and CSS subset with Android's text layout (`StaticLayout` and `LineBreaker`), drawn on a Canvas | Pages are plain draw commands, like PDF page images today | Fast and predictable; exact text positions for selection and highlights; no WebView memory | CSS coverage is ours to build; MathML and complex SVG need another route (C) |
| **C. Hybrid** | B for text, plus a hidden WebView that renders islands (MathML, SVG, complex tables) into images placed in the flow | As B | B's speed with A's fidelity where it matters | Two engines; island sizing and baselines; selection inside islands |
| **R. Readium** | Readium Kotlin toolkit 3.4.0 (BSD-3-Clause, 2026-09-11): EPUB parsing (streamer) and a WebView navigator | Its navigator pages in its own View, so the curl would also need snapshots | Mature, maintained, handles spec edge cases and locators | Large; its navigator UI does not fit our curl; we would likely keep only its parser and locator model |

**Readium was dropped on 2026-10-20 (owner decision).** It needs Google's `desugar_jdk_libs`, which is GPL-2.0
with the Classpath Exception, and ADR-0003 allows no GPL code in the app. Instead, we write our own EPUB parser,
prototyped in the spike with protection against hostile files. ADR-0020 will record this.

### What is measured, on the unfolded Fold, release build

- **Correctness:** joined page text equals source text at 80, 100, 130, 170 and 220% font sizes. Also no split lines,
  and widow and orphan rules respected.
- **Speed:**
  - time to the first page of a chapter;
  - time to paginate a whole book (a 1,500-page synthetic textbook and Moby-Dick);
  - re-pagination after a font change.
- **Turning:** curl frame times and jank, with page images built ahead of time, and memory after 300 turns.
- **Fidelity:** a feature matrix across the corpus (headings, lists, tables with merged cells, figures, SVG, MathML,
  footnotes, asides, page breaks, embedded fonts, right to left).
- **Selection and locators:**
  - mapping a touch to a text position;
  - mapping a highlight to a stable locator (EPUB CFI or a text-quote selector, as in `conversion-spec.md` section 5)
    and back after re-pagination.
- **Size:** what each candidate adds to the APK.

### Corpus

- **Generated by us:** `tools/testepubs/make_test_epubs.py`, standard library only, like the PDF robustness generator.
  - A 1,500-page synthetic textbook in the converter's output shape: MathML, SVG, merged-cell tables, asides,
    footnotes, page breaks and a page list.
  - A right-to-left book.
  - Damaged and hostile files: a zip bomb, path traversal, XML entity expansion, missing files and broken markup.
- **Real books** (download pending the owner's approval): IDPF EPUB 3 samples, CC BY-SA 3.0, about 9 MB in total.
  - `linear-algebra` (MathML)
  - `moby-dick` (long novel)
  - `accessible_epub_3` (tables and structure)
  - `wasteland` (footnotes)
  - `georgia-cfi` (CFI test)
  - `childrens-literature` (images)
  - `page-blanche` (fixed layout)
  - They stay local and are not committed, like the PDF corpus; only the manifest and hashes are committed.

### Expected outcome (to be proven, not assumed)

C is the likely winner. Reflowed textbooks are mostly text, and turning pages is where Phase 1 set the bar, so a native
engine with exact text positions fits our curl, selection and memory budget best. A WebView renders the pieces that
native code shouldn't attempt. The spike may prove A good enough, which would be simpler. Readium's parser and locator
model may be worth adopting even if its navigator is not. **ADR-0019** records the decision, and **ADR-0020** records
how EPUBs are parsed (our own code or Readium).

## Step 2 onward: milestones (refined after the spike)

1. **M1, EPUB in the library.**
   - A new `:core:epub` module: the container, the package document, nav or NCX, the spine, metadata, the cover, and
     sanitising (Room gains a `format` column on books).
   - Import of `.epub` from the picker and the share sheet; covers and details as for PDFs.
2. **M2, the paginated reader.**
   - The engine from ADR-0019, with a page cache under ADR-0013's budget.
   - Single pages and spreads; Curl, Slide and Scroll.
   - Positions saved as locators; Contents, Go to page through the `page-list`, the scrubber; links and footnote
     popovers.
3. **M3, typography.**
   - The "Aa" panel's text settings and pinch for font size.
   - Re-pagination that keeps the position; the book's own fonts versus ours; hyphenation.
4. **M4, Phase 1 features on EPUB.**
   - Bookmarks, highlights and notes with locators; the Annotations tab; Share, Copy and Translate.
   - Right to left; the image viewer; fixed-layout EPUBs, one spine item per page, if the spike shows it is cheap.
     Otherwise they move to Phase 8.
2b. **M2b, the riffle scrubber** (added by the owner 2026-10-20; spec `docs/design/riffle-scrubber-spec.md`, mock
    `docs/design/FlowFolio_Riffle_Scrubber.html`, ADR-0022 and ADR-0023). It replaces the reader's scrubber for PDFs
    and EPUBs, in five phases (R1 logic, R2 drawing, R3 gestures and loupe, R4 landing and auto-hide, R5 the real
    reader, accessibility and performance). EPUB links and footnote popovers follow it.
5. **M5, close-out.** Memory and benchmarks, the robustness corpus, tests, and a sign-off report with the owner.

Each milestone is checked on the Fold with screenshots and measurements, as in Phase 1. The owner's position, settings
and annotations are restored after every device session.

## Out of scope for Phase 2

- The converter and FlowFolio mode for PDFs (Phase 3).
- Search inside books, text to speech and dictionary (Phase 8).
- DRM-protected EPUBs: they are detected and refused with a clear message.
- Audio and video in EPUBs: shown as a placeholder.

## Risks

- **WebView snapshots (A, C) may be too slow** for page-ahead rendering at 120 Hz. The spike measures this before
  anything is built on it.
- **CSS from arbitrary books** can be wild. We support a documented subset, and the rest falls back gracefully,
  checked against the corpus.
- **Line breaking and hyphenation quality** must match good e-readers. Android's `LineBreaker` and `Hyphenator` cover
  most languages; the spike checks them.
- **Locators must survive re-pagination and re-conversion.** Phase 3 needs the same scheme (`conversion-spec.md`
  section 5).

## Approvals needed before or during the spike

1. **Downloads (approved and done 2026-10-20):** the seven IDPF samples listed above, about 9 MB in total, from
   `github.com/IDPF/epub3-samples` (release 20230704).
2. **Readium in the spike:** approved, then dropped by the owner because of the GPL-licensed desugaring library it
   requires (see the note under Candidates).
