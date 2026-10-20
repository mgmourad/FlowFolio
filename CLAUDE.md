# CLAUDE.md: FlowFolio project memory

Read this first. Keep it current: update at the end of every phase.

## Current phase

**Phase 2: EPUB reading and pagination engine. Milestone 1 (EPUB in the library) built; milestone 2 (the paginated EPUB
reader, `docs/plans/phase2-m2.md`) in progress: engine, reader and islands built, device checks open** (started 2026-10-20; plan: `docs/plans/phase2-plan.md`;
spike: `docs/spikes/2026-10-epub-pagination.md`; ADR-0019 hybrid native pagination and ADR-0020 own EPUB parser, accepted 2026-10-20).
Phase 1 signed off by the owner 2026-10-20 (`docs/plans/phase1-signoff.md`); ADRs 0014, 0016-0018 accepted.
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
  `:core:datastore`, `:core:epub` (EPUB parsing, ADR-0020), `:core:reflow` (EPUB pagination and page drawing, ADR-0019), `:core:data` (repositories, import, reader access), `:core:designsystem`, `:core:pagecurl`
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
# Baseline profile and macrobenchmarks (:benchmark, on the unfolded Fold, textbook pushed; installs a separate
# `com.flowfolio` app with its own library; uninstall it afterwards). Commit the regenerated profile.
.\gradlew.bat :app:generateBaselineProfile               # -> app/src/release/generated/baselineProfiles/
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark"
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
13. Settings and the Bookmarks tab live in `:feature:library` for now; reader settings also live in the reader's "Aa"
    panel (`ReaderSettingsContent`). Split into `:feature:settings` when it grows.
14. Reader memory target (ADR-0013): at most 110 MB over the app's floor on the same screen and fold state, and at most
    10% growth from turn 50 to the last turn, on retained memory. Unfolded (300 turns, milestone 4 build, 2026-10-17): single +107.3 MB (little headroom left), spread +73.5 MB;
    zoomed in (tiles shown) the app read 242 MB vs 214 MB unzoomed.
    Since milestone 5 the library "floor" holds the design's GPU caches (glows, backdrop), 365-410 MB, so the script's
    floor-relative verdict no longer means anything. Judge the reader by its absolute memory against the pre-M5 floor
    (182 MB + 110 = 292 MB): slice 4 build, spread, Night paper, 300 turns: 263 MB, growth 2.3%. Opened from the
    library in the same process the reader reads about 456 MB, because the library's glow caches stay allocated.
15. Measuring on the Fold: logical display 0 is always the active panel; the phone must stay unlocked during runs
    (`tools/measure-reader.ps1` aborts if the lock screen shows). Debug-only intent extras `importPath`,
    `openBookId`, `layout`, `startPage` (0-based; the script sets it, since the position persists) drive the app from
    adb. The phone's "Stay awake" may read off; keep the screen on with a harmless `input keyevent 59` every few
    seconds, or a run measures a backgrounded app. Put the owner's reading position back afterwards.

16. Zoom is entered by pinching only. A double tap does not enter zoom because single taps turn pages instantly, and
    waiting to tell a single tap from a double tap would delay every turn. Inside zoom, a double tap leaves it.
17. Two-finger gestures can't be sent with `adb input`: use `ReaderZoomOnDeviceTest` (UiAutomator). Install the app
    and test APKs with `adb install -r -t` and run `adb shell am instrument -w -e class ...`, rather than
    `connectedDebugAndroidTest`, which uninstalls the app and its imported library afterwards.
18. Bottom sheets and dialogs are separate windows, so their test tags are not exported as resource ids; find their
    contents by text in UiAutomator.
19. The OpenStax test PDF has no page labels, so "Go to page" by printed number falls back to the position in the file.
20. The details cover sways forever (as in the mock), so UiAutomator can never get an idle state there: `uiautomator
    dump` fails on that screen. Tap by coordinates, or test it with Compose tests.
21. GPU memory: never draw blurs with BlurMaskFilter on the GPU for anything repeated or animated; use `softShadow` /
    `blurredShape` (cached CPU bitmaps, `ShadowBitmaps`). Text and glyph glows are still GPU blurs (about 70-100 MB in
    dark mode); Settings > Neon glow (`UserSettings.neonGlow`) turns them off.
22. Before any adb input or UI dump on the owner's phone, check that FlowFolio is the resumed activity
    (`dumpsys activity activities | topResumedActivity`) and stop if it is not, so no tap or dump reaches another app
    or the home screen. Delete pulled dumps after parsing.
