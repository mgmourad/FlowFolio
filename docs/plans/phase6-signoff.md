# Phase 6 sign-off: reading features

- **Status:** Signed off by the product owner, 2026-11-08.
- **Plan:** `PLAN.local.md`, Phase 6 — "reading features". Milestone plans:
  [`phase6-m0.md`](phase6-m0.md) (reading-surface precision),
  [`phase6-m1.md`](phase6-m1.md) (in-book search),
  [`phase6-m2.md`](phase6-m2.md) (fixed-layout EPUBs),
  [`phase6-m3.md`](phase6-m3.md) and [`phase6-m3-neural-tts.md`](phase6-m3-neural-tts.md) (Narrator Mode with offline
  neural voices).
- **Decisions ratified with this sign-off:** [ADR-0051](../adr/0051-neural-tts-via-sherpa-onnx.md) (offline neural TTS
  via `sherpa-onnx` with Kokoro and Piper voice packs).
- **Measured and verified on:** the owner's physical Galaxy Z Fold (`RFGL7318RKK`, folded and unfolded) and the JVM/
  Robolectric test suite (`spotlessCheck detekt lintDebug testDebugUnitTest`), gated by green CI (`ci-gate`, including
  the Linux converter corpus gate).

## What Phase 6 delivered

Phase 6 completed the core reading experience so FlowFolio handles everything a serious reader expects: finding phrases
inside a book, looking up definitions across three reference sources, rendering fixed-layout EPUBs page by page, and
reading books aloud with natural offline neural voices.

- **M0 — Reading-surface precision and first-use guides.**
  - First-use guides on the reader's chrome buttons (Contents, Go-to-page, Aa, ribbon, Annotations, Dictionary), shown
    once on first tap with "Got it" and "Never show tips again".
  - The Bookmarks tab groups ribbons by book (one card per book showing its title, author, ribbon colour swatches, and
    count; tapping opens that book's ribbons and colour filter).
  - Top-level screen titles share the header button row (`ScreenHeader`); the folded running head stays clear of the
    front camera cutout; the unfolded section label right-aligns to the outer margin; the running head hides while
    reader chrome is open; and long book titles in Details cap at three lines with tap-to-expand.
- **M1 — In-book search and three-source Define.**
  - Case-insensitive in-book search (`BookSearch`, `ReaderSearchPanel`) across both Dynamic Flow (`ReflowBook`) and
    original PDF (`PdfDocument`) views, with chapter/section labels, surrounding snippets, jump-to-match, and transient
    match highlighting that clears when dismissed.
  - The reader's Define panel now queries three sources at once: offline **English Wiktionary**, bundled **Princeton
    WordNet 3.1** (`wordnet.db.gz`, 132k entries with synonyms and hypernyms), and online **English Wikipedia** opening
    summaries for specialized and technical terms.
  - Added R8 keep rules for `com.google.mlkit.**` so on-device translation works reliably in minified release builds.
- **M2 — Fixed-layout EPUBs.**
  - EPUBs declaring `rendition:layout: pre-paginated` open through `FixedEpubPageSource`, rendering each spine item at
    its viewport dimensions in an isolated, network-blocked WebView into the page-curl reader rather than reflowing
    page-designed books.
- **M3 — Narrator Mode (offline neural TTS via `sherpa-onnx`).**
  - Built `:core:voice` on `sherpa-onnx` v2.0.1 (`SherpaOnnxConventionPlugin`, SHA-256 verified at build time) with two
    download-on-first-use neural engines: **Kokoro** (`kokoro-int8-multi-lang-v1_0`, 11 US/UK English voices in one
    shared download) and **Piper VITS** (`Amy` US female and `Alan` UK male).
  - Streaming synthesis (`OfflineTts.generateWithCallback` via an explicit `ChunkCallback` `Function1` class kept under
    R8) feeds a persistent `AudioTrack` queue so playback begins on the first sentence without waiting for the full page.
  - Structural speech cadence (`SpeechText`): distinct natural silences after titles (`480 ms`), subtitles (`340 ms`),
    paragraphs (`320 ms`), sentences (`150 ms`), and long clauses (`65 ms`); `0 ms` pause when a page ends mid-sentence,
    with the next page's opening sentence pre-queued before auto-advancing and the page-turn sound played on flip.
  - Text conditioning rejoins words hyphenated across line breaks (including PDFium `\u0002` and soft `\u00AD` hyphens)
    and across page boundaries, strips running headers and folios on mid-sentence page turns, expands numbers, and
    generates regular plural (`-s`/`-es`/`-ies`) and past-tense (`-d`/`-ed`/`-ied`) lexicon inflections on-device.
  - Audio conditioning (`PcmAudio.cleanVocoderBand`) applies a 2-pole Butterworth low-pass filter at `8.2 kHz`, a soft
    downward expander below `-41 dBFS`, and `6 ms` raised-cosine edge fades to eliminate 8-bit neural vocoder hiss.
  - Non-blocking download progress overlay in the reader chrome so downloading or switching voices never interrupts
    reading.
- **Unfolded vertical tool rail & polish.**
  - On the unfolded inner screen, the reader's secondary tools (Aa, Narrator, Bookmark, Annotate, Translate, Search,
    Contents) sit in a vertical glass rail along the right edge with an active-panel glow; the "What's new" dialog renders
    formatted Markdown release notes and sequences cleanly after the first-launch tour.

**Release.** One pre-release ships from Phase 6: **0.6.0-canary.1 (600001)**, tag `v0.6.0-canary.1`.

## Exit criteria

| Criterion (phase plan) | Result | Evidence |
|---|---|---|
| Search inside a book in both views and jump to highlighted matches | **Met.** Searches reflowed chapters and PDF pages, jumps to the match, and highlights it transiently. | `BookSearchTest`, `ReaderSearchPanel.kt`, `ReflowBook.kt` |
| A fixed-layout EPUB looks like itself | **Met.** `rendition:layout: pre-paginated` EPUBs render one spine item per page at viewport size via `FixedEpubPageSource`. | `FixedEpubPageSource.kt`, `ReaderViewModel.kt` |
| The app can read aloud offline with natural voices and continuous page turns | **Met.** 11 Kokoro voices + 2 Piper voices stream offline via `sherpa-onnx` with structural pauses, hyphen joining, de-hiss filtering, and auto-advance with page-turn sound. | `:core:voice`, `Narrator.kt`, `SpeechTextTest`, `PcmAudioTest`, live stress test on `RFGL7318RKK` |

## Ratify ADR-0051

| ADR | What it decided | Why accept |
|---|---|---|
| [0051](../adr/0051-neural-tts-via-sherpa-onnx.md) | Offline neural TTS via `sherpa-onnx` v2.0.1 (`:core:voice`) with download-on-first-use Kokoro and Piper voice packs and lexicon phonemization | Ships in `0.6.0-canary.1`; keeps the app 100% offline after first voice download and free of GPL `espeak-ng` dependencies. |

## What comes next

Phase 7 — scanned books and OCR (`PLAN.local.md`).
