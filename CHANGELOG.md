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
- Phase 1 milestone 5, Library, Shelves and Details slice:
  - Library as in the mock: header with search, sort and import; filter chips (All, In progress, Not started,
    Recently added); a "Continue reading" card with Resume; a row of 3D books on a plank per shelf, then the books on
    no shelf. Long-press a book to add it to shelves, locate its file or remove it.
  - Shelves: user collections (Room schema v3: `shelves`, `shelf_books`), cards with three books fanned out, a "New
    shelf" card, rename and remove; tapping a shelf scrolls the library to it.
  - Book details: blurred cover backdrop, swaying 3D cover, title, author, pills, Read/Continue and Add to shelf;
    Overview, Details and Contents tabs (the PDF's outline; tapping a chapter opens the reader there). Two panes when
    unfolded.
  - The cover flies from the shelf to the details page; Read swings the cover open before the reader appears.
  - Books keep the PDF's Author metadata (schema v3 `books.author`; books imported earlier show none until the
    metadata slice).
  - The backdrop blur is computed once on the CPU from a 40 px copy of the cover, not as a live GPU blur.
- Owner tweaks after slice 3:
  - The splash shows the owner's own icon artwork: it pops in, a band of light crosses it, then the wordmark rises.
  - Launcher icon refitted: the whole book fits the safe circle every launcher shape keeps, on the plate's blue
    extended to the edges (`tools/icon/make_launcher_icons.ps1` grows the plate's edge colours outward).
  - A shelf page: tap a shelf's book count to see the whole shelf as 3D tiles or a list. List rows show cover, title,
    author, progress and size, with a menu; swipe a row left for Rename and Delete, right for Shelves and Locate.
  - Books can be renamed (the library title; the file is unchanged).
  - 3D book spines are painted in the cover's dominant colour, with the title in a contrasting ink.
  - Shelf rows snap to a book when swiped.
  - The unfolded rail spreads its tabs over its full height.
  - Toggle on-colours swapped between dark and light mode.
- Phase 1 milestone 5, reader slice:
  - The mock's glass bars: Back, title, Contents, Go to page, "Aa" and the ribbon button on top; page, scrubber and
    percent below. They hide after 10 seconds, as before.
  - Bookmarks (Room schema v4, `bookmarks`): the ribbon button opens a strip of the app's five colours to put a
    ribbon on the page or recolour it, and a remove button. The ribbon hangs from the page's top edge and turns with
    it.
  - Page colours in "Aa": Auto, Paper, Sepia, Night and Black. Text and line art are recoloured in PDFium (images
    keep their colours); the status bar icons follow the paper. Auto gives Night pages in dark mode and printed
    white in light mode. This replaces the Night pages switch.
  - Page turns in "Aa": Curl (default), Slide (pages slide like a carousel) and Scroll (one continuous column, single
    pages).
  - Edge bounce: turning past the first or last page nudges the page and springs it back.
  - Reader memory on the unfolded Fold, spread, Night paper, 300 turns: 263 MB, growth 2.3%, 2.25% janky frames.
- Owner tweaks after the Bookmarks and Settings slice:
  - On Night and Black pages the Contents, Go to page and "Aa" panels use a nearly opaque glass, so their text stays
    readable over bright photos on the page.
  - Unfolded, the book's title sits beside Back at the top left.
  - Back (button or gesture) closes the book: the opening plays in reverse and the book flies back to where it was
    opened from.
  - The rail's tabs sit closer together in the middle of the rail.
  - Unfolded, bookmark cards in both columns have the same height.
- Phase 1 milestone 5, Bookmarks and Settings slice:
  - Bookmarks tab: every ribbon in the library as a card with its colour tag, the book's cover, the printed page and
    the chapter it is in (from the PDF's outline), newest first; colour filter chips; tap to open the book there with
    the book opening; swipe right to recolour, left to remove. Two columns unfolded.
  - Settings grouped as in the mock: Appearance (theme, Neon glow, page turn), Reading (two pages side by side, cover
    on its own), Library (import storage, ask every time) and Conversion ("On this device"). Two columns unfolded.
    The mock's reflow and reminder settings stay hidden until those features exist.
  - Neon glow (setting `neon_glow`, on by default) turns the text and icon glows off.
- Owner tweaks after the reader slice:
  - The reader's bars and panels take the light or dark palette of the page colour, so icons, the page number and
    the percentage read well on every paper; over dark papers the glass is a lighter frosted tint with a pale edge.
  - Contents, Go to page, the ribbon colours and the "Aa" sheet are glass panels drawn in the reader itself, so they
    blur the page behind (sheets and dialogs are separate windows the glass cannot see through). Contents opens under
    the top bar and stops above the bottom bar. Back or a tap outside closes them.
  - Ribbon colours: green replaces grey (a stored grey ribbon reads as green). The ribbon is flat and solid like the
    mock, drops in on a spring and pokes 6 dp above the page.
  - Changing the page colour is revealed in a circle from the touch, like the theme switch; the circle starts once the
    pages are drawn in the new colour.
  - Slide turns pages like a stack: the top page lifts off to the left over the next one.
  - Book opening (Read on the details page and Resume in the library): the cover flies to the page it opens on (the
    right-hand page of a spread), the paper floods in, then the cover swings open on its spine and the page melts in.
  - Unfolded, the details page's blurred cover spans the top half of the screen.

### Changed
- Shadows (cards, chips, buttons, glass, book contact shadows, the plank glow) are rendered once on the CPU into small
  alpha bitmaps and reused (`ShadowBitmaps`). As GPU blurs, each kept its own render target: the slice 3 library used
  525 MB on the unfolded Fold; now about 365 MB. Reader, spread, 300 turns: 263 MB, growth 0.7%, 1.9% janky frames.
- `tools/measure-reader.ps1` takes `-StartPage` (default 0) and sets the reading position before turning (debug-only
  `startPage` extra), since the position now persists between runs.
- FlowFolio design language (ADR-0014): the owner's dark and light palettes (pixel-exact), glow in dark mode and
  soft shadow in light mode, rounded surfaces, own toggle and outline glyphs, themed dialogs and sheets; library,
  settings and the reader's sheets use it.
- Night pages (reader setting, dark mode only): text and line art light on dark, images keep their printed colours.
  Replaced by the page colours of the milestone 5 reader slice.
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
