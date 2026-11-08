# FlowFolio — project status

- **Date:** 2026-11-07
- **Latest release:** FlowFolio **0.5.0-canary.1** (build 500001), tag `v0.5.0-canary.1`, GitHub pre-release.
- **Phase:** **5 of 7** signed off. Next: **Phase 6 — reading features**.

## Where we are

FlowFolio is an Android app (Kotlin, Jetpack Compose) that reflows PDF textbooks into EPUB ("Dynamic Flow") and
reads them with a page-curl / two-page-spread reader, entirely on-device.

Five of seven phases are signed off and shipped. The **conversion engine is done**: a reader can turn a PDF textbook
into a reflowed, readable EPUB on the phone, watch chapters appear as they convert, and see at a glance how well the
conversion went. What remains is **reader-side completeness** — in-book search, fixed-layout EPUBs, text-to-speech and
accessibility — and **scanned-book OCR**.

## What's done

| Phase | Scope | Signed off | Release |
|---|---|---|---|
| 0 | Scaffolding, CI, build structure | 2026-10-16 | — |
| 1 | PDF reader | 2026-10-20 | — |
| 2 | EPUB reading and the pagination engine | 2026-10-27 | — |
| 3 | Converter v1 on the phone | 2026-11-06 | 0.3.0-alpha.5 |
| 4 | Verification and good fallbacks | 2026-11-06 | 0.3.0-alpha.6 |
| **5** | **The product flow around conversion** | **2026-11-07** | **0.5.0-canary.1** |

A great deal of owner-requested product work landed outside the phase plan (app themes, Reading Atmosphere, VoiceMark,
reading goals, the first-launch tour, shelves, folder import, favourites, finished, library search, in-app updates,
storage housekeeping, the offline dictionary). So the app reads as further along than "Phase 5" alone suggests.

### What Phase 5 delivered (most recently)

- **Progressive delivery** — a converting book's first chapter is readable long before the whole book is done; chapters
  appear as they finish and the app reports "chapter N of M readable".
- **The conversion health badge** — the reader's "Aa" sheet grades how well a book converted, from the metrics already
  in `flowfolio.json`.
- **Settings as a menu of pages** — Appearance, Text, Reading, Atmosphere, Library, Dynamic Flow, Updates and Storage
  each open a page of their own; About and "Show me around" stay on the menu.
- **The polish slot** — the first-book demo with "Never show tips again", the reader's chapter-and-section running
  heads, the shelf-page header, annotation counts and VoiceMark tabs, and the Favourites page header.

## What's next

### Phase 6 M0 (done, 2026-11-07)

The reading-surface tweak batch that arrived ahead of Phase 6: first-use guides on the reader's buttons, the Bookmarks
tab grouped one card per book, page titles on the button row, the folded running head clear of the camera, and the
book-details title capped at three lines. `docs/plans/phase6-m0.md`; CHANGELOG Unreleased.

### Phase 6 — reading features (canary)

The three things a serious reader expects that the app still cannot do:

1. **Search inside a book** (M1) — find a phrase in the book you're reading, in both views, with jumpable results.
   Reflowed text is already indexed by chapter and offset, so the locator machinery exists.
2. **Fixed-layout EPUBs** (M2) — `rendition:layout: pre-paginated` is detected but ignored; such books are reflowed
   today, which is wrong for comics, children's books and anything designed page by page.
3. **Text to speech** (M3) — nothing exists; Android's own `TextToSpeech` keeps it offline and dependency-free.
4. **Accessibility pass** (M4) — screen-reader labels, focus order, contrast, touch targets.
5. **Polish slot** (M5).

**Done when:** you can search a book, a fixed-layout EPUB looks like itself, and the app can read aloud.

### Phase 7 — scanned books (beta)

Books that are pictures of pages become readable:

1. **OCR decision and ADR** (M1) — settle the licence first: ONNX Runtime (32.6 MB/ABI) vs ML Kit (proprietary, already
   blocks F-Droid). ADR-0003's no-GPL rule covers model weights too.
2. **OCR in the pipeline** (M2) — `PageKind.SCANNED` is already detected and passed through as an image; give it a text
   layer instead.
3. **Quality gate** (M3) — a scanned-book corpus and baselines, with a confidence threshold below which the page stays
   an image.
4. **Polish slot** (M4).

**Done when:** a scanned book reflows, and a bad OCR page falls back rather than inventing text.

## Open items

- **The real pixel diff (mechanism B)**, deferred from Phase 4 — `geometricCoverage` is recorded but not gated. It wants
  a headless HTML renderer (a new dependency → its own ADR + owner approval).
- **F-Droid self-hosted repo**, deferred — f-droid.org is blocked by ML Kit (proprietary), PDFium (prebuilt binary) and
  Chaquopy (pip at build time); a self-hosted repo has none of those constraints.
- **GitHub Actions secrets copy of the release signing key** (ADR-0049's follow-up) — the second off-machine location.
- **Launch posts** — written, not sent.

All ADRs describing shipped code are **accepted** (0044, 0045, 0046, 0047, 0048, 0049, 0050); nothing shipped rests on
a `Proposed` decision.

For the detailed plan, read `PLAN.local.md` (private) and the phase sign-offs in `docs/plans/`.
