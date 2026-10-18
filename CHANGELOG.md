# Changelog

All notable changes to this project are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning: [SemVer](https://semver.org/).

## [Unreleased]

### Added
- Phase 1 milestone 3 (reader, in progress): `:core:pagecurl` (vendored pagecurl with book-spread back pages),
  `:feature:reader` with page curl in single-page and two-page spread layouts (cover alone, print pairing),
  layout chosen from the window (fold/unfold/rotation keep the position), byte-budgeted page cache of GPU-backed
  images, password prompt, edge-gesture guard, `tools/measure-reader.ps1` (frames + memory on device).

- Launcher icon from the owner's artwork (adaptive icon: gradient background plus the artwork, built by
  `tools/icon/make_launcher_icons.ps1`; `tools/icon/preview_launcher_icon.ps1` previews it under launcher masks).
- Skeuomorphic page shading (iOS Books style): a soft gutter shadow and crease at the binding with a faint highlight
  where the paper bulges, outer-edge shading lit from the upper left, in spreads and single pages; softer cast shadow
  and back-page gutter on the turning sheet. Gradients only, so no offscreen layers (ADR-0013).
- Direction-based page turns on either page (iOS Books): swipe left = forward, swipe right = back, even short swipes;
  tap the right page of a spread to go forward, the left page to go back.
- Reader chrome styled like print: the page number has no background (grey type, soft shadow) at the bottom left of
  the left page (bottom centre on a single page); a tap in the centre of the screen shows the book title (top right
  of the right page, top centre on a single page) and Back for 10 seconds.
- Phase 1 milestone 4 (reader navigation): reading position saved in Room (schema v2, `books.last_page`) and resumed
  on open; Contents (outline, opens on the current chapter), Go to page (printed labels first, then 1..N) and a page
  scrubber in the centre-tap controls; PDF links (internal jumps; web links confirmed, http/https/mailto only);
  pinch-zoom up to 6x with 512 px 16-bit tiles rendered after the view settles (at most 40 tiles, freed on exit);
  Reading settings ("Aa"): two pages side by side on/off and cover-alone pairing on/off.
- Phase 1 milestone 5, foundations slice (ADR-0015, `docs/plans/phase1-m5-design.md`).
  - The owner's mock (`docs/design/FlowFolio_Mock.html`) is now the design reference: its palettes, surfaces,
    gradients, glow and shadow values, motion curves and grain.
  - Bundled Fraunces and Instrument Sans (SIL OFL, 1.17 MB).
  - Components: card, chip, pill, icon button, primary and ghost buttons, segmented control, sliding tabs, the
    mock's toggle, gradient progress bar, progress ring.
  - The 3D book (true perspective, spine depth from the page count, lift on press), the shelf plank and the mock's
    icons.
  - A debug-only design catalog (`--ez catalog true`).
- Phase 1 milestone 5, app shell slice:
  - Library, Shelves, Bookmarks and Settings tabs in the mock's floating glass tab bar (folded), or a glass rail on
    the left when the window is 600 dp or wider (unfolded). The chrome slides away in the reader.
  - Real backdrop blur on the glass (Haze, Android 12+; tint only on older phones).
  - Animated splash on cold start (the mock's icon, ribbon and page curl, wordmark and tagline; tap to skip).
  - Screen entrance (fade and rise) and staggered reveal motion from the mock. The mock's focus blur and slight
    scale on entrance are left out: each frame of a changing blur, or of glows at a changing scale, left a texture in
    the GPU cache (+140 MB on the library screen before the fix). Animated shadows and glows now draw once into a
    layer that is then transformed. The library screen costs 247 MB (193 MB before the shell).
  - Theme changes are revealed in a circle growing from the touch (700 ms).
  - Status and navigation bar icons follow the app's theme.
  - Shelves and Bookmarks are placeholders until their slices.
- FlowFolio design language (ADR-0014): the owner's dark and light palettes (pixel-exact), glow in dark mode and
  soft shadow in light mode, rounded surfaces, own toggle and outline glyphs, themed dialogs and sheets; library,
  settings and the reader's sheets use it.
- Night pages (reader setting, dark mode only): text and line art light on dark, images keep their printed colours.
- Touch zones: outer 30% on each side turn pages (tap or swipe), the middle 40% is for taps (controls).
- `ReaderZoomOnDeviceTest` (UiAutomator 2.4.0) for pinch gestures on a device; Compose test tags are exposed as
  resource ids.

### Fixed
- Right-page turns finished on a fast default spring; they now finish at the pace of a full turn, like the left page.
- Taps on the page curl stopped turning pages after the first turn (the curl state was recreated on every turn).
- Reader memory budget (ADR-0013): 16-bit GPU-backed page images and gradient fold shading instead of the blurred
  curl shadow; `tools/measure-reader.ps1` now measures the app floor, samples retained memory and reports PASS/FAIL.
- Reader memory leaks found on the reference Fold: evicted GPU-backed pages are now recycled promptly, and PDFium's
  per-document caches are bounded by reloading the document every 100 renders (+12 MB/100 pages before).
- Phase 1 milestone 2: library and import. Modules `:core:database` (Room), `:core:datastore` (settings),
  `:core:data` (repositories), `:core:designsystem`, `:feature:library`. Import from the file picker and
  "Open with"/share; storage setting with the approved disclaimers (copy by default, or read in place); SHA-256 book
  ids, duplicate detection, metadata titles, JPEG covers, missing-file detection and "Locate file", delete, sort,
  settings screen (storage, ask every time, theme). The reader screen is a stub until milestone 3.
- Phase 1 milestone 1: `:core:model` (page sizing, outline) and `:core:pdf` with our PDFium JNI bridge (ADR-0010):
  file-descriptor loading for app and Storage Access Framework files, typed errors, password support, tile rendering,
  outline and page labels, armeabi-v7a/arm64-v8a/x86_64.
- Robustness test PDFs generated by `tools/testpdfs/make_test_pdfs.py` (labels, rotation, extreme sizes, zero pages,
  RC4 encryption, broken xref, non-PDF) plus damaged variants; on-device tests on the reference Fold.
- ADR-0009 (on-device conversion), ADR-0010 (PDFium), ADR-0011 (vendored pagecurl), ADR-0012 (module structure);
  PDF renderer spike report.
- Phase 0 foundation: monorepo layout, MIT license, `CLAUDE.md`, architecture overview,
  ADRs 0001–0008, dependency license matrix, conversion output contract draft (spec v0.1.0).
- Android skeleton: single-activity Compose + Material 3 app with Hilt, empty Library screen,
  convention plugins, Spotless/ktlint, detekt, unit tests, Robolectric Compose tests, and an
  instrumented launch test.
- Converter skeleton: FastAPI service with `/healthz` and `/version`, `flowfolio-convert` CLI,
  env-based settings, tests, multi-stage non-root Docker image, dev compose file.
- CI: path-aware GitHub Actions workflow (Android, converter, Docker smoke test, Conventional
  Commits check, `ci-gate`), Dependabot, pre-commit hooks.
- Golden corpus manifest schema (no books yet).

### Fixed
- `gradlew` committed without the executable bit, breaking the Linux CI build.
- CI commit-subject check rejected every commit (anchored regex used mid-pattern).

### Changed
- The Python converter is no longer the product path (ADR-0009); conversion moves on-device in Kotlin.
- Dependabot no longer proposes Python minor/major bumps for the converter image (pinned by ADR-0004).
