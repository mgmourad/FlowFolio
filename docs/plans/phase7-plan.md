# Phase 7 plan: scanned books (the final phase)

Approved by the owner 2026-11-10, with Arabic as a beta requirement. Ships as **0.7.0-beta.1** (ADR-0050: Phase 7 is beta).

**Goal:** books that are pictures of pages become readable.
**Done when:** a scanned book reflows in Dynamic Flow, **in English and in Arabic**, and a bad OCR page falls back to
its picture rather than inventing text.

**Arabic is a beta requirement** (owner, 2026-11-10), alongside the Latin-script languages.

## Where we start

- The converter already detects scanned pages (`PageKind.SCANNED` in `ir/Document.kt`). Today they fall back to a
  page picture (`fallback:scanned` in `Converter.kt`), and the sidecar counts them (`scannedPages`).
- **ONNX Runtime is already in the APK.** sherpa-onnx brought `libonnxruntime.so` (21 MB, arm64) for Narrator Mode
  (ADR-0051). The old plan's worry, "32.6 MB per ABI", is already paid; an OCR model costs only its own files.
- Model files are downloaded on first use, not bundled (ADR-0009), the way the voice packs are.
- **Right to left, the reader is mostly ready; the converter is not.**
  - The reflow engine lays text out with Android's `StaticLayout`, which already joins Arabic letters and handles
    mixed Arabic and English lines (bidi).
  - Selection already allows for right-to-left lines (`PageGlyphs`), and the EPUB reader already reads
    `page-progression-direction="rtl"`.
  - The converter writes `lang` but never `dir="rtl"` or the right-to-left page direction, and its reading order
    assumes columns run left to right.
- The converter is a plain JVM module (ADR-0044), and CI's corpus gate runs it on Linux. Whatever reads text must run
  there too, or scanned books can't be gated.

## M1: OCR spike and ADR-0054 (decide before building)

Three candidates. A and C are measured on the same ~20 English and ~20 Arabic scanned pages; B is listed for the
record:

| | Licence | On the phone | In CI (JVM) | Notes |
|---|---|---|---|---|
| **A. PaddleOCR PP-OCR models on ONNX Runtime** (recommended) | Apache-2.0 weights, MIT runtime | Runtime already shipped; models ~15 MB, downloaded | ONNX Runtime's JVM build, desktop tooling only (like JNA) | Same models on both, so CI gates what the phone runs |
| B. ML Kit Text Recognition v2 | Proprietary | Easy; ML Kit already present for Translate | **No**, Android only | **Out: no Arabic.** CI couldn't gate it either |
| C. Tesseract | Apache-2.0 | Extra native library, slower on phones | Yes | Has Arabic; weaker on photographed and skewed pages |

- **What the spike measures:**
  - accuracy (character error rate against hand-typed text);
  - seconds per page on the Fold, in `:convert`;
  - peak memory;
  - APK and download size.
- **Proposed bars:**
  - at most 3 % character errors on clean English scans, and at most 6 % on clean Arabic scans (joined letters and
    dots make Arabic harder for every engine); 
  - at most 2 s a page, so a 300-page scan converts in about 10 minutes with chapters readable as they finish.
- **Arabic checks in the spike:**
  - Does the engine return Arabic in reading order, or in visual order that must be reversed?
  - Are Arabic and Western digits both read correctly?
  - How does PDFium extract a *typed* Arabic PDF? This needs no OCR, but typed PDFs often store Arabic as
    presentation forms or in visual order. If they do, today's converter garbles them, and the fix belongs in this
    phase too.
- **The engineering question for A:** how to call the runtime that is already bundled. Either onnxruntime-android's
  Java API, which must be the *same* runtime version as sherpa's (two copies of `libonnxruntime.so` cannot coexist),
  or a small JNI over its C API, like our PDFium bridge. The spike settles which. **Any new dependency is brought to
  you first**, with licence, last release date and alternatives.
- **Output:** ADR-0054 (OCR engine) for your acceptance (ADR-0053 went to background narration, 2026-11-10).

## M2: OCR in the pipeline

- **An `OcrEngine` interface in `:core:conversion`** (plain JVM). The phone and desktop implementations sit outside it,
  as `PdfSource` does today.
