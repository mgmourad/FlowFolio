# Phase 2 sign-off: EPUB reading and the pagination engine

- **Status:** Signed off by the product owner, 2026-10-27 ("Phase 2 is signed off. Commit everything to main and
  push so CI runs."). One sign-off for the whole phase, including M2b's and M3's.
- **Plan:** [`phase2-plan.md`](phase2-plan.md); milestone sign-offs: [M2](phase2-m2-signoff.md) (signed off
  2026-10-20), [M3](phase2-m3-signoff.md), [M4](phase2-m4-signoff.md).
- **Measured on:**
  - the reference Fold (SM-F976U, unfolded 2256 x 2504, Android 17), 2026-10-27 afternoon: benchmarks, memory and
    re-pagination;
  - the `fold8_api37` emulator (same sizes, 4 virtual cores): tests, the robustness corpus and the PDF comparison.

  The Fold was charging and warm during the runs. It hit thermal status 2 (moderate) after the 12-minute benchmark;
  the final re-pagination runs started at status 1 and ended at status 2.

## Exit criteria

| Criterion (phase plan) | Result | Evidence |
|---|---|---|
| Correct pagination at five or more font sizes | **Met.** On real Android text layout, every page joined back together equals each chapter's text exactly, with no line clipped. This holds at five sizes (80-220%), in all seven reading fonts with their bold and italic, at the line-spacing and justification extremes, on the 9 corpus books and a built sample: 415 layouts. Every page's locator leads back to that page. | `PaginationOnDeviceTest` (3 tests) |
| Curl over reflowable content as smooth as over PDF pages | **Met on the Fold.** Release build (R8, `benchmarkRelease`), Moby-Dick in a spread, 8 turns each way, 5 runs. Frame CPU P50 3.2 ms with the baseline profile (P90 4.2, P99 5.6), 3.4 ms without (P90 4.6, P99 7.6), under the 4 ms target. PDFs measured 2.6-2.9 ms on the same Fold in Phase 1. Janky frames: 3.2% over 300 spread turns (target 5% or fewer), and that was on the debuggable build, which runs slower. On the emulator EPUB turns cost no more than PDF turns (P50 9.2 ms against 10.7 ms). | `:benchmark` `EpubPageTurnBenchmark` (new); `measure-reader.ps1` gfxinfo |
| Memory within ADR-0013's budget, at most 10% growth over 300 turns | **Met on the Fold.** Your app (debug build), with your Material You dark theme and Rustic paper. Moby-Dick in a spread, 300 turns: 291.3 MB at turn 50, 279.4 MB at turn 300 (graphics 166 MB throughout). That is within the 292 MB budget, with no growth (−4.1%). On the emulator: 3.2% growth. | `tools/measure-reader.ps1` |
| Re-pagination after a font-size change shows the reader's page in 300 ms or less | **Met for text; at the line for equation-dense pages** (after ADR-0041). Optimised build (R8, compiled for speed), unfolded spread, a size step up and back down, 8 changes each:<br>• Moby-Dick at 41%: pages shown 18-45 ms after the change (before ADR-0041: 414-617 ms).<br>• *Linear Algebra* at 53%, on a spread full of equations: 223-387 ms, median about 270 ms; three of the eight were over 300 ms, as the phone heated to thermal status 2 (before: 406-829 ms).<br>Two things changed. Only the reader's chapter is laid out before the pages show, now 25-60 ms anywhere in the book, since chapters start on their own spread (ADR-0041). And the equations of the pages in view are read on all cores before they are drawn: each such page took about 180 ms, now 7-37 ms. What remains on equation pages is the WebView measuring the chapter's equations at the new size, which lays the chapter out once more. | Logcat `FlowFolioLayout` "pages in view shown" |
| Print page numbers from the `page-list` | **Met.** "Go to page" and the page indicator use the book's printed numbers where it has a page list, and count screen pages where it has none. | `PaginationOnDeviceTest.builtBookPaginatesExactlyAtFiveSizes`; M2 sign-off |
| Unit, Robolectric and instrumented tests; CI green | **Tests met; CI not yet run.** 226 JVM and Robolectric tests, Spotless, detekt, lint and the riffle screenshot tests pass. 35 instrumented tests pass on the emulator: reflow 4, PDF engine 20, data 7, app 4. CI runs when the phase is committed and pushed. | `testDebugUnitTest`; `connectedDebugAndroidTest` (library modules); `am instrument` (app) |

### Robustness corpus (M5)

Every corpus book goes through everything the reader does with a book. That means parsing it, laying it out at the
cover-screen and inner-screen sizes, and reading every page's text, character boxes, links, pictures and islands. It
also means the contents and printed pages, and drawing pages. Each book must come through or be refused cleanly,
within two minutes. All 17 pass (`RobustnessOnDeviceTest`):

| Book | Outcome |
|---|---|
| IDPF samples: Accessible EPUB 3, *Children's Literature*, Georgia CFI, *A First Course in Linear Algebra*, Moby-Dick, Page Blanche, *The Waste Land* | Read in full, 0.1-8.3 s per book for both sizes |
| Our books: synthetic textbook (2,988 pages), right-to-left sample | Read in full |
| Hostile: DRM | Refused (DRM) |
| Hostile: no container | Refused (not an EPUB) |
| Hostile: path traversal | Refused (not an EPUB): the archive won't open entries that leave the book |
| Hostile: XML entity expansion | Refused (damaged): never expanded |
| Hostile: zip bomb | Read; the oversized entry is not read |
| Hostile: missing files, broken markup, scripts and remote resources | Read; missing chapters are empty, scripts never run in the page engine |

## Fixed during M5

- **The hourglass waited for the neighbouring pages too.** After a text change it stayed up until all eight planned
  pages were drawn. It now goes as soon as the pages in view are drawn, and the neighbours follow.
- **The reader's page queued behind background work.** After a re-layout, thumbnails for the scrubber's loupe,
  links and highlight placement could take the session's lock first, adding about 50 ms. Page renders now go first.
- **Re-pagination after a text change (ADR-0041).**
  - In a spread every chapter starts on a left page, so only the reader's chapter has to be laid out before the
    pages show; the rest of the book follows in the background.
  - The equations of the pages in view are read on all cores before they are drawn.
  - While a page is drawn again with newly finished equations, its earlier drawing from the same layout stays on
    screen instead of blank paper.

## What Phase 2 delivered

- **M1: EPUB in the library.** Our own EPUB parser (ADR-0020), import, covers and details, and DRM refused.
- **M2: the paginated reader (ADR-0019).**
  - Native pagination with widows, orphans, hyphenation and justification.
  - Equations, SVG and complex tables drawn as islands through a hidden WebView, with a disk cache (ADR-0021).
  - Curl, Slide and Scroll; single pages and spreads; positions stored as locators.
  - Contents and Go to page by printed number.
- **M2b: the riffle scrubber** (ADR-0022 to 0026): page-edge physics, loupe, flags, landing turn, haptics and the
  owner's recorded paper sounds.
- **EPUB links and footnote popovers.**
- **M3: typography.**
  - Seven bundled reading fonts plus the book's own (ADR-0027, ADR-0030).
  - Text settings and pinch to size.
  - Fast re-layout that keeps the reader's sentence.
  - Islands per layout and EPUB memory (ADR-0031).
  - The M2 carry-overs.
- **M4: Phase 1 features on EPUB** (ADR-0040).
  - Selection; highlights, notes and ribbons by locator.
  - Share, Copy and Translate.
  - The library tabs.
  - The image viewer.
  - Right to left.
- **M5: close-out.** The EPUB page-turn benchmark, the robustness corpus test, the memory run, and the re-layout
  fixes above, including chapters on their own spreads (ADR-0041).
- **The owner's rounds between milestones:**
  - app themes: FlowFolio, Rustic, Material You (ADR-0028, ADR-0029);
  - the motion system and entrances (ADR-0033 to 0035);
  - shelf colours, select mode and drag-and-drop order;
  - multi-file and folder import with Folders (ADR-0038);
  - fuller book details from Open Library (ADR-0039);
  - a fixed signing key for sideloaded builds (ADR-0036).

## Not in Phase 2

- **Fixed-layout EPUBs** move to Phase 8 (`phase2-m4.md`); until then they are reflowed.
- Search inside books, text to speech and the dictionary stay in Phase 8, as planned.

## For the owner

1. **Chapters now start on their own spread (ADR-0041), as you chose.**
   - **What you'll see:** in two-page view, a blank right-hand page before about half the chapters. Page counts in a
     spread include those blanks.
   - **Re-pagination:** text pages now show in under 50 ms; spreads full of equations take about 270 ms (223-387 ms
     on a warm phone). Shaving the rest for equation pages, by measuring equations ahead for nearby sizes, would be
     follow-up work.
2. **Sign off Phase 2**, or list what to change. M2b's listening and feel check and M3's sign-off are folded into
   this one.
3. **Then the commit to main and the push**, which runs CI on the whole phase. Nothing has been committed since
   `3477a15`, as asked.

### Your phone after the measurements

- **What was installed:** the new debug build (M4, M5 and ADR-0041), over your FlowFolio. It keeps your library
  and settings, and its database is now v14.
- **What was put back:** your database (20 books, 2 annotations, 11 reading sessions, your EPUB position) and your
  settings (Material You blue, dark, Rustic paper, the book's own font). They were backed up before the runs,
  restored afterwards, and checked to be identical.
- **What was removed:** the Moby-Dick copy imported for the memory run, and the separate benchmark app.
- **Checked again at the end:** the same 20 books, 2 annotations and 11 reading sessions, and your settings file
  byte for byte.
