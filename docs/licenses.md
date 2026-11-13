# Dependency License Matrix

Policy: [ADR-0003](adr/0003-licensing-policy.md). FlowFolio itself is MIT.
**Where**: `app` = shipped in the APK · `server` = shipped in the converter image ·
`test` = test classpath only · `build` = build/CI tooling, never distributed.
Release dates were checked on Maven Central, Google Maven, PyPI, or GitHub Releases on 2026-10-16.

## Android

| Dependency | Version | Released | License | Where | Risk notes |
|---|---|---|---|---|---|
| Kotlin stdlib + compiler | 2.4.21 | 2026-10-08 | Apache-2.0 | app/build | |
| Android Gradle Plugin | 9.4.1 | 2026-09-18 | Apache-2.0 | build | |
| Gradle (wrapper) | 9.8.1 | 2026-10-07 | Apache-2.0 | build | Distribution SHA-256 pinned in wrapper properties |
| KSP | 2.3.12 | 2026-09-09 | Apache-2.0 | build | |
| Hilt / Dagger | 2.60.1 | 2026-07-06 | Apache-2.0 | app | |
| **ONNX Runtime for Android** (`com.microsoft.onnxruntime:onnxruntime-android` AAR) | 1.28.0 | 2026-07-27 | MIT | app | `:core:voice`, OCR (ADR-0054, ADR-0059). Fetched at build time by `OnnxRuntimeConventionPlugin`, SHA-256 pinned (`f351a063...`); only `libonnxruntime.so` (arm64-v8a, armeabi-v7a, x86_64) and the C headers are used, through `OrtBridge`. Owner-approved 2026-11-13 |
| ONNX Runtime Java | 1.28.0 | 2026-07-27 | MIT | tests (:core:conversion) | JVM only, to measure OCR against real scans; the same version the app ships (ADR-0059) |
| PaddleOCR PP-OCRv6 det/rec small, PP-OCRv5 Arabic rec | RapidOCR 3.10.0 export | 2026-10-08 | Apache-2.0 | downloaded on request (Settings, Scanned books) | Mirrored on FlowFolio's releases (`ocr-models-v1`), SHA-256 pinned (`OcrPack`) |
| **Tesseract** | 5.5.4 | 2026-10-28 | Apache-2.0 | app | Built from source by `TesseractConventionPlugin` (`:core:ocr`), SHA-256 pinned per ABI (ADR-0054) |
| **Leptonica** | 1.87.0 | 2025-12-24 | BSD-2-Clause | app | Built from source with no image codecs (raw pixels in), SHA-256 pinned (ADR-0054) |
| **cpu_features** (`google/cpu_features`, `ndk_compat`) | 0.9.0 | 2023 | Apache-2.0 | app (statically linked) | Transitive build dependency of Tesseract 5.5 on Android (NEON detection); linked into `libtesseract.so`, no separate `.so` |
| tessdata_best `ara.traineddata` | main | 2026-11-11 | Apache-2.0 | downloaded on request (Settings, Scanned books) | 12.6 MB, mirrored on FlowFolio's releases (`ocr-models-v1`), SHA-256 pinned (`OcrPack`) |
| tess4j | 5.21.0 | 2026-11-07 | Apache-2.0 | test (`:tools:convert`) | JNA wrapper over libtesseract for the desktop JVM; CI installs `libtesseract` (ADR-0054) |
| Chaquopy (Gradle plugin + runtime) | 17.2.0 | 2026-09-30 | MIT | app/build | Embeds CPython 3.12 (PSF-2.0) for arm64-v8a and x86_64 only; pip-installs at build time, which F-Droid still has to be checked against (ADR-0047) |
| CPython (through Chaquopy) | 3.12 | - | PSF-2.0 | app | Shipped as Chaquopy's prebuilt runtime |
| yt-dlp | 2026.10.26 | 2026-10-26 | Unlicense | app | Source only, via pip. Its *PyInstaller release binaries* are GPLv3+ and are NOT used. No ffmpeg: only an address is extracted, and MediaPlayer plays it (ADR-0047) |
| kotlinx-coroutines (core, android, test) | 1.11.0 | 2026-05-08 | Apache-2.0 | app/test | |
| Room (runtime, ktx, compiler, Gradle plugin) | 2.8.5 | 2026-09-09 | Apache-2.0 | app/build | Schema exported to `core/database/schemas/` |
| DataStore Preferences | 1.2.1 | 2026-03-11 | Apache-2.0 | app | |
| Navigation Compose | 2.10.2 | 2026-09-23 | Apache-2.0 | app | String routes (type-safe routes would need kotlinx-serialization, not approved) |
| androidx.hilt:hilt-navigation-compose | 1.4.0 | 2026-07-01 | Apache-2.0 | app | Brings hilt-lifecycle-viewmodel-compose 1.4.0 transitively |
| Turbine | 1.2.1 | 2025-06-11 | Apache-2.0 | test | |
| pagecurl (vendored source, `:core:pagecurl`) | 1.5.1 | 2024-02-09 | Apache-2.0 | app | Modified copy (ADR-0011); LICENSE + NOTICE in the module; listed in the in-app licenses screen |
| **PDFium** via pdfium-binaries | chromium/8100 | 2026-10-12 | PDFium BSD-3/Apache-2.0; build scripts MIT; bundled FreeType (FTL), libjpeg-turbo (IJG/BSD), OpenJPEG (BSD-2), lcms (MIT), zlib, ICU (Unicode), HarfBuzz (MIT), abseil (Apache-2.0) | app | `:core:pdf` (ADR-0010). Downloaded at build time, SHA-256 pinned per ABI. License texts ship as assets under `third_party_licenses/pdfium/`, shown in the in-app licenses screen. 6.3 MB (arm64), 4.3 MB (armv7), 6.8 MB (x86_64) |
| Compose BOM (ui, material3, tooling) | 2026.09.00 | 2026-09-09 | Apache-2.0 | app | |
| androidx.activity:activity-compose | 1.13.0 | 2026-03-11 | Apache-2.0 | app | |
| androidx.lifecycle (runtime/viewmodel-compose) | 2.11.0 | 2026-06-17 | Apache-2.0 | app | |
| androidx.core:core-ktx | 1.19.1 | 2026-09-23 | Apache-2.0 | app | |
| JUnit 4 | 4.13.2 | 2021-02-13 | EPL-1.0 | test | Not distributed; stable, low activity |
| Robolectric | 4.17 | 2026-09-10 | MIT | test | Pinned to simulate API 36 (see Known issues in CLAUDE.md) |
| Roborazzi (core, compose, junit-rule, Gradle plugin) | 1.76.0 | 2026-09-29 | Apache-2.0 | test | Screenshot tests, ADR-0024; owner-approved 2026-10-20 |
| androidx.test.ext:junit | 1.3.0 | 2025-07-30 | Apache-2.0 | test | |
| androidx.test:runner | 1.7.0 | 2025-07-30 | Apache-2.0 | test | |
| androidx.test.espresso:espresso-core | 3.7.0 | 2025-07-30 | Apache-2.0 | test | Calls an API removed in API 37 under Robolectric |
| androidx.test.uiautomator:uiautomator | 2.4.0 | 2026-07-01 | Apache-2.0 | test | Multi-finger gestures (pinch) in on-device tests; approved with the Phase 1 plan |
| androidx.profileinstaller:profileinstaller | 1.4.1 | 2024-10-02 | Apache-2.0 | app | Installs the baseline profile on first launch (~40 KB). Owner-approved 2026-10-19 |
| androidx.benchmark:benchmark-macro-junit4 | 1.5.0 | 2026-09-09 | Apache-2.0 | test | `:benchmark` only: startup and page-turn macrobenchmarks, profile recording. Owner-approved 2026-10-19 |
| androidx.baselineprofile (Gradle plugin) | 1.5.0 | 2026-09-09 | Apache-2.0 | build | Wires the committed profile into release builds; creates the benchmark build types. Owner-approved 2026-10-19 |
| Fraunces (variable font) | google/fonts main, 2026-10-17 | 2018-2024 (Undercase Type) | SIL OFL 1.1 | app | Display font (ADR-0015). `core/designsystem/res/font`; licence text in `assets/third_party_licenses/fonts/`. 775 KB (roman + italic) |
| Instrument Sans (variable font) | google/fonts main, 2026-10-17 | 2022-2024 (Instrument) | SIL OFL 1.1 | app | UI font (ADR-0015). Same locations. 396 KB (roman + italic) |
| Literata (variable) | google/fonts main, 2026-10-21 (upstream 3.103, 2023-05-19) | TypeTogether for Google | SIL OFL 1.1 | app | Reading font, the default (ADR-0027). `core/designsystem/res/font/reading_*`; licence texts in `assets/third_party_licenses/fonts/`. 1.8 MB |
| Charter | Butterick "Charter 210112" (Bitstream originals 1989-1992) | Bitstream Inc. | Bitstream Charter licence (permissive, SPDX `Bitstream-Charter`) | app | Reading font (ADR-0027). Notice kept; trademark acknowledged. 145 KB |
| Atkinson Hyperlegible Next (variable) | google/fonts main, 2026-10-21 | Braille Institute, 2020-2024 | SIL OFL 1.1 (reserved names Atkinson, Hyperlegible) | app | Reading font (ADR-0027). 0.2 MB |
| EB Garamond (variable) | google/fonts main, 2026-10-21 | Georg Duffner, Octavio Pardo | SIL OFL 1.1 | app | Reading font (ADR-0027). 1.6 MB |
| Source Sans 3 (variable) | google/fonts main, 2026-10-21 (upstream 3.052R, 2023-04-04) | Adobe | SIL OFL 1.1 | app | Reading font (ADR-0027). 1.0 MB |
| Lexend (variable) | google/fonts main, 2026-10-21 | Lexend project | SIL OFL 1.1 | app | Reading font (ADR-0027). No italic exists. 0.2 MB |
| Andika | google/fonts main, 2026-10-21 | SIL International | SIL OFL 1.1 | app | Reading font (ADR-0027). Four static files, 2.7 MB |
| JetBrains Mono (variable, roman) 2.211 | google/fonts main, 2026-10-28 (last changed there 2026-03-03) | The JetBrains Mono Project Authors, 2020 | SIL OFL 1.1 (trademark of JetBrains s.r.o. acknowledged) | app | The reader's code face (`CodeFace`; owner's choice 2026-10-28, since a phone's font style can make the system monospace proportional). `core/designsystem/res/font/code_jetbrains_mono.ttf`; licence text `assets/third_party_licenses/fonts/JetBrainsMono-OFL.txt`. 187 KB; no italic bundled. Alternatives: Source Code Pro, Fira Mono (both OFL), the system's monospace |
| dev.chrisbanes.haze:haze | 2.0.1 | 2026-09-29 | Apache-2.0 | app | Backdrop blur for glass surfaces (ADR-0015). Real blur on Android 12+, tint on older. ~250 KB. Owner-approved 2026-10-17 |
| dev.chrisbanes.haze:haze-blur | 2.0.1 | 2026-09-29 | Apache-2.0 | app | Same project as Haze; in 2.0 the blur effect is its own module, so it comes with the approved Haze. ~160 KB |
| com.google.mlkit:translate | 17.0.3 | latest on Google Maven, 2026-10-18 | Google APIs ToS + ML Kit terms (proprietary, not GPL) | app | On-device translation (ADR-0018). 16.4 MB native library on arm64; downloads ~30 MB models per language from Google; sends anonymous usage data to Google. Owner-approved 2026-10-18 |
| com.google.mlkit:language-id | 17.0.6 | latest on Google Maven, 2026-10-18 | Google APIs ToS + ML Kit terms (proprietary, not GPL) | app | Detects the passage's language for the Translate panel. 1.0 MB on arm64. Added with translate; disclosed to the owner 2026-10-19 |
| Spotless Gradle plugin | 8.10.4 | 2026-10-08 | Apache-2.0 | build | |
| ktlint | 1.8.0 | 2025-11-14 | MIT | build | |
| detekt | 1.23.8 | 2025-02-21 | Apache-2.0 | build | **Maintenance risk:** last 1.x release is 20 months old; 2.0 is still alpha. Works with Kotlin 2.4 today; re-evaluate when 2.0 is stable |
| kotlinx-serialization-json + Kotlin serialization plugin | 1.12.0 (plugin with Kotlin 2.4.21) | 2026-10-19 | Apache-2.0 | app | `:core:conversion`: the sidecar `flowfolio.json` and the corpus metrics. Owner-approved 2026-10-27 |
| JNA (net.java.dev.jna:jna) | 5.19.1 | 2026-06-12 | Apache-2.0 (dual-licensed with LGPL-2.1; used under Apache-2.0) | dev tool only | `android/tools/convert` (ADR-0044): calls the desktop PDFium build for the JVM tests and the corpus harness. Never a dependency of the app. Owner-approved 2026-10-27 |
| PDFium desktop builds (pdfium-binaries win-x64, linux-x64) | chromium/8100 | 2026-10-12 | as PDFium above | dev tool only | Fetched by `FetchHostPdfiumTask`, SHA-256 pinned; never shipped |
| EPUBCheck (org.w3c:epubcheck) | 5.4.0 | 2026-09-15 | BSD-3-Clause; its dependencies (Saxon-HE MPL-2.0, Jing BSD-3-Clause, Guava, Apache Commons, TwelveMonkeys, Xerces Apache-2.0, ICU4J ICU) are test-only too | test | Validates every converted EPUB in `ConversionTest`/`CorpusTest`. Not distributed. Owner-approved 2026-10-27 |
| English Wiktionary lexicon data (`WiktionaryStore`) | 2026-10 snapshot + REST cache | 2026-10 | CC BY-SA 4.0 | app data | Offline English definitions for the reader's Define panel and Dictionary tab (ADR-0046). Stored as TSV data in `filesDir/dictionary/en-wiktionary.tsv` (ShareAlike applies to the lexicon data itself, not FlowFolio's MIT code). Attributed in the Define panel and here. Owner-approved 2026-10-28 |
| Princeton WordNet 3.1 database (`WordNetStore`) | 3.1 | 2011 | WordNet License (permissive, BSD-style) | app data | The full English WordNet (132k entries), built by `tools/dictionary/build_wordnet.py` into a SQLite database and bundled gzipped (`wordnet.db.gz`, ~8 MB). Definitions, synonyms and hypernyms for the Define panel. ShareAlike does not apply; the WordNet License only requires the notice be kept. Attributed in the Define panel and here. Owner-approved 2026-11-07 |
| English Wikipedia summaries (`WikipediaStore`) | REST summary API | online | CC BY-SA 3.0 (article text) | network | The opening `extract` of a Wikipedia article, fetched online for the Define panel so specialized terms ("kubernetes", "vram") get a first sentence. Never cached; ShareAlike applies to the returned text. Attributed in the Define panel and here. Owner-approved 2026-11-07 |
| Page-turn and riffle recordings (4 `.wav`) | - | 2026-10-21 | MIT (this project) | app | The product owner's own recordings, made for FlowFolio (ADR-0025): a flip forward and back, a riffle forward and back. `feature/reader/riffle/res/raw/`. No third-party terms |
| Ambient recordings (8 `.mp3`) | - | 2026-11-02 | Royalty-free | app | The eight Reading Atmosphere beds (rain, fireplace, ocean, forest, stream, night garden, cafe, brown noise), royalty-free stock audio licensed for use in the app and transcoded to mono 64 kbps. `core/data/res/raw/`, 6.4 MB. No attribution required |

