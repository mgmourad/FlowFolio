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
| kotlinx-coroutines (core, android, test) | 1.11.0 | 2026-05-08 | Apache-2.0 | app/test | |
| Room (runtime, ktx, compiler, Gradle plugin) | 2.8.5 | 2026-09-09 | Apache-2.0 | app/build | Schema exported to `core/database/schemas/` |
| DataStore Preferences | 1.2.1 | 2026-03-11 | Apache-2.0 | app | |
| Navigation Compose | 2.10.2 | 2026-09-23 | Apache-2.0 | app | String routes (type-safe routes would need kotlinx-serialization, not approved) |
| androidx.hilt:hilt-navigation-compose | 1.4.0 | 2026-07-01 | Apache-2.0 | app | Brings hilt-lifecycle-viewmodel-compose 1.4.0 transitively |
| Turbine | 1.2.1 | 2025-06-11 | Apache-2.0 | test | |
| pagecurl (vendored source, `:core:pagecurl`) | 1.5.1 | 2024-02-09 | Apache-2.0 | app | Modified copy (ADR-0011); LICENSE + NOTICE in the module; listed in the future in-app licenses screen |
| **PDFium** via pdfium-binaries | chromium/8100 | 2026-10-12 | PDFium BSD-3/Apache-2.0; build scripts MIT; bundled FreeType (FTL), libjpeg-turbo (IJG/BSD), OpenJPEG (BSD-2), lcms (MIT), zlib, ICU (Unicode), HarfBuzz (MIT), abseil (Apache-2.0) | app | `:core:pdf` (ADR-0010). Downloaded at build time, SHA-256 pinned per ABI. License texts ship as assets under `third_party_licenses/pdfium/`; an in-app licenses screen is required before release. 6.3 MB (arm64), 4.3 MB (armv7), 6.8 MB (x86_64) |
| Compose BOM (ui, material3, tooling) | 2026.09.00 | 2026-09-09 | Apache-2.0 | app | |
| androidx.activity:activity-compose | 1.13.0 | 2026-03-11 | Apache-2.0 | app | |
| androidx.lifecycle (runtime/viewmodel-compose) | 2.11.0 | 2026-06-17 | Apache-2.0 | app | |
| androidx.core:core-ktx | 1.19.1 | 2026-09-23 | Apache-2.0 | app | |
| JUnit 4 | 4.13.2 | 2021-02-13 | EPL-1.0 | test | Not distributed; stable, low activity |
| Robolectric | 4.17 | 2026-09-10 | MIT | test | Pinned to simulate API 36 (see Known issues in CLAUDE.md) |
| androidx.test.ext:junit | 1.3.0 | 2025-07-30 | Apache-2.0 | test | |
| androidx.test:runner | 1.7.0 | 2025-07-30 | Apache-2.0 | test | |
| androidx.test.espresso:espresso-core | 3.7.0 | 2025-07-30 | Apache-2.0 | test | Calls an API removed in API 37 under Robolectric |
| androidx.test.uiautomator:uiautomator | 2.4.0 | 2026-07-01 | Apache-2.0 | test | Multi-finger gestures (pinch) in on-device tests; approved with the Phase 1 plan |
| Fraunces (variable font) | google/fonts main, 2026-10-17 | 2018-2024 (Undercase Type) | SIL OFL 1.1 | app | Display font (ADR-0015). `core/designsystem/res/font`; licence text in `assets/third_party_licenses/fonts/`. 775 KB (roman + italic) |
| Instrument Sans (variable font) | google/fonts main, 2026-10-17 | 2022-2024 (Instrument) | SIL OFL 1.1 | app | UI font (ADR-0015). Same locations. 396 KB (roman + italic) |
| dev.chrisbanes.haze:haze | 2.0.1 | 2026-09-29 | Apache-2.0 | app | Backdrop blur for glass surfaces (ADR-0015). Real blur on Android 12+, tint on older. ~250 KB. Owner-approved 2026-10-17 |
| dev.chrisbanes.haze:haze-blur | 2.0.1 | 2026-09-29 | Apache-2.0 | app | Same project as Haze; in 2.0 the blur effect is its own module, so it comes with the approved Haze. ~160 KB |
| com.google.mlkit:translate | 17.0.3 | latest on Google Maven, 2026-10-18 | Google APIs ToS + ML Kit terms (proprietary, not GPL) | app | On-device translation (ADR-0018). 16.4 MB native library on arm64; downloads ~30 MB models per language from Google; sends anonymous usage data to Google. Owner-approved 2026-10-18 |
| com.google.mlkit:language-id | 17.0.6 | latest on Google Maven, 2026-10-18 | Google APIs ToS + ML Kit terms (proprietary, not GPL) | app | Detects the passage's language for the Translate panel. 1.0 MB on arm64. Added with translate; disclosed to the owner 2026-10-19 |
| Spotless Gradle plugin | 8.10.4 | 2026-10-08 | Apache-2.0 | build | |
| ktlint | 1.8.0 | 2025-11-14 | MIT | build | |
| detekt | 1.23.8 | 2025-02-21 | Apache-2.0 | build | **Maintenance risk:** last 1.x release is 20 months old; 2.0 is still alpha. Works with Kotlin 2.4 today; re-evaluate when 2.0 is stable |

