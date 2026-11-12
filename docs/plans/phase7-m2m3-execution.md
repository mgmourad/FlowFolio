# Phase 7 — finishing M2 and building M3 (execution plan)

Owner approvals received 2026-11-11 (this session):
- Build Tesseract 5.5.4 + Leptonica 1.87.0 from source (latest versions), Apache-2.0 / BSD-2-Clause.
- `tess4j` 5.21.0 (Apache-2.0) for `:tools:convert` tests only + `libtesseract` on the Linux CI runner.
- Mirror the OCR model files to a FlowFolio GitHub release and repoint `OcrPack` at it.

## 0. Commit the inherited Arabic-book work (uncommitted, verified)

The previous session left a coherent, tested unit on top of `bc2f230`: typed-Arabic reading order
(`Extraction`/`TextDirection`/`RightToLeftLineTest`), right-to-left reading order (`ReadingOrder`), `BookScript`
page-voting, the progressive-EPUB OOM fix, the Mac-arm64 PDFium fetch (closes known issue 37), and the `convert chars`
debug command. Commit it before touching anything else.

## 1. Tesseract on Android (finish M2)

1.1. **Build Tesseract 5.5.4 + Leptonica 1.87.0 from source** with the NDK, ABIs arm64-v8a/armeabi-v7a/x86_64.
  - New `TesseractConventionPlugin` in `build-logic` (mirrors `PdfiumConventionPlugin` / `SherpaOnnxConventionPlugin`):
    fetch pinned source tarballs, verify SHA-256, cross-compile each ABI with CMake + the NDK toolchain into
    `libtesseract.so` + `libleptonica.so`. Leptonica built with no image codecs (raw pixels in, so no jpeg/png/tiff);
    Tesseract with training tools and OpenMP off, `Leptonica_DIR` pointed at the built Leptonica.
  - New `:core:ocr` module: the JNI bridge (`src/main/cpp`) + a `TesseractOcr : OcrReader` in Kotlin.
1.2. **`TesseractOcr`** reads an `OcrImage` with `tessdata_best` `ara` and returns `OcrLine`s (text + box + confidence)
  in reading order, Arabic reordered visual→logical (reuse `RtlText`). Engine id `tesseract-ara`.
1.3. **Wire it in**: `OcrPack.FILES` gains `ara.traineddata`; `OcrPack.readerIn` passes `tesseractArabic` to
  `ScannedPageReader(latin, arabic, tesseractArabic)`; `OcrGate(tesseract, paddleArabic)` then does the agreement.

## 2. JVM/CI Tesseract (tess4j)

- Add `tess4j` 5.21.0 to `:tools:convert` test scope; a `JvmTesseract : OcrReader` over it (parallel to `JvmOnnxModel`).
- `apt install libtesseract-dev` (and `tesseract-ocr-ara`? verify package) in `.github/workflows/ci.yml` so the corpus
  job can OCR Arabic pages. A test for the agreement path (`OcrGate(tesseract, paddleArabic)`) against the spike pages.

## 3. Speed on the real Fold

- Convert a 50-page English scan in a non-debuggable build in `:convert` on the owner's phone (known issues 22, 30).
  Target 2 s/page. If slow: batch recognition, more ORT threads, `ocrScale` 2.5. Record numbers.

## 4. Mirror the models

- Publish PP-OCRv6 det/rec, PP-OCRv5 Arabic, `ara.traineddata` as a GitHub release asset; repoint `OcrPack.FILES`
  URLs, keep the same SHA-256s.

## 5. M3: the scanned-books quality gate

- Corpus (manifest + hashes only): clean scans, skewed, two-column, footnotes, mixed typed+scanned, Arabic-with-English,
  typed Arabic. Ground truth ~30 EN / ~20 AR pages in `samples/golden/` (owner checks Arabic).
- CI baselines: per-page word recall + CER, share of pages kept as text, planted bad pages stay pictures.
- Tune 0.85 confidence / 40% agreement on the corpus; record numbers.
- Typed Arabic PDFs: verify the inherited fix; finish `page-progression-direction="rtl"` + `dir="rtl"`/`lang="ar"`.

## 6. Handover

Update CLAUDE.md, CHANGELOG, plan status; build the handover APK (new name); install on emulator (folded + unfolded)
and the owner's phone; report what was verified and what was not.

---

## Ledger

- **0 done**: inherited Arabic-book work committed as `9252845` (typed-Arabic order, RTL reading order, BookScript,
  OOM fix, Mac PDFium).
- **1.1 done**: `TesseractConventionPlugin` (fetch + cross-compile) and `:core:ocr` (bridge + `TesseractOcr`)
  build cleanly for all 3 ABIs. Sources pinned: Tesseract 5.5.4, Leptonica 1.87.0, and **cpu_features 0.9.0** (a
  transitive build dependency of Tesseract 5.5 on Android, Apache-2.0, statically linked — Ruling: added as part of
  "build Tesseract from source", not a separately-shipped lib; flagged to the owner).
- **1.2 done**: `TesseractPage` (shared post-processing) + `TesseractPageTest` green.
- **1.3 done**: `OcrPack.FILES` gains `ara.traineddata`; `readerIn` passes `TesseractOcr(dir, "ara")`.
- **2 done**: `tess4j` 5.21.0 test dep in `:tools:convert`; `libtesseract-dev` + `tesseract-ocr-ara` on the CI corpus
  runner.
- **On-device validation done** (`fold8_api37`): a 2-page scanned Arabic PDF converts with both pages read as
  `ocr:tesseract-ara` (word agreement 58% and 80%, above the 40% bar). Two bugs found and fixed along the way:
  `libc++_shared.so` had to be bundled (Tesseract is built against the shared libc++, the app's other libs use the
  static STL), and Tesseract's Arabic output is **logical** order so it must not be reordered (PaddleOCR's visual
  order still is).
- **Open risks**: CI android job (30 min timeout) will cross-compile Tesseract on its first run — watch at the phase
  boundary; may need a prebuilt-fetch fallback or a GitHub Actions cache.
