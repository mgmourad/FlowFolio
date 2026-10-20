# Phase 1 sign-off: PDF reader with page curl

- **Status:** Ready for the product owner's review (2026-10-20)
- **Phase plan:** the brief's section 10; build plan approved 2026-10-16; milestone 5 plan in
  [`phase1-m5-design.md`](phase1-m5-design.md)
- **Measured on:** the reference Galaxy Z Fold, unfolded (2256 x 2504), Android 17, with OpenStax *University Physics
  Volume 1* (947 pages, local testing only)

## Exit criteria

| Criterion (brief) | Result | Evidence |
|---|---|---|
| Smooth curl on a 900-page PDF | **Met.** In a release build (R8), turning both ways, frames take 2.6-2.9 ms of CPU at P50 and 5-10 ms at P99; the P99 varies from run to run. Frame overrun at P99 is 1.5-2.7 ms past the 8.3 ms deadline at 120 Hz, so the slowest 1% of frames are a little late. Measured with gfxinfo on a non-debuggable build: 2-4% janky frames on taps and swipes, 1-2% on slow drags. | `:benchmark` `PageTurnBenchmark`; `tools/measure-reader.ps1` |
| Bounded memory | **Met.** 282 MB after 300 spread turns, against the 292 MB ceiling (ADR-0013 as revised in CLAUDE.md issue 14). Memory grew 6.9% from turn 50 to turn 300 (limit 10%). The growth is loaded library code and shared runtime pages; graphics stayed flat at 139 MB. | `measure-reader.ps1` (results kept locally, `tools/results/` is not committed) |
| Unit and UI tests | **Met.** All 123 JVM and Robolectric Compose tests pass. All 29 instrumented tests pass on the Fold: PDF engine 20, import 5, app 4 (including pinch zoom and selecting text while zoomed). | `testDebugUnitTest`; `am instrument` |

Beyond the exit criteria, Phase 1 also has:
- **A baseline profile**, recorded for cold start and for page turns, and shipped through `profileinstaller`. Over two
  benchmark runs on the Fold it made no measurable difference. P50 frame time and cold start (0.20-0.24 s to the
  library) were the same with or without it, and P99 moved more between runs than between modes. Android 17
  already optimises the app soon after install. We ship the profile because it helps fresh installs and costs
  nothing, and the benchmark will show if that changes.
- **Repeatable measurements:** the `:benchmark` module, documented in the CLAUDE.md commands.

## What Phase 1 delivered

- **Milestones 1-2: engine and library.**
  - PDFium through our own JNI bridge (ADR-0010).
  - Import: a copy into the app, or "read in place", each with its disclaimer.
  - A library with covers, and Room for book data.
- **Milestone 3: reader.**
  - The vendored 3D page curl (ADR-0011).
  - A single page on the cover screen and a two-page spread when unfolded, with the cover alone and print pairing.
  - A byte-budgeted cache of 16-bit GPU page images, kept to the memory budget (ADR-0013).
- **Milestone 4: navigation.**
  - The reading position is saved and resumed.
  - Contents, Go to page by printed page number, and a scrubber.
  - PDF links, and pinch zoom to 6x with sharp tiles.
- **Milestone 5: the design mock** (ADR-0014 and ADR-0015), in seven slices plus the owner's tweak rounds.
  - **Design system:** fonts, glass surfaces, the app shell with a rail when unfolded, and the splash.
  - **Library screens:** Library, Shelves and Book details, with the cover flight and book-opening animations.
  - **Reader controls:**
    - glass bars and ribbon bookmarks in colours;
    - paper styles: Auto, Paper, Sepia, Night and Black;
    - turn styles: Curl, Slide and Scroll;
    - right-to-left books.
  - **Bookmarks tab and Settings.**
  - **Book data:** editable details, book type and time read (Room v5).
  - **Annotations** (ADR-0016): highlights in five colours, notes, Share and Copy, an Annotations tab, and selection
    with a magnifier.
  - **Online services:**
    - an Open Library lookup, off by default (ADR-0017);
    - on-device translation with history, using ML Kit (ADR-0018).
  - **Printed page numbers:** from PDF page labels, or the numbers printed in the margins.
- **Smoothness pass after the owner's feedback:**
  - turns land with no hitch;
  - the spread's left page follows the curl in both directions, with no flicker;
  - selecting text no longer recomposes the reader.

## Decisions the owner still has to make

1. **Accept or amend four Proposed ADRs.**
   - [0014](../adr/0014-design-language-and-night-pages.md): design language and Night pages.
   - [0016](../adr/0016-annotations-and-reading-direction.md): annotations and reading direction.
   - [0017](../adr/0017-online-services.md): online services (Open Library; its Translate part is superseded).
   - [0018](../adr/0018-on-device-translation.md): on-device translation.
2. **Sign off Phase 1**, so Phase 2 can be planned.

## Known limits carried forward

| Item | When it is handled |
|---|---|
| **No in-app licences and privacy screen.** It must cover PDFium, the fonts and ML Kit's usage data (CLAUDE.md issue 8; ADR-0018). | Before any release (Phase 8 at the latest) |
| **The library holds 365-410 MB in dark mode**, mostly GPU caches for glows. A reader opened from it reads about 456 MB in the same process. "Neon glow" off saves 70-100 MB. | Phase 8 performance profiling; sooner if the owner wants |
| **ML Kit adds about 17 MB** to the install on arm64, and each language downloads a model of about 30 MB. | Accepted (ADR-0018); a "Manage languages" setting later |
| **Reading reminders are not built** (deferred in the milestone 5 plan). | Phase 8, with notifications |
| **Reflow mode is not built.** FlowFolio mode is meant to be the default, but it needs the converter. | Phases 2-3 |
| **The first frame of a selection drag** can run past one 120 Hz frame. Turning back uses the GPU close to the frame budget, at about 7.7 ms of 8.3 ms. | Watch in Phase 8 profiling |

## What comes next

These phases come from the brief, adjusted by ADR-0009: conversion runs on the phone, so there is no upload step.

- **Phase 2: EPUB reading and pagination engine.** The plan for Phase 2 is:
  - a spike comparing ways to paginate reflowable text, then an ADR;
  - opening EPUBs, paginating them, and turning their pages with the same curl;
  - typography settings and print page numbers from the EPUB page list;
  - exit criteria: correct pagination at several font sizes, and the curl working over reflowable content.
  - The pagination engine is also what will display FlowFolio (reflow) mode.
- **Phase 3: converter v1, on the phone** (`:core:conversion`). It turns born-digital PDFs into EPUB plus a sidecar
  file. It also needs a corpus harness with metrics.
- **Phase 4: verification.** Repair and pixel comparison, a health report, and a CI quality gate.
- **Phase 5: specialised content.** Equations, tables, figures, footnotes and cross-references.
- **Phase 6: integration in the app.** Conversion jobs, progressive delivery, a dual view and the health report.
- **Phase 7: study features.**
- **Phase 8: platform polish.** Text to speech, search, sync, accessibility, and the release build.
- **Phase 9: scanned books.**

Phase 2 starts with its own plan and spike proposal for the owner's approval, as with every phase.
