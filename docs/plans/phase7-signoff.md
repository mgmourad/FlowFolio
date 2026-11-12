# Phase 7 sign-off: scanned books (English and Arabic)

- **Status:** **Draft, awaiting the owner** (2026-11-12). Not signed off. 0.7.0-beta.1 went out before this
  sign-off (by the M3 session). 0.7.0-beta.2 was released on the owner's instruction (2026-11-12) to get the Arabic
  fix to readers; Phase 7 still closes with this sign-off.
- **Plan:** [`phase7-plan.md`](phase7-plan.md), with handoffs [`phase7-handoff-m3.md`](phase7-handoff-m3.md),
  [`phase7-handoff-m4.md`](phase7-handoff-m4.md) and the M2/M3 execution record
  [`phase7-m2m3-execution.md`](phase7-m2m3-execution.md).
- **Decisions in this phase:**
  - [ADR-0054](../adr/0054-ocr-engine.md), the engines, accepted 2026-11-11.
  - [ADR-0055](../adr/0055-arabic-agreement-by-letter-pairs.md), the Arabic agreement measure. **Proposed**: to be
    accepted with this sign-off.
- **Verified on:**
  - the `fold8_api37` emulator (debug and release builds);
  - the JVM suite (`spotlessCheck detekt lintDebug testDebugUnitTest`, `:core:conversion:test`, `:tools:convert:test`
    with the PDF corpus and the OCR gate), run on this Mac;
  - the owner's Arabic book, page by page.
  - **Not** on the owner's Fold, and **not** in CI since the M4 changes.

## Done when (the plan) — where it stands

> A scanned book reflows in Dynamic Flow, **in English and in Arabic**, and a bad OCR page falls back to its picture
> rather than inventing text.

| | Status | Evidence |
|---|---|---|
| English scans reflow | **Met** | OCR gate: 98.9% median word recall, 3.0% CER, 20 of 21 pages kept (bar 3% CER: met) |
| Arabic scans reflow | **Met for modern print; under the plan's accuracy bar** | Corpus: 4 of 10 printed pages kept at 93.0% recall but **16.0% CER** (plan's bar: 6%). The owner's 1960s book: 10 of 11 body pages kept after ADR-0055 (6 before) |
| Bad pages stay pictures | **Met** | The 9 manuscripts, a heavily blurred page and the English clipping read by the Arabic engines all stay pictures |
| Typed Arabic PDFs read correctly | **Met** | Reading order and line breaks fixed in M2 (`RightToLeftLineTest`); the owner's book's typed pages read in order |
| Right-to-left books | **Met** | `dir="rtl"`, `lang="ar"`, `page-progression-direction="rtl"`, right column first |
| At most 2 s a page on the Fold | **Not met** | Measured 2026-11-12 on the owner's Fold, release build: 4.4 s a page English, 7.0 s Arabic (known issue 55) |

## What Phase 7 delivered

- **M1:** the OCR spike and ADR-0054. PaddleOCR is used for English; for Arabic, Tesseract reads and PaddleOCR checks.
- **M2:**
  - PaddleOCR in Kotlin on the ONNX Runtime the app already ships (`OrtBridge`).
  - Tesseract 5.5.4 built from source for Arabic (`:core:ocr`).
  - The scanned-books pack, about 50 MB, downloaded on first use and mirrored on FlowFolio's releases with pinned
    hashes.
  - Typed-Arabic reading order, right-to-left books, and the per-book script vote.
- **M3:** a 41-page proofread corpus (`samples/golden/ocr/`), `OcrCorpusTest`, and its CI wiring.
- **M4 (this round):**
  - Verified M3, and fixed what that turned up: the Arabic gate (ADR-0055); the CI gate, which had never run; and
    stale and developer-facing text in the app.
  - Known issues 44, 45 and 51 closed (CHANGELOG, Unreleased).

## Left open

1. **Speed on the Fold** is 4.4 s a page for English and 7.0 s for Arabic, against a 2 s target (known issue 55).
   Batched recognition and fewer double readings are the next things to try.
2. **Arabic accuracy.** Kept pages read at about 16% CER on the corpus against the plan's 6%.
   - Better PaddleOCR Arabic models, or Tesseract's `ara` plus `script/Arabic`, are the next things to try.
   - The corpus needs mid-century book print with proofread text, which it lacks. The owner's book would be ideal if a
     few pages can be proofread.
   - The owner has not yet checked the Arabic ground truth, as M3's handoff asked.
3. **CI on the M4 changes** has not run: the fixed OCR job will run for the first time at the next push.
4. **M4's owner items:**
   - the owner's polish list (not yet given);
   - the second copy of the release key in GitHub Actions secrets (ADR-0049, the owner's step);
   - the launch posts;
   - F-Droid yes or no;
   - whether the tour needs a slide for scanned books.
5. **A page in two columns read by OCR** reads straight across both columns (the 1883 clipping); figures on scanned
   pages are dropped when the page becomes text.
