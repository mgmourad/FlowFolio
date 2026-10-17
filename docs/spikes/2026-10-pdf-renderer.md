# Spike: PDF renderer, extraction, page curl, on-device ML

- **Date:** 2026-10-16
- **Phase:** 1 (stop point before building)
- **Code:** was `android/spikes/pdf-renderer/` (commits 23aad39..f685cbb); removed in Phase 1 milestone 1 after porting the
  PDFium bridge to `:core:pdf`. Recover with `git show 23aad39 -- android/spikes`.
- **Reference device:** Samsung SM-F976U (reports itself as the product owner's Galaxy Z Fold), Android 17 / API 37,
  Snapdragon SM8850, ~11 GB RAM. Cover screen 1080×2520; inner screen 2256×2504, up to 120 Hz.
- **Test book:** OpenStax *University Physics Vol. 1*, 959 pages, 81.5 MB (CC BY-NC-SA 4.0; local only, not committed),
  SHA-256 `93652b69…4aba8`. Plus a generated 5-page PDF with page labels and an outline (`testdata/make_labels_pdf.py`).
- **Release build**, debug-signed, not minified. Emulator runs were used only to validate the harness.

## Candidates

| | Framework `PdfRenderer` | Jetpack `androidx.pdf` 1.0.0-beta01 | PDFium via our JNI bridge |
|---|---|---|---|
| What it is | Android's built-in renderer (itself PDFium inside) | The framework renderer run in a sandboxed process, plus viewer APIs | `pdfium-binaries` chromium/8100 + ~300 lines of C++ |
| License | Platform | Apache-2.0 | BSD-3/Apache-2.0 (PDFium), MIT (build scripts); bundled third-party: FreeType (FTL), libjpeg-turbo, OpenJPEG, lcms, zlib, ICU, HarfBuzz — all permissive |
| APK cost (arm64) | 0 | small (Kotlin only) | 6.3 MB `libpdfium.so` + 0.2 MB bridge |

**PdfiumAndroidKt was dropped before measuring:** same PDFium engine, but no page labels, no glyph/font/path
access, and it bundles its own `libpdfium.so`, which would clash with ours.

## Results on the Fold

### Rendering (fit-to-width, full page, ARGB_8888; ms)
| Engine | Screen | Open p50 | Sequential p50 / p95 | Random p50 / p95 |
|---|---|---|---|---|
| PDFium | cover (1080 px) | 12.9 | **9.5 / 17.3** | **11.6 / 19.6** |
| Framework | cover | 15.6 | 18.3 / 35.8 | 19.7 / 34.9 |
| androidx.pdf | cover | 80.4 | 12.1 / 35.6 | 10.4 / 16.2 |
| PDFium | inner (2256 px) | 12.7 | **17.9 / 24.7** | **18.6 / 26.5** |
| Framework | inner | 11.1 | 18.1 / 32.4 | 19.1 / 34.2 |
| androidx.pdf | inner | 64.8 | 28.9 / 56.2 | 35.3 / 68.6 |

All engines render a page well inside the time a page turn takes (~350 ms), so with prefetch the curl never waits.
PDFium has the tightest tail (p95).

### Image quality
Pixel diff against PDFium on 6 pages (cover, text, figures, equations): **≤ 0.053% of pixels differ by more than
32/255** for both other engines. They are effectively identical because the framework renderer is PDFium.
3× zoom crops are sharp for all. Quality does not separate the candidates.

### Features
| | Framework | androidx.pdf | PDFium (ours) |
|---|---|---|---|
| Outline / table of contents | — | — | ✅ 198 entries (book), 2/2 (test PDF) |
| Page labels (printed numbers) | — | — | ✅ `i, ii, 1, 2, 3` on test PDF |
| Text, links | API 35+ | ✅ | ✅ |
| Glyph positions, font names, sizes, weights, colors | — | — | ✅ |
| Vector paths, images, form XObjects | — | partial (page objects) | ✅ |
| Process isolation (sandbox) | — | ✅ | — (see risks) |

### Stage B extraction probe (PDFium, pages 101–150)
- **4.5–6.4 ms per page** (p50) for raw glyphs + grouping into runs, about **5–6 s for the whole 959-page book**.
- 104,932 characters → 2,054 glyph runs, 1,846 lines; 8 fonts (Mulish family, IBM Plex Sans, DejaVu Sans).
- 16,258 text objects, 6,593 path objects with **192,045 path segments**, 42 images.
- **Finding: math is drawn as vector paths, not text.** Inline symbols (Δx, x_f, x_0) and display equations on page 111
  are visible in the render but absent from the text layer. See implications below.

### On-device ML (ONNX Runtime 1.31.0, all-MiniLM-L6-v2, sequence length 128)
| Model | Provider | Batch 1 p50 | Batch 8 p50 | Throughput |
|---|---|---|---|---|
| 8-bit (23 MB) | CPU | 6.5–9.8 ms | 42.8–105 ms | 76–187 seq/s |
| 8-bit | XNNPACK | 6.1–16.4 ms | 42.7–106 ms | 75–187 seq/s |
| fp32 (90 MB) | CPU | 13.9–30.3 ms | 167–219 ms | 37–48 seq/s |
| fp32 | XNNPACK | 112–146 ms | 865–1080 ms | 7–9 seq/s (avoid) |

Ranges span two runs minutes apart (CPU frequency/thermal state). Session load 166–307 ms. Memory +13–43 MB.
At ~100 sequences/s, embedding every paragraph of a 1,000-page textbook (~10,000 paragraphs) takes ~2 minutes.
**ONNX Runtime adds 32.6 MB per ABI** (arm64) to the app, five times PDFium.

### Page curl (oleksandrbalan/pagecurl 1.5.1, 50 automated turns, `dumpsys gfxinfo`)
Display ran at 120 Hz (~117 frames/s observed), so the per-frame budget is 8.3 ms.

| Screen | Mode | Engine | Frames | Missed deadline | p50 / p95 / p99 frame time |
|---|---|---|---|---|---|
| Inner | single page | PDFium | 3,498 | **2.09%** | 9 / 13 / 22 ms |
| Inner | single page | Framework | 3,513 | 2.16% | 9 / 13 / 22 ms |
| Inner | two-page spread | PDFium | 3,505 | **1.83%** | 8 / 13 / 16 ms |
| Inner | two-page spread | Framework | 3,513 | 1.62% | 9 / 12 / 18 ms |
| Cover | single page | PDFium | 3,409 | **1.53%** | 7 / 11 / 16 ms |
| Cover | single page | Framework | 3,406 | 1.50% | 7 / 11 / 13 ms |

Turning was verified on the cover run: after 50 swipes from physical page 101 the screen showed physical page 151
(printed "139"). A first folded attempt was discarded because the phone was unfolded mid-run; the run script now
aborts when the fold state changes.

"Frame time" in gfxinfo spans input to display and overlaps across frames, so it exceeds 8.3 ms while frames
still arrive on time; **missed deadline** is the meaningful number: ~98% of frames on time at 120 Hz.

**Memory during curl was far too high:** total PSS 415–436 MB on the cover screen, 597–727 MB single page on the
inner screen, 697–951 MB spread
(native heap 181–432 MB, graphics/GPU textures ~370–390 MB), against a 250 MB target. Causes in the spike code:
the 128 MB cache budget does not cover bitmaps still referenced by Compose, every bitmap is also uploaded as a
GPU texture, and nothing is pooled or recycled.

## Conclusions

1. **Renderer: PDFium through our own JNI bridge (ADR-0010, proposed).** Same image quality, the best tail latency,
   and the only option with outline, page labels, and the glyph/font/path data the on-device converter needs. One engine
   for reading and converting means the two can never disagree about a page.
2. **Page curl: pagecurl is fast enough on the Fold at 120 Hz on both screens (ADR-0011, proposed),** for single page and spread.
   A real book spread (only the right-hand page turns, revealing the next spread) needs changes to the library, so we
   **vendor it** into `:core:pagecurl` with attribution and own it.
3. **Memory is the main Phase 1 engineering risk,** not speed. The reader needs a strict budget covering bitmaps
   *and* textures: render at exactly the displayed size, keep only current/next/previous units decoded, reuse bitmaps
   from a pool, release Compose references on eviction, and measure against 250 MB on the inner screen.
4. **On-device conversion is feasible** on this device: raw extraction for a 959-page book takes seconds; small-model
   inference takes minutes. Budget phones will be slower; that is a Phase 3 measurement.

## Implications beyond Phase 1

- **Equations as vector paths (Phase 5):** the brief's equation parser assumes math-font glyphs. For books like this,
  Stage E must first recognize symbols from their outlines (shape matching against reference fonts or a small
  classifier), then parse layout. This needs its own spike early in Phase 5, and the corpus must include both kinds
  of PDF.
- **Printed page numbers without page labels:** this book has no `/PageLabels` (PDF page 111 prints "99").
  Go-to-page must fall back to reading the printed number from running headers/footers (Stage C detects them).
- **ONNX Runtime size:** 32.6 MB/ABI. Phase 3 should evaluate a reduced-operator build or on-demand delivery.
- **Security:** PDFium parses untrusted files in our process; androidx.pdf sandboxes for this reason. Mitigations to
  decide in Phase 1 build: keep PDFium updated (weekly pdfium-binaries releases), fuzz the bridge with malformed PDFs,
  and run *conversion* in an isolated process. Rendering stays in-process for latency.

## Not yet measured

- Malformed/encrypted/rotated PDFs (robustness set), non-textbook PDFs: Phase 1 build adds a robustness test set.
- Fold/unfold continuity and the memory-disciplined cache: Phase 1 build exit criteria.
