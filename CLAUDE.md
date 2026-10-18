# CLAUDE.md: FlowFolio project memory

Read this first. Keep it current: update at the end of every phase.

## Current phase

**Phase 1: PDF reader with page curl. Build in progress: milestones 1-4 done, milestone 5 (design) in progress: slices 1-3 done** (started 2026-10-16).
Phase 0 approved 2026-10-16 (first green CI: run 38979039796).
Spike done (docs/spikes/2026-10-pdf-renderer.md); ADR-0010 (PDFium via own JNI) and ADR-0011 (vendored pagecurl) accepted.
Build plan approved 2026-10-16: 5 milestones; check in with the product owner at milestone 3 with memory numbers.
Memory design: render at displayed size, 16-bit HARDWARE page images, one byte budget, prompt release of evicted pages,
PDFium reloaded every 100 renders, gradient curl shading, gradient-only page shading (`PageShading.kt`); create the curl state once per layout (pagecurl keys it on initialCurrent). Target (ADR-0013): reader <= floor + 110 MB, growth <= 10%.

## Product direction (owner decisions, 2026-10-16)

- **Foldables first** (reference device: Galaxy Z Fold 8 Ultra): single page on the cover screen,
  two-page spread when unfolded; slab phones and tablets must still work well.
- **Conversion runs on the device, inside the app** (ADR-0009). No server converter in the product.
- **FlowFolio (reflow) mode is the default.** Pinch changes font size; tapping an image opens a zoomable viewer.
- **Original mode** is a full PDF reader (pinch-zoom page, links, TOC, go to page).
- **Import storage** is a user setting: copy into the app (default) or read in place, each with a disclaimer.
- Any PDF/EPUB must work well; textbooks are the hardest case, not the only one.

## Working agreement (summary)

One phase at a time; plan before code; never claim something works without running it
(say "tested", "implemented but untested", or "stubbed"); ask before adding any dependency
(state license, last release date, alternatives); every significant decision gets an ADR;
Conventional Commits; keep `CHANGELOG.md`; no secrets in the repo; propose a spike when unsure.

## Architecture summary

- Android modules (ADR-0012): `:app`, `:core:model` (JVM), `:core:pdf` (PDFium JNI, NDK), `:core:database` (Room),
  `:core:datastore`, `:core:data` (repositories, import, reader access), `:core:designsystem`, `:core:pagecurl`
  (vendored), `:feature:library` (library + settings), `:feature:reader` (curl, spreads, page cache).
- Monorepo: `android/` app (including, from Phase 3, the on-device conversion engine). The Python
  `converter/` is no longer the product path (ADR-0009); its fate is decided in the Phase 3 plan.
  Output contract: `docs/conversion-spec.md`.
- Conversion engine (ADR-0009, planned): Kotlin Multiplatform `:core:conversion` (Android + JVM),
  PDFium via our own JNI bridge, ONNX Runtime for models, WorkManager foreground job; corpus quality
  gate runs on the JVM target in CI.
- Android: Kotlin 2.4, AGP 9 (built-in Kotlin), Compose + Material 3, single activity, MVVM +
  unidirectional data flow, Hilt (ADR-0007), minSdk 29 / target 37 (ADR-0006). Build config lives
  in `android/build-logic` convention plugins; versions only in `android/gradle/libs.versions.toml` (ADR-0005).
- Python converter skeleton (Phase 0, legacy): Python 3.13, uv, FastAPI, Docker.
- Licensing (ADR-0003 as amended by ADR-0009): MIT project. No GPL/LGPL/AGPL code or model weights
  anywhere, since everything ships in the app. Matrix: `docs/licenses.md`.

## Design language (ADR-0014, ADR-0015)

**`docs/design/FlowFolio_Mock.html` is the design reference for every milestone and phase.** Keep our 3D page curl;
on the unfolded Fold use a side rail and wider layouts. M5 plan: `docs/plans/phase1-m5-design.md`.