## Converter (Python): retired

The Phase 0 FastAPI server, its Docker image and their dependencies were removed in Phase 3 (ADR-0043). Python remains
only for the standard-library scripts in `tools/` (corpus fetch, test PDF and EPUB generators, sounds, icon):

| Dependency | Version | Released | License | Where | Risk notes |
|---|---|---|---|---|---|
| Python | 3.13 | n/a | PSF-2.0 | dev tool | Standard library only |
| ruff | 0.17.1 | 2026-10-15 | MIT | build | Run by the pre-commit hooks through `uvx` |
| uv | 0.13.1 | 2026-10-13 | MIT OR Apache-2.0 | build | Provides `uvx` for ruff and pre-commit |

## Test corpus (never committed)

Downloaded by `tools/corpus/fetch_corpus.py` into `samples/corpus/cache/` (gitignored) and checked by SHA-256
(`samples/corpus/manifest.yaml`). Nothing derived from them is committed except metrics (`samples/golden/baselines/`)
and hand labels (`samples/golden/labels/`, a few words per page).

| Source | Licence | Notes |
|---|---|---|
| OpenStax *Physics*, *Statistics*, *Introduction to Intellectual Property* | CC BY 4.0 | |
| OpenStax *University Physics Vol. 1*, *Calculus Vol. 1*, *Principles of Economics 3e*, *Psychology 2e*, *U.S. History*, *Introductory Business Statistics 2e* | CC BY-NC-SA 4.0 | Local testing only (CLAUDE.md known issue 10) |
| Project Gutenberg EPUBs (7 titles) | Public domain in the US; Project Gutenberg licence for the trademark | Reader test books |
| IDPF EPUB 3 samples | CC BY-SA 3.0 | Phase 2 pagination corpus |

