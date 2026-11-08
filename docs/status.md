# FlowFolio — project status

- **Date:** 2026-11-08
- **Latest release:** FlowFolio **0.6.0-canary.1** (build 600001), tag `v0.6.0-canary.1`, GitHub pre-release.
- **Phase:** **6 of 7** signed off. Next: **Phase 7 — scanned books and OCR**.

## Where we are

FlowFolio is an Android app (Kotlin, Jetpack Compose) that reflows PDF textbooks into EPUB ("Dynamic Flow") and
reads them with a page-curl / two-page-spread reader, entirely on-device.

Six of seven phases are signed off and shipped. Both the **conversion engine** and the **core reading feature set** are
done: a reader can turn a PDF textbook into a reflowed, readable EPUB on the phone, search inside any book in either
view, look up words across Wiktionary, WordNet 3.1 and Wikipedia, read fixed-layout EPUBs page by page, and listen to
books read aloud offline with Kokoro and Piper neural voices. What remains is **scanned-book OCR** (Phase 7).

## What's done

| Phase | Scope | Signed off | Release |
|---|---|---|---|
| 0 | Scaffolding, CI, build structure | 2026-10-16 | — |
| 1 | PDF reader | 2026-10-20 | — |
| 2 | EPUB reading and the pagination engine | 2026-10-27 | — |
| 3 | Converter v1 on the phone | 2026-11-06 | 0.3.0-alpha.5 |
| 4 | Verification and good fallbacks | 2026-11-06 | 0.3.0-alpha.6 |
| 5 | The product flow around conversion | 2026-11-07 | 0.5.0-canary.1 |
| **6** | **Reading features (search, fixed-layout EPUB, neural TTS)** | **2026-11-08** | **0.6.0-canary.1** |

A great deal of owner-requested product work landed alongside the phase plan (app themes, Reading Atmosphere, VoiceMark,
reading goals, the first-launch tour, shelves, folder import, favourites, finished, library search, in-app updates,
storage housekeeping, the three-source dictionary, and the unfolded vertical tool rail).

### What Phase 6 delivered (most recently)

- **Narrator Mode with offline neural voices** (`:core:voice`, ADR-0051) — 11 Kokoro voices and 2 Piper voices running
  100% offline via `sherpa-onnx`, downloaded on first use with SHA-256 verification and a non-blocking progress overlay;
  streaming sentence synthesis, structural pauses for titles/subtitles/paragraphs, zero-gap mid-sentence page
  transitions with cross-page pre-queueing and page-turn sounds, line-break and cross-page hyphen rejoining, and a
  2-pole Butterworth `8.2 kHz` vocoder de-hiss filter.
- **In-book search** — case-insensitive phrase search across both Dynamic Flow chapters and PDF pages, with surrounding
  snippets, jump-to-match, and transient match highlighting.
- **Fixed-layout EPUBs** — `rendition:layout: pre-paginated` EPUBs render one spine item per page at viewport size via
  `FixedEpubPageSource`.
- **Three-source Define** — offline English Wiktionary, bundled Princeton WordNet 3.1 (`wordnet.db.gz`), and online
  English Wikipedia opening summaries.
- **Reading-surface precision & vertical tool rail** — unfolded right-edge vertical tool rail with active panel glow,
  first-use button guides, Bookmarks grouped by book, camera-clear folded running heads, and formatted Markdown in the
  "What's new" dialog.

## What's next

### Phase 7 — scanned books (beta)

Books that are pictures of pages become readable:

1. **OCR decision and ADR** (M1) — settle the licence first: ONNX Runtime vs ML Kit (proprietary, already blocks
   F-Droid). ADR-0003's no-GPL rule covers model weights too.
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

All ADRs describing shipped code are **accepted** (0044–0051); nothing shipped rests on a `Proposed` decision.

For the detailed plan, read `PLAN.local.md` (private) and the phase sign-offs in `docs/plans/`.