`:core:designsystem` holds everything visual. Use it instead of raw Material colours:
- the mock's tokens (`Palette`, `FlowFolioType`, `Motion`);
- the fonts: Fraunces and Instrument Sans;
- effects (`card`, `softShadow`, `innerShadow`, `glass`, `appBackground`);
- components (`FlowFolioCard`, `FlowFolioChip`, `FlowFolioTabs`, `FlowFolioToggle`, `Book3D`, `ShelfPlank`, ...);
- glyphs.

Check components on a device with the debug catalog:
`adb shell am start -n com.flowfolio.dev/com.flowfolio.MainActivity --ez catalog true [--es theme light]`.
When testing on the owner's phone, read every setting before you touch it and restore it afterwards.

Library, Shelves and Details live in `:feature:library` (Room v3 shelves; `ShelvesViewModel` is shared by all
three). Never animate a blur radius, or scale/rotate content with blurred shadows or glows, without an offscreen
layer: every frame leaves a texture in the GPU cache (see CHANGELOG, slice 2).

App shell (`app/AppShell.kt`): top-level tabs in the glass tab bar (folded) or rail (>= 600 dp); screens keep clear
of it with `Modifier.chromeStart()` / `chromeBottom()` (`LocalChromePadding`). Glass blurs what is marked
`glassSource()` (`LocalGlassState`, Haze). The splash is skipped when debug extras drive the app from adb.

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
# Textbook-based device tests skip unless the book is on the device:
#   adb push samples\corpus\cache\university-physics-volume-1.pdf /data/local/tmp/flowfolio/
# Regenerate the robustness PDFs (stdlib only):
#   python -I tools/testpdfs/make_test_pdfs.py android/core/pdf/src/androidTest/assets/pdfs
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
7. Local git identity is the machine's global config (owner chose to keep it).
8. PDFium license texts ship as assets but there is no in-app licenses screen yet (needed before release).
9. Reference Fold: `wm size` reports the inner screen even when folded (use `wm size -d 0`); folders created by adb
   under `Android/data/<pkg>` are invisible to the app; restart adb if the device shows `unauthorized`.
10. OpenStax *University Physics* is CC BY-NC-SA: local testing only; prefer CC BY titles for anything committed.
11. DataStore unit tests write once per store: DataStore's atomic rename fails on Windows when the file exists.
12. Linked ("read in place") books only work for files picked with the system picker; shared files are always copied.
13. Settings screen lives in `:feature:library` for now; reader settings live in the reader's "Aa" sheet
    (`ReaderSettingsSheet`). Split into `:feature:settings` when it grows.
14. Reader memory target (ADR-0013): at most 110 MB over the app's floor on the same screen and fold state, and at most
    10% growth from turn 50 to the last turn, on retained memory. Unfolded (300 turns, milestone 4 build, 2026-10-17): single +107.3 MB (little headroom left), spread +73.5 MB;
    zoomed in (tiles shown) the app read 242 MB vs 214 MB unzoomed.
15. Measuring on the Fold: logical display 0 is always the active panel; the phone must stay unlocked during runs
    (`tools/measure-reader.ps1` aborts if the lock screen shows). Debug-only intent extras `importPath`,
    `openBookId`, `layout` drive the app from adb.

16. Zoom is entered by pinching only. A double tap does not enter zoom because single taps turn pages instantly, and
    waiting to tell a single tap from a double tap would delay every turn. Inside zoom, a double tap leaves it.
17. Two-finger gestures can't be sent with `adb input`: use `ReaderZoomOnDeviceTest` (UiAutomator). Install the app
    and test APKs with `adb install -r -t` and run `adb shell am instrument -w -e class ...`, rather than
    `connectedDebugAndroidTest`, which uninstalls the app and its imported library afterwards.
18. Bottom sheets and dialogs are separate windows, so their test tags are not exported as resource ids; find their
    contents by text in UiAutomator.
19. The OpenStax test PDF has no page labels, so "Go to page" by printed number falls back to the position in the file.
## Decision log

ADRs 0001–0014 in `docs/adr/`: record decisions, monorepo, licensing policy, uv, Android build
structure, minSdk 29, Hilt, CI design, on-device conversion engine (supersedes 0002 in part and 0004),
PDFium via own JNI bridge, vendored pagecurl, Phase 1 modules, reader memory budget, design language and Night
pages (0014, proposed).