23. The reader bars hide 10 seconds after the last interaction; a UI dump taken later will not find them.
24. Bookmarks are stored per page (one ribbon per page; adding again recolours it). The Bookmarks tab is slice 5.
28. Network (ADR-0017, ADR-0018): INTERNET is used by the Open Library lookup (`BookLookup`, off until the reader
    agrees) and by ML Kit, which downloads translation models (~30 MB per language) and sends anonymous usage data.
    Translation itself runs on the phone (`MlKitTranslator`, `TranslateOverlay`); history is Room v8 `translations`. Page numbers: `PageLabels` runs in
    `books.page_labels` (Room v7), from PDF labels or `FolioScanner` (margin folios).
27. Annotations (ADR-0016): page text from `nativePageText`; selection logic in `TextSelection`, gestures and drawing
    in `ReaderSelection` / `ReaderAnnotating`. Selection works in every turn style and zoomed in (`PageBounds`). Room was at schema v6
    (`annotations`, `books.right_to_left`). Right to left mirrors the book layer and each page (`LocalMirroredPages`).
26. Room is at schema v5 (`android/core/database/schemas`): books carry editable details and `book_type`;
    `reading_sessions` holds time read (`ReadingTimeViewModel`, `SessionTracker`). Device checks add real sessions to
    the owner's totals; keep reader visits short or say so.
29. Vendored pagecurl: never key composables on the moving edge (it rebuilt every page each frame); key on `current`
    and read the edge in `drawWithCache`. Never add fields to `PageCurlState.InternalState` (an inner class): the
    Compose compiler's `$stable` field goes missing at run time (`NoSuchFieldError`); keep extra state on the outer
    class. Selection state is read in draw/placement lambdas so a drag doesn't recompose the reader.
30. Measure smoothness on a non-debuggable build: a debuggable app runs Compose mostly in ART's interpreter, so frame
    times look 2-3x worse than users see. Temporarily set `isDebuggable = false` in the debug build type (same
    package and signature, library kept; don't commit it), install, `cmd package compile -m speed-profile -f`, open
    the book from the library (debug intent extras need a debuggable build), then reinstall the normal debug build.
31. `:benchmark` drives the `benchmarkRelease` / `nonMinifiedRelease` builds (`com.flowfolio`, debug-signed, R8 for
    the first). They accept the adb hooks through `BuildConfig.MEASUREMENT_HOOKS`, set per variant in
    `app/build.gradle.kts` (the plugin overwrites DSL settings on those build types); real release builds keep it false.
    Gradle's connected run uninstalls the test APK and hides crashes: when a run reports 0 tests, install both APKs and
    run `am instrument -w -r -e class ... -e androidx.benchmark.enabledRules BaselineProfile` by hand.
32. Room is at schema v10: `books.format` (PDF or EPUB) and `books.last_locator` (an EPUB's position, `chapter:offset`;
    `last_page` and `page_count` are at the size it was last read). Saving a plain page clears the locator, so the
    `startPage` debug extra works for EPUBs too. `tools/testepubs/make_test_epubs.py` makes the synthetic and hostile
    EPUBs; the IDPF samples are in `samples/corpus/manifest.yaml`.
33. EPUB islands (MathML, SVG, complex tables; ADR-0019): `IslandHost` keeps a WebView under the pages; `EpubIslands`
    queues work: measure the chapter being read, draw the islands on the pages planned, then measure the other chapters
    nearest first, each followed by `ReflowBook.repaginate` of that chapter only. `IslandRenderer` serves only the
    book's files and runs only our own scripts: one rewrites `mfenced` (gone from MathML Core) and draws brackets around
    tables as CSS borders, since phone fonts have no stretchy glyphs. The WebView sometimes has nothing drawn for a frame:
    blank captures are retried before being kept. A layout moves page numbers, so `EpubSession.revision` (a layout
    counter plus the islands' revision) is part of every `PageKey`; without it the cache shows old pages. Islands are
    measured at their full width and scaled down to the column. Islands cost memory and frames while turning pages
    (Linear Algebra: up to ~650 MB and ~20% janky frames, against ~330 MB and ~5% without islands; see
    `docs/plans/phase2-m2.md`).
25. The reader's panels (Contents, Go to page, ribbon colours, "Aa") are drawn in the reader's window (`ReaderPanels`),
    not as sheets or dialogs, so glass can blur the page; their tags are resource ids. The bars and panels use
    `FlowFolioPalette(dark = tone.dark)`, not the app theme.
## Decision log

ADRs 0001–0020 in `docs/adr/`: record decisions, monorepo, licensing policy, uv, Android build
structure, minSdk 29, Hilt, CI design, on-device conversion engine (supersedes 0002 in part and 0004),
PDFium via own JNI bridge, vendored pagecurl, Phase 1 modules, reader memory budget, design language and Night
pages (0014), annotations (0016), online services (0017), on-device translation (0018, supersedes the
Translate part of 0017), EPUB pagination engine (0019), EPUB parsing (0020).
