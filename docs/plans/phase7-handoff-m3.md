# Handoff prompt: Phase 7, finish M2 and build M3 (scanned books' quality gate)

Paste this to the next session. It assumes nothing from earlier conversations.

---

You are continuing **FlowFolio**, an Android reader (Kotlin, Compose) whose Dynamic Flow converts PDFs to reflowable
EPUBs on the phone. Read `CLAUDE.md` first, then `docs/plans/phase7-plan.md` (approved), `docs/adr/0054-ocr-engine.md`
(accepted) and `docs/spikes/2026-11-ocr/README.md`. Follow CLAUDE.md's working agreement:
- plan before code;
- never claim something works without running it;
- ask the owner before adding any dependency (licence, last release date, alternatives);
- Conventional Commits ending with the co-author line the conversation gives you;
- verify on the `fold8_api37` emulator, folded and unfolded;
- hand over a uniquely named debug APK;
- CI runs only at phase boundaries.

## Where things stand (2026-11-11)

Phase 7 makes scanned books reflow, **in English and in Arabic** (an owner requirement).

- **M1 is done.** ADR-0054 is accepted:
  - English: PaddleOCR PP-OCRv6 small, kept when its mean line confidence is at least 0.85;
  - Arabic: Tesseract *and* PaddleOCR PP-OCRv5 Arabic read each page; the page becomes text only when their words
    agree at least 40%, and Tesseract's text is used. Choosing by self-reported confidence was rejected: Tesseract
    was 95% sure of a manuscript it misread.
- **M2 is built for English** (see CHANGELOG, Unreleased, "Phase 7 M2"):
  - `:core:conversion/ocr`: PaddleOCR in Kotlin, which matches RapidOCR at 98.9% median word recall on the spike's
    pages. Also `ScannedPageReader`, `Script` (which keeps Arabic pages away from the Latin model; it was 99.8% sure
    of nonsense on clean Arabic), `OcrGate`, `OcrPages` and `OnnxMetadata`.
  - `:core:voice` `OrtBridge`: a C API bridge to the ONNX Runtime sherpa-onnx ships. ORT's Java API cannot load
    against it; known issue 53 explains why.
  - `:core:data/ocr` `OcrPack`: Settings → Dynamic Flow → Scanned books, a 38 MB download, wired into `FlowService`.
  - Verified on the emulator: a scanned sample reflows, and its Arabic page stays a picture.
- **Measuring tool:** `PaddleOcrScansTest` (`:core:conversion`) runs when given `FLOWFOLIO_OCR_PAGES` (the spike's
  `en-NN.jpg/.txt`, `ar-NN.jpg/.txt`, from `docs/spikes/2026-11-ocr/fetch_wikisource.py`) and `FLOWFOLIO_OCR_MODELS`
  (RapidOCR 3.10's `.onnx` files). Fetch Wikisource slowly: it rate-limits (429).

## Your task, in order

### 1. Finish M2

- **Tesseract on Android, for Arabic.**
  - No usable prebuilt exists: Tesseract4Android is JitPack-only, its 4.9.0 build failed there, and its last
    release was June 2025.
  - Build Tesseract 5.5 and Leptonica (BSD-2-Clause) from source with the NDK. Fetch pinned sources, as
    `PdfiumConventionPlugin` and `SherpaOnnxConventionPlugin` do. Target the ABIs PDFium uses (arm64-v8a,
    armeabi-v7a, x86_64), and feed it raw pixels so no image libraries are needed.
  - Expose an `OcrReader` that reads a page with `tessdata_best` `ara` (12.6 MB, Apache-2.0) and returns lines with
    boxes. Pass it as `ScannedPageReader(tesseractArabic = ...)`; `OcrGate(tesseract, paddleArabic)` already does the
    agreement.
  - Add `ara.traineddata` to the scanned-books pack (`OcrPack.FILES`).
  - For CI, add `tess4j` (5.21.0, Apache-2.0) to `:tools:convert` tests only, and install `libtesseract` on the Linux
    runner in `.github/workflows/ci.yml`.
  - **Ask the owner before adding each of these**, as ADR-0054 says.
- **Speed on the real Fold.**
  - Convert a 50-page English scan in a **release** build in `:convert`, on the owner's phone
    (`adb devices`: RFGL…). Follow known issues 22 and 30. The target is 2 s a page.
  - The emulator's debug build takes about 25–35 s a page, mostly recognition (one ONNX call per line). Debuggable
    builds run this Kotlin interpreted, so don't trust those numbers.
  - If it is slow, try batching recognition (RapidOCR batches 6 lines of similar width), more ORT threads, and
    `ocrScale` 2.5.
- **Mirror the model files** to a FlowFolio GitHub release and point `OcrPack` at it, pinning the same SHA-256s.
  Today they come from modelscope.cn. Publishing is outward-facing: ask the owner first.

### 2. M3: the quality gate for scanned books

Follow the plan's M3:
- **The corpus:** public-domain English and Arabic scans; manifest and hashes only, never the PDFs. Include clean
  scans, a skewed page, two columns, footnotes, a mixed typed-and-scanned PDF, an Arabic book with English words and
  numbers, and a typed Arabic PDF.
- **Ground truth:** about 30 English pages and 20 Arabic, typed to match exactly what each page shows. Ask the owner
  to check the Arabic ones.
- **CI checks against baselines:** word recall and CER per page; the share of pages kept as text; and that planted
  bad pages (handwriting, a photo, heavy blur) stay pictures.
- **Tune the 0.85 confidence gate and the 40% agreement bar** on this corpus, and record the numbers.
- **Check typed Arabic PDFs** (no OCR): PDFium may extract presentation forms or visual order. If they come out
  garbled, fix it; the plan puts that in this phase.
- **Right-to-left books in the converter:** chapters with `dir="rtl"` and `lang="ar"`, the package's
  `page-progression-direction="rtl"`, and reading order right column first. The plan's M2 lists these.

## Known rough edges to look at

- **Done since this was written (2026-11-11, the owner's Arabic book):** typed Arabic lines come out in reading
  order, right-to-left pages read right column first, and an Arabic scanned book skips OCR once its pages vote it
  Arabic while Tesseract is missing (`BookScript`). See the CHANGELOG's "Arabic books" entry. Still open from that
  book: the package's `page-progression-direction="rtl"` and `dir`/`lang` on chapters; an English run inside an
  Arabic line can come out reversed ("marks book"); a lone Arabic word at a line start can be taken for a list
  bullet; and an OCR page in two columns reads across them (that book's 1883 newspaper clipping).

- A tall first line (a drop cap) used to make a paragraph a heading. `OcrPages` now treats tall *and* long lines as
  body text; check this on real books. Mixed-book samples exaggerate it, so test with whole books.
- Pictures on a scanned page (figures) are dropped when the page becomes text: `OcrPages` leaves the scan out of
  `RawPage.images`. Finding and keeping the figure regions is open.
- The sidecar schema did not change: OCR pages are `kind: "scanned"` with status `ok`, and their blocks' `method` is
  `ocr:<engine>@<confidence>`. If you add fields, version the spec.
- Engine 1.5.0 made every book reconvert once. Bump `SidecarBuilder.ENGINE_VERSION` again only when output changes.

## When done

Update CLAUDE.md, CHANGELOG and the plan's status. Build the debug APK
(`android/app/build/outputs/apk/debug/handover/`, new name), install it on the emulator and on the owner's phone if
connected, and report what was verified and how, and what was not.