## Repository tooling

| Tool | Version | License | Where | Notes |
|---|---|---|---|---|
| pre-commit | 4.7.0 | MIT | build | Installed with `uv tool install` |
| pre-commit-hooks | v6.0.0 | MIT | build | |
| gitleaks | v8.30.1 | MIT | build | Secret scanning |
| conventional-pre-commit | v4.4.0 | MIT | build | commit-msg hook |
| GitHub Actions (checkout, setup-java, upload-artifact, gradle/actions) | pinned by SHA in `ci.yml` | MIT / Apache-2.0 | build | |

## Evaluated in the Phase 1 spike (removed with the spike module)

| Dependency | Version | Released | License | Notes |
|---|---|---|---|---|
| androidx.pdf:pdf-document-service | 1.0.0-beta01 | 2026-08-26 | Apache-2.0 | Compared and rejected |
| io.github.oleksandrbalan:pagecurl | 1.5.1 | 2024-02-09 | Apache-2.0 | Vendored into `:core:pagecurl` (ADR-0011) |
| com.microsoft.onnxruntime:onnxruntime-android | 1.31.0 | 2026-10-09 | MIT | 32.6 MB per ABI; feasibility probe |
| all-MiniLM-L6-v2 (ONNX model weights) | HF main | n/a | Apache-2.0 | Local only (`samples/models/`, gitignored) |

## Under evaluation (not added)

| Candidate | License | Notes |
|---|---|---|
| oleksandrbalan/pagecurl | Apache-2.0 | Phase 1; no release since early 2024; plan to vendor or fork |
| Readium Kotlin Toolkit | BSD-3-Clause | Phase 2 EPUB; our own parser chosen instead (ADR-0020) |
| pypdfium2, pdfminer.six, pdfplumber, Docling | Apache-2.0 / MIT | Python converter candidates; not needed since conversion runs on the phone in Kotlin (ADR-0009, ADR-0044) |
| Docling models / layout models | check weights | Phase 4-5, if a stage needs a model (ONNX Runtime, ADR to come) |
| PyMuPDF / MuPDF | **AGPL-3.0** | Server only, only via a dedicated ADR (ADR-0003) |
| Marker | **GPL-3.0** (+ weights license) | Server only, only via a dedicated ADR |
