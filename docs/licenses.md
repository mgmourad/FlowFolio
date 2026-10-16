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
| Compose BOM (ui, material3, tooling) | 2026.09.00 | 2026-09-09 | Apache-2.0 | app | |
| androidx.activity:activity-compose | 1.13.0 | 2026-03-11 | Apache-2.0 | app | |
| androidx.lifecycle (runtime/viewmodel-compose) | 2.11.0 | 2026-06-17 | Apache-2.0 | app | |
| androidx.core:core-ktx | 1.19.1 | 2026-09-23 | Apache-2.0 | app | |
| JUnit 4 | 4.13.2 | 2021-02-13 | EPL-1.0 | test | Not distributed; stable, low activity |
| Robolectric | 4.17 | 2026-09-10 | MIT | test | Pinned to simulate API 36 (see Known issues in CLAUDE.md) |
| androidx.test.ext:junit | 1.3.0 | 2025-07-30 | Apache-2.0 | test | |
| androidx.test:runner | 1.7.0 | 2025-07-30 | Apache-2.0 | test | |
| androidx.test.espresso:espresso-core | 3.7.0 | 2025-07-30 | Apache-2.0 | test | Calls an API removed in API 37 under Robolectric |
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
