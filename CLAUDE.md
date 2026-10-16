# CLAUDE.md: FlowFolio project memory

Read this first. Keep it current: update at the end of every phase.

## Current phase

**Phase 0: Foundation. Complete pending product-owner approval** (2026-10-16).
Exit criteria met: first green CI run on `main` (run 38979039796, all jobs), both projects run locally.
Next: Phase 1 (PDF reader with page curl), which starts with a plan and a PDF-rendering spike.

## Working agreement (summary)

One phase at a time; plan before code; never claim something works without running it
(say "tested", "implemented but untested", or "stubbed"); ask before adding any dependency
(state license, last release date, alternatives); every significant decision gets an ADR;
Conventional Commits; keep `CHANGELOG.md`; no secrets in the repo; propose a spike when unsure.

## Architecture summary

- Monorepo (ADR-0002): `android/` app, `converter/` Python service. The only shared artifact is
  the output contract `docs/conversion-spec.md` (sidecar JSON Schema from Phase 3).
- Android: Kotlin 2.4, AGP 9 (built-in Kotlin), Compose + Material 3, single activity, MVVM +
  unidirectional data flow, Hilt (ADR-0007), minSdk 29 / target 37 (ADR-0006). Build config lives
  in `android/build-logic` convention plugins; versions only in `android/gradle/libs.versions.toml` (ADR-0005).
- Converter: Python 3.13, uv (ADR-0004), FastAPI, Pydantic v2, Typer CLI, Docker (non-root).
  Planned stage pipeline A–I over a typed `Document` IR: see `docs/architecture.md`.
- Licensing (ADR-0003): MIT project. No GPL/LGPL/AGPL in the app. Copyleft in the converter only
  via a dedicated ADR, optional and swappable. Model weights checked separately. Matrix: `docs/licenses.md`.

## Conventions

- Kotlin: ktlint `android_studio` style via Spotless (no trailing commas), detekt with
  `android/config/detekt/detekt.yml`. Packages: `com.flowfolio.<layer>.<feature>`
  (namespace to be confirmed before any release build).
- Python: ruff (lint + format, line length 100), mypy `--strict`, pytest. Config only via
  `FLOWFOLIO_*` env vars (`converter/.env.example`).
- Commits: Conventional Commits, enforced by the commit-msg hook and CI.
  Scopes in use: `android`, `converter`, `ci`, `docs`.
- ADRs: `docs/adr/NNNN-title.md` from `0000-template.md`; accepted ADRs are never edited, only superseded.
- Corpus PDFs are never committed; only manifest, hashes, baselines.

## Commands

Windows dev machine; PowerShell shown, bash equivalents work in CI.

```powershell
# One-time setup
uv tool install pre-commit; pre-commit install          # hooks: pre-commit + commit-msg

# Android (from android/)
.\gradlew.bat assembleDebug                              # debug APK -> app/build/outputs/apk/debug/
.\gradlew.bat testDebugUnitTest                          # JVM unit + Robolectric Compose tests
.\gradlew.bat spotlessCheck detekt lintDebug             # static checks (spotlessApply to fix format)
.\gradlew.bat connectedDebugAndroidTest                  # instrumented tests; needs emulator/device
& "$env:ANDROID_HOME\emulator\emulator.exe" -avd flowfolio_api37   # start the local emulator

# Converter (from converter/)
uv sync
uv run flowfolio-convert serve --reload                  # http://127.0.0.1:8000/docs
uv run pytest; uv run ruff check .; uv run ruff format --check .; uv run mypy

# Docker (from repo root)
docker compose -f infra/docker/compose.dev.yml up --build   # converter on :8000

# All hooks on all files
pre-commit run --all-files
```

Local toolchain (installed 2026-10-16): Microsoft OpenJDK 21 (`JAVA_HOME`), Android SDK at
`%LOCALAPPDATA%\Android\Sdk` (`ANDROID_HOME`, user env var), platform 37, AVD `flowfolio_api37`
(Pixel 7, API 37, x86_64, WHPX acceleration), Android Studio, uv 0.13.1, gh, Docker.

## CI

`.github/workflows/ci.yml` (ADR-0008): `changes` → `commits`, `android`, `converter`, `docker`
→ `ci-gate` (the single required check). Actions are SHA-pinned; Dependabot updates weekly.

## Known issues

1. **Robolectric pinned to API 36** (`app/src/test/resources/robolectric.properties`): Espresso 3.7.0
   calls `InputManager.getInstance()`, which is gone in API 37. API 37 is covered by the instrumented test.
2. **Robolectric on JDK 21 needs `--add-opens/--add-exports`** for `java.base` internals; set in
   `AndroidApplicationConventionPlugin`.
3. **detekt 1.23.8 is 20 months old**; 2.0 is alpha. Works today; re-evaluate (licenses.md).
4. **Starlette warns that `httpx` is deprecated** for `TestClient` (suggests `httpx2`). Harmless now;
   decide in Phase 3.
5. Gradle prints "deprecated features ... incompatible with Gradle 10", coming from plugins. Track it.
6. Branch protection needs a paid plan while the repo is private; `ci-gate` is advisory until then.
7. Local git identity is the machine's global config; confirm the author name/email you want on commits.

## Decision log

ADRs 0001–0008 in `docs/adr/`: record decisions, monorepo, licensing policy, uv, Android build
structure, minSdk 29, Hilt, CI design.
