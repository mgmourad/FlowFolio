# ADR-0054: Read scanned pages with PaddleOCR, and Arabic with PaddleOCR and Tesseract checking each other

- **Status:** Accepted (design, owner 2026-11-11). Each new dependency is confirmed when M2 adds it.
- **Date:** 2026-11-11
- **Deciders:** product owner, lead engineer
- **Phase:** 7 (M1)

## Context

Phase 7 makes scanned books reflow (`docs/plans/phase7-plan.md`), in English and Arabic (owner, 2026-11-10). The
converter already detects scanned pages (`PageKind.SCANNED`) and keeps them as pictures. It needs an engine that:
- reads them on the phone;
- runs in CI on the JVM, so CI can test scanned books;
- has a permissive licence for code and weights (ADR-0003);
- says when a page cannot be trusted, so the page stays a picture rather than inventing text.

The M1 spike (`docs/spikes/2026-11-ocr/`) measured 20 proofread English pages and 20 Arabic ones (10 printed, 9
handwritten).

## Options considered

1. **PaddleOCR PP-OCR models (Apache-2.0) on ONNX Runtime**, the way RapidOCR runs them.
   - English: PP-OCRv6 small reads 98.7% of words (median), with 86% on its worst page, and its own confidence
     separated good pages from bad ones perfectly at 0.85.
   - Arabic (PP-OCRv5 mobile): 74–91% of words on modern print, poor on pre-1950 type.
   - The runtime is already in the app (ORT 1.28.2, via sherpa-onnx).
2. **ML Kit Text Recognition v2.** Ruled out: no Arabic, Android only (CI could not test it), proprietary.
3. **Tesseract 5 (Apache-2.0).**
   - English 98.3% median, but 32% on its worst page.
   - Arabic: better than option 1 on 3 of the 4 usable pages (77–94%).
   - Its confidence cannot be trusted alone: it was 95% sure of a handwritten page it read as nonsense.
4. **Both on Arabic, keeping the one that reports the higher confidence** (the owner's first idea). Simulated on the
   spike's outputs, it chose the weaker text on 3 of the 4 good pages, because the two engines' confidences are on
   different scales. It also showed the handwritten page as text.
5. **Both on Arabic, checking each other** (owner's choice, 2026-11-11).
   - On every usable page the two engines' words agreed 49–86%; on every bad or handwritten page, 11% or less.
   - Agreement is a measure neither engine can inflate by being overconfident.

## Decision

- **English and other Latin-script languages: PaddleOCR (option 1).**
  - PP-OCRv6 small, a download of about 30 MB.
  - A page is kept as text only when its mean line confidence is ≥ 0.85.
- **Arabic: PaddleOCR and Tesseract together (option 5).**
  - Both read each scanned Arabic page.
  - **Agreement decides whether** the page becomes text: the share of words the two outputs have in common must reach
    **40%** (to be tuned in M3).
  - **Tesseract's text is what is shown**, since it read the usable pages better.
  - Below the bar, the page stays a picture (`fallback:ocr-disagreement`).
  - Downloads: PaddleOCR PP-OCRv5 Arabic (about 12 MB) and Tesseract's `ara` best model (12.6 MB).
- **Packs:** offered when a scanned book is first opened, like the voice packs. The files are mirrored on FlowFolio's
  GitHub releases with pinned SHA-256s.
- **On the phone:**
  - PaddleOCR runs through a small JNI bridge to the C API of the ONNX Runtime the app already bundles, as PDFium
    has its own bridge (ADR-0010).
  - Tesseract 5 and Leptonica are built from source with the NDK, for the same ABIs as PDFium.
  - Detection post-processing, CTC decoding and word agreement are written in Kotlin in `:core:conversion`, behind an
    `OcrEngine` interface.
- **In CI and desktop tooling:** ONNX Runtime Java 1.28.0 and `tess4j` (Tesseract through JNA), in `:tools:convert`
  only, as JNA is (ADR-0044).
- **Arabic lines** from PaddleOCR are reordered from visual to logical order with `java.text.Bidi` /
  `android.icu.text.Bidi`. Never `python-bidi` (LGPL).

## Consequences

- Positive:
  - each language gets the stronger reader;
  - Arabic pages are checked by two independent engines, so a confident wrong reading by one is caught by the other;
  - manuscripts and unreadable pages stay pictures;
  - CI tests exactly the models the phone runs.
- Negative / accepted risks:
  - **Arabic printed before about 1950 will mostly stay pictures.** Both engines read at most about half of its
    words. Reflowing it needs a stronger Arabic model, and no permissive one was found.
  - **Arabic pages take about twice as long:** an estimated 2–3 s each on the Fold, above the plan's 2 s. M2
    measures it.
  - **A second native engine to build and maintain.** The APK grows by a few MB per ABI.
  - The 40% agreement bar rests on four usable Arabic pages. M3's larger corpus must confirm it before release.
  - The JNI bridge tracks the ORT that sherpa-onnx ships. When sherpa-onnx updates ORT, OCR must be rechecked.
- **New dependencies, confirmed with the owner as M2 adds each:**
  - ONNX Runtime Java `com.microsoft.onnxruntime:onnxruntime:1.28.0` (MIT, released 2026-07-27; the newest is
    1.31.1), desktop and CI only. No 1.28.2 Java build is published, and patch releases do not change how models run.
  - The ONNX Runtime C headers (MIT) at 1.28.2, for the phone's JNI bridge.
  - Tesseract 5.5 (Apache-2.0) and Leptonica (BSD-2-Clause), built from source for Android.
  - `tess4j` 5.21.0 (Apache-2.0, released 2026-11-07), desktop and CI only.
  - The model files: PP-OCR (Apache-2.0) and `tessdata_best` `ara` (Apache-2.0), mirrored as release assets.
- Follow-ups:
  - M2 measures speed on the Fold.
  - M3 builds a larger Arabic corpus, including pages like the owner's own books, and tunes both bars.