- **A scanned page goes through these steps:**
  - Render it at reading resolution.
  - Straighten it.
  - Find the lines, then read them.
  - Pass the lines on, with their boxes and confidence, to the stages that already exist (reading order, blocks,
    headings).
  - Since a scan has no fonts, heading levels come from line heights.
- **Mixed books work page by page:** typed pages stay as they are, and scanned pages get text.
- **The bonus beyond reflow: scanned PDFs gain a text layer in the PDF view too.** Selection, highlights, in-book
  search, Define and Narrator Mode then work on scans. Today they do nothing there.
- **The model pack:**
  - Offered the first time a scanned book is opened, with its size, like the voice packs.
  - Kept in Settings → Dynamic Flow ("Scanned books").
  - Without the pack, scanned pages stay pictures, exactly as today.
- **Progress:** the notification gets a "Reading scanned pages" stage, and progressive delivery keeps chapters
  readable while the rest converts.
- **Engine version bump:** books converted earlier reconvert on their next open (`FlowFiles.outdated`).
- **Languages:** two packs for the beta, Latin-script (English, French, Spanish, German and the rest) and Arabic.
  The book's language, from its details or chosen by the reader, picks the pack; a book can use both. Other scripts
  are later packs.
- **Right-to-left books in the converter:**
  - Reading order runs right to left: the right-hand column first, and indents, list markers and alignment
    mirrored.
  - Chapters carry `dir="rtl"` and `lang="ar"`, and the package sets `page-progression-direction="rtl"`. The book
    then opens turning right to left, using the reader's existing right-to-left mode.
  - Mixed lines (an English term or a number inside Arabic) keep the correct order.
  - Headings, the table of contents and page labels work in Arabic.
- **Reading fonts:** the seven bundled reading fonts have no Arabic, so Arabic text uses the phone's Arabic font
  (font fallback). Bundling an Arabic reading font (for example Noto Naskh Arabic, OFL) is a separate decision for
  you.

## M3: The quality gate for scanned books

- **A scanned corpus**, public-domain scans only, English and Arabic, kept out of the repository; the manifest and hashes go in, as for
  today's corpus. It includes clean scans, a skewed or photographed book, two columns, footnotes, a mixed
  typed-and-scanned PDF, an Arabic book with English terms and numbers, and a typed Arabic PDF.
- **Ground truth:** about 30 hand-typed English pages and about 20 Arabic pages in `samples/golden/`; the Arabic
  pages need a reader of Arabic to check them (you, ideally), and character-error baselines that CI enforces.
- **A confidence threshold**, per line and per page. Below it the page stays a picture
  (`fallback:ocr-low-confidence`), so nothing is invented. The conversion health badge counts these pages.
- **The done-when, as a test:** a scanned English book and a scanned Arabic book reflow (the Arabic one turning
  right to left), and planted bad pages (handwriting, a photo, heavy blur) fall
  back to pictures.

## M4: Polish slot and beta readiness

- **Your tweak batch for the phase.** Every phase ends with one.
- **Open issues worth closing before a beta:**
  - known issue 44, a full-page cover image overflowing the column;
  - known issue 45, taps not turning pages in the reflowed view, to be diagnosed;
  - What's new heading its notes with the newest *published* version instead of the installed one (seen today
    before canary.3 was published).
- **Release housekeeping the plan carries:**
  - a second off-machine copy of the release key in GitHub Actions secrets (ADR-0049);
  - the unsent launch posts;
  - a yes or no on a self-hosted F-Droid repository.
- **Sign-off, then 0.7.0-beta.1.**

## Decisions I need from you

1. **Direction:** spike option A first, with B and C as the fallbacks it is measured against?
2. **Arabic:** is 6 % character errors on clean Arabic scans an acceptable bar? Should typed Arabic PDFs be fixed
   in this phase if the spike finds them garbled? And will you check the Arabic ground-truth pages?
3. **Speed:** is about 2 s a page (a 300-page scan in about 10 minutes) acceptable?
4. **M4:** anything to add to, or drop from, beta readiness? (Including whether to bundle an Arabic reading font.)