## Converter (Python)

| Dependency | Version | Released | License | Where | Risk notes |
|---|---|---|---|---|---|
| Python | 3.13 | n/a | PSF-2.0 | server | `python:3.13-slim` base image |
| FastAPI | 0.143.3 | 2026-10-16 | MIT | server | |
| Starlette (via FastAPI) | 1.8.0 | n/a | BSD-3-Clause | server | |
| Uvicorn [standard] | 0.54.0 | 2026-09-25 | BSD-3-Clause | server | |
| Pydantic | 2.14.1 | 2026-10-11 | MIT | server | |
| pydantic-settings | 2.16.0 | 2026-10-14 | MIT | server | |
| Typer | 0.27.3 | 2026-10-06 | MIT | server | |
| httpx | 0.28.1 | 2024-12-06 | BSD-3-Clause | test | Starlette now warns that httpx is deprecated for its TestClient in favor of `httpx2`; revisit in Phase 3 |
| pytest | 9.1.1 | 2026-06-19 | MIT | test | |
| mypy | 2.4.0 | 2026-10-01 | MIT | build | |
| ruff | 0.17.1 | 2026-10-15 | MIT | build | |
| uv / uv_build | 0.13.1 | 2026-10-13 | MIT OR Apache-2.0 | build | |

## Repository tooling

| Tool | Version | License | Where | Notes |
|---|---|---|---|---|
| pre-commit | 4.7.0 | MIT | build | Installed with `uv tool install` |
| pre-commit-hooks | v6.0.0 | MIT | build | |
| gitleaks | v8.30.1 | MIT | build | Secret scanning |
| conventional-pre-commit | v4.4.0 | MIT | build | commit-msg hook |
| GitHub Actions (checkout, setup-java, upload-artifact, gradle/actions, setup-uv, docker/*) | pinned by SHA in `ci.yml` | MIT / Apache-2.0 | build | |

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
| Readium Kotlin Toolkit | BSD-3-Clause | Phase 2 EPUB |
| pypdfium2 / PDFium | Apache-2.0 / BSD-3 | Phase 3 |
| pdfminer.six, pdfplumber | MIT | Phase 3 |
| Docling (code) / docling models | MIT / check weights | Phase 3; model weights licensed separately |
| PyMuPDF / MuPDF | **AGPL-3.0** | Server only, only via a dedicated ADR (ADR-0003) |
| Marker | **GPL-3.0** (+ weights license) | Server only, only via a dedicated ADR |
