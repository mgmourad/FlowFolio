# CLAUDE.md: FlowFolio project memory

Read this first. Keep it current: update at the end of every phase.

## Current phase

**Phase 2 (EPUB reading and the pagination engine) signed off by the owner 2026-10-27**
(`docs/plans/phase2-signoff.md`). Next: Phase 3, converter v1 on the phone: plan drafted 2026-10-27
(`docs/plans/phase3-plan.md`, three milestones: M1 engine and extraction, M2 from structure to EPUB, M3 on the phone
and the quality gate), approved 2026-10-27. M1 and M2 are built together and signed off together. Approved with it:
kotlinx.serialization 1.12.0, EPUBCheck 5.4.0 (tests only), a release signing key, retiring the Python server (ADR-0043
to write), the OpenStax corpus, a 5-minute target for a 1,000-page textbook, and at least 20 test books loaded in the
app on the emulator, some on shelves.

**Phase 3 signed off by the owner 2026-10-28 and released as FlowFolio 0.3.0-alpha.1 (build 300001)**
(`docs/plans/phase3-m1m2-signoff.md`). Next: the owner's tweaks to M3 in three milestones, M3T1 (polish and
navigation), M3T2 (finished, favourites, search), M3T3 (Reading Goal, Dictionary from English Wiktionary): plan approved
2026-10-28 (`docs/plans/phase3-m3-tweaks-plan.md`); M3T1 built 2026-10-29, M3T2 and M3T3 built 2026-11-01 (Room v16
and v17, ADR-0046). ADR-0044 accepted by the owner 2026-11-06.

**M1 and M2 built 2026-10-27.** The engine is a plain JVM module (ADR-0044), the Python server is retired (ADR-0043). On the
`fold8_api37` emulator *University Physics* (959 pages) converts in about 58 s into a 40 MB EPUB and opens in the reader;
all 9 corpus PDFs give EPUBs with no EPUBCheck errors and sidecars that follow the schema; baselines and 30 hand-labelled
pages in `samples/golden/`. 27 test books are in the emulator's library, on shelves Textbooks and Classics.

**M3 built 2026-10-28 as the owner's Dynamic Flow design (ADR-0045).** The "Convert"
entry point became Dynamic Flow: one book, two views (PDF and reflowed EPUB), switched in the reader's "Aa" sheet, the
library's press-and-hold menu and the list's swipe, or on open (Settings). A foreground service in its own process
replaced the planned WorkManager job. CI now checks the CC BY corpus books (`corpus` job). Left: the Fold timing.

**Releases (ADR-0042, owner 2026-10-27):** from Phase 3, every signed-off phase is committed, pushed, tagged and
published as a GitHub pre-release: Phases 3-5 alpha, 6-8 canary, 9 beta. Version `0.<phase>.<patch>-<channel>.<n>`
(tag `v0.3.0-alpha.1`), `versionCode` = MAJOR×10,000,000 + MINOR×100,000 + PATCH×1,000 + n (300001), title
"FlowFolio 0.3.0-alpha.1 (build 300001)", APK `flowfolio-0.3.0-alpha.1-300001.apk` plus its SHA-256, signed with the
release key in `~/.flowfolio` (never in the repo). Checklist: set `flowfolio.versionName`/`versionCode` in
`android/gradle.properties`, add the CHANGELOG version heading, commit, push, wait for green CI, build the release APK,
tag, `gh release create --prerelease`.

**M4, the owner's batch of tweaks, built 2026-11-02** (CHANGELOG, Unreleased). Fourteen items in one batch, delivered
as a debug APK under the delivery rule below (no CI run): the AMOLED theme (`AppStyle.AMOLED`, `AmoledPalette`, black
in both of its `PaletteFamily` slots); confetti on anything that fills (`Confetti.kt`, built into
`FlowFolioProgressBar`, `FlowFolioRing` and `GradientProgressBar`); the shelf page's six orders in the library,
applied inside each shelf row (`ShelfSorting.kt`); the reminder's time-of-day window and solid/hollow day toggles
(`GoalReminder.kt`, split out of `ReadingGoalDialog.kt`); a target for `Glyph.ReadingGoal`; a deeper goal-path curve
with a green tick at its end; the library search box animated, focused and themed; a book's marks gathered on the
bottom right of its cover; one row of actions in book details in every fold state; and `ic_stat_flowfolio` on every
notification. Verified on `fold8_api37` folded and unfolded; three items were not reproducible on an emulator with no
reading history and are **implemented but untested**: the Continue Reading card's filled star and green tick, the
confetti itself, and the notification glyph in the status bar.

**M4b, a second batch of tweaks, built 2026-11-02** (CHANGELOG, Unreleased). Eight items: book details stacks its
actions (wide Read over a centred second row); the Continue Reading card's tick is green and its page count reads
"p. N/N"; a slogan and bottom-aligned buttons on every top-level page (`ScreenHeader.kt`); the reader's lookup search
reaches across both tabs at once; translations remember where they were asked for (**Room v18**: `translations.page`,
`translations.locator`, and `ReadingPlace` in `:core:model`) so they navigate like definitions; and four more ribbon
and highlighter colours. All eight verified on `fold8_api37` unfolded.

**M5 built over five rounds, 2026-11-02 to 2026-11-03** (CHANGELOG, Unreleased; owner's lists of 2026-11-02 and
2026-11-03). Reading Atmosphere (five moods over eight owner-supplied ambiences, plus a file or a stream address of
the reader's own), VoiceMark (Room v20), the detour mark, the offline Dictionary (ADR-0046), intentional tags
(Room v19), the first-launch tour, folders-to-shelves on import, in-app updates (ADR-0048) and Storage housekeeping.
Two new ADRs: **0047** (yt-dlp on embedded CPython for audio out of video pages; accepted by the owner 2026-11-03)
and **0048** (updating from the releases page; proposed). All verified on `fold8_api37` except where the
emulator cannot: see known issues 40 and 41.

**Released as FlowFolio 0.3.0-alpha.3 (build 300003) on 2026-11-03**, tag `v0.3.0-alpha.3`, GitHub pre-release,
APK SHA-256 `091ab24d3598d27ae7e78b6b7d2228e9a9e6bfdd2f79b9efbf025b18494fcd38`. **The first build anyone can
install**: the two earlier pre-releases were deleted the same day, tags and all, having never been downloaded
(ADR-0049). Signed with the **new** release key, `CN=FlowFolio Release` SHA-256 `59fbae55...`, scheme v3 only
(v1 off, minSdk 29; AGP signs with the highest applicable scheme). Green CI on `2899725` with Android and the
converter corpus both run; the APK verifies across `--min-sdk-version 29 --max-sdk-version 37`, installs and
launches on `fold8_api37`. Checked end to end afterwards: a 300002 build on the emulator reports
"0.3.0-alpha.3 is available" and offers Download, so the public repository, the releases API, the filename
version parsing and the comparison all work together.

**The launch posts are written and not yet sent**; the owner held them until alpha.3 shipped, which it now has.

**Released as FlowFolio 0.3.0-alpha.4 (build 300004) on 2026-11-03**: three fixes from the owner's own books --
code listings that came out as columns of single letters, letter-spaced text read as one word per letter, and the
three bundled ambiences no atmosphere offered -- plus "Make shelves from this folder" in a folder's menu. Engine
1.2.3, so Dynamic Flow bundles rebuild on first open. All three verified on `fold8_api37`: the DevOps book's
listings read properly, the atmosphere list offers all eight, and the folder menu made "Career Books" and
"Programming" from one nested folder.

**Released as FlowFolio 0.3.0-alpha.2 (build 300002) on 2026-11-03**, tag `v0.3.0-alpha.2`, GitHub pre-release.
Green CI on `c7b2c00`; the minified release APK was installed on `fold8_api37` and checked, extractor included, so
R8 is not stripping the Chaquopy bridge.

**That one release is signed with the DEV key** (owner's decision, 2026-11-03, the release key not being on the
build machine); its notes say it needs a clean install. **From 0.3.0-alpha.3 onwards the release key is the only
one that ships**, decided 2026-11-03: the dev key signs every sideloaded build and lives on a development machine,
so making it the identity users update against would let anyone holding it publish an update straight over their
install. `app/build.gradle.kts` now fails `packageRelease` when `flowfolio.releaseSigning.*` is missing, rather
than quietly emitting an unsigned APK as it did for alpha.2. The cost is one more clean install, taken now while
almost nobody has alpha.2 rather than after the repository is public.

**The repository is still private**, so the in-app update check (ADR-0048) gets a 404 from the releases API and
neither GitHub nor F-Droid distribution works for anyone else. It has to be made public for either.

Phase 2 in brief (details in the milestone sign-offs and the CHANGELOG):
- M1 EPUB in the library (ADR-0020); M2 the paginated reader (ADR-0019, islands ADR-0021); M2b the riffle scrubber
  (ADR-0022 to 0026; demo: `adb shell am start -n com.flowfolio.dev/com.flowfolio.MainActivity --ez riffleDemo true`);
  EPUB links and footnote popovers.
- M3 typography: seven reading fonts and the book's own (ADR-0027, ADR-0030), text settings and pinch, islands per
  layout and EPUB memory (ADR-0031), the M2 carry-overs.
- M4 Phase 1 features on EPUB (ADR-0040): selection from the layout (`ReflowBook.pageGlyphs`), highlights and ribbons by
  locator (`EpubSession.place`), the image viewer (`FigureZoom`); fixed-layout EPUBs moved to Phase 8.
- M5 close-out: `EpubPageTurnBenchmark`, `RobustnessOnDeviceTest`, memory run; chapters start on their own spread and
  only the reader's chapter is laid out before the pages show (ADR-0041).
- The owner's rounds: app themes (ADR-0028/0029), motion (ADR-0033 to 0035), shelves and select mode, folder import
  (ADR-0038), Open Library details (ADR-0039), the fixed dev signing key (ADR-0036).
- Measured on the Fold 2026-10-27, release build: page turns P50 3.2 ms; memory 279-291 MB over 300 turns; re-pagination
  18-45 ms on text, about 270 ms on equation spreads.
- Left open from Phase 2: the island zoom on the cover screen and pinch inside it not checked on a device; equation
  spreads can still take a little over 300 ms after a size change (the WebView re-measures the chapter).

Phase 1 signed off by the owner 2026-10-20 (`docs/plans/phase1-signoff.md`); ADRs 0014, 0016-0018 accepted.
Phase 0 approved 2026-10-16 (first green CI: run 38979039796).
Spike done (docs/spikes/2026-10-pdf-renderer.md); ADR-0010 (PDFium via own JNI) and ADR-0011 (vendored pagecurl) accepted.
Build plan approved 2026-10-16: 5 milestones; check in with the product owner at milestone 3 with memory numbers.
Memory design: render at displayed size, 16-bit HARDWARE page images, one byte budget, prompt release of evicted pages,
PDFium reloaded every 100 renders, gradient curl shading, gradient-only page shading (`PageShading.kt`); create the curl state once per layout (pagecurl keys it on initialCurrent). Target (ADR-0013): reader <= floor + 110 MB, growth <= 10%.

**Phase 3 signed off by the owner 2026-11-06** (`docs/plans/phase3-signoff.md`) and **released as FlowFolio
0.3.0-alpha.5 (build 300005)**, tag `v0.3.0-alpha.5`. All six exit criteria met, including the Fold timing:
*University Physics* (959 pages) converts in **199.8 s** (target 5 min) on the real Fold, in the `:convert` process,
opening reflowed. ADR-0044, 0048 and 0049 accepted with the sign-off; the licences screen (known issue 8) landed with
this close-out. Next: Phase 4.

**Phase 4 signed off by the owner 2026-11-06** (`docs/plans/phase4-signoff.md`) and **released as FlowFolio
0.3.0-alpha.6 (build 300006)**, tag `v0.3.0-alpha.6`. Delivered: fallbacks worth keeping (full-page covers, sharper
crops, captions with equations, full-width tables, a tap hint), syntax highlighting for code, the update check with a
"What's new" link, the folded-turn back-of-page fix, and the spurious-spaces fix. The pixel-verification core ended up
**diagnostic-only** — `geometricCoverage` is recorded but not gated (the geometric signal false-positives on
text-matrix-scaled PDFs), and the real pixel diff (mechanism B) is deferred. ADR-0050 (release-channel renumbering) is
proposed with this sign-off. Next: Phase 5.

**Phase 5 signed off by the owner 2026-11-07** (`docs/plans/phase5-signoff.md`) and **released as FlowFolio
0.5.0-canary.1 (build 500001)**, tag `v0.5.0-canary.1`. Delivered: progressive delivery (chapters readable as they
finish converting), the conversion health badge, Settings as a menu of pages, and the owner's polish batch (the
first-book demo with "Never show tips again", the reader's chapter-and-section running heads, the shelf-page header,
annotation counts and VoiceMark tabs, and the Favourites page header). ADR-0050 (release-channel renumbering: 3–4
alpha, 5–6 canary, 7 beta) is accepted with this sign-off. Next: Phase 6.

**Phase 6 M0 (the reading-surface tweak batch) built 2026-11-07** (`docs/plans/phase6-m0.md`):
the owner's batch arriving ahead of Phase 6's milestones. Delivered: first-use guides on the reader's buttons (centred
windows on first use — Contents, Go-to-page, Aa, ribbon, Annotations, Dictionary); the Bookmarks tab grouped one card
per book (title, author, ribbon colours, "N Bookmarks"); top-level page titles on the button row (smaller when folded);
the folded running head shifted left of the text border and cut at the front camera (read from the display cutout); the
unfolded section label right-aligned; the running head hidden while the chrome shows so it never overlaps the tools;
and the book-details title capped at three lines with a tap-to-view.

**Phase 6 signed off by the owner 2026-11-08** (`docs/plans/phase6-signoff.md`) and **released as FlowFolio
0.6.0-canary.1 (build 600001)**, tag `v0.6.0-canary.1`. Delivered: M1 in-book phrase search across both Dynamic Flow
and PDF views (`BookSearch`, `ReaderSearchPanel`) plus three-source Define across English Wiktionary, bundled Princeton
WordNet 3.1 (`wordnet.db.gz`), and Wikipedia summaries; M2 fixed-layout EPUB rendering page by page via
`FixedEpubPageSource`; M3 Narrator Mode powered by offline neural TTS via `sherpa-onnx` v2.0.1 (`:core:voice`,
ADR-0051) with 11 Kokoro voices and 2 Piper voices downloaded on first use, streaming synthesis, structural pauses for
titles/subtitles/paragraphs, zero-pause mid-sentence page transitions with pre-queued next-page audio and page-turn
sounds, line-break and cross-page hyphen rejoining, on-device plural and past-tense lexicon inflection generation, and
an `8.2 kHz` 2-pole Butterworth vocoder de-hiss filter; plus the unfolded right-edge vertical tool rail and R8 keep
rules for ML Kit and `sherpa-onnx` JNI callbacks. ADR-0051 is accepted with this sign-off. Next: Phase 7.

**Phase 6.5, the UX overhaul "Quiet Library", closed by the owner 2026-11-10 and released as FlowFolio
0.6.0-canary.2 (build 600002)**, tag `v0.6.0-canary.2`, gated on green CI.
- Nine phases, then the owner's review rounds (reader panels held to the mock, Details, Words, the dock), and a new
  app icon. The icon's source is `docs/brand/icon-concepts/`: Flowing Pages with a Fraunces F whose stem is the
  ribbon, built as vector layers by `make_vector.py`.
- Room is at v22 (`book_tags`). Next: Phase 7 (scanned books and OCR).

**UX overhaul "Quiet Library" (ADR-0052, accepted by the owner 2026-11-09; `docs/plans/ux-overhaul-plan.md`).**
This runs between Phase 6 and Phase 7 in nine phases, one at a time. Stop and report after each. Commits stay local
until the end, which ships as 0.6.0-canary.2. `docs/ux-review/` (the mock, the review, captures) is gitignored and stays
local.
- **Phase 1 (design system) built 2026-11-09.** Semantic `Tokens` per theme, built by `quietPalette`. The old role
  fields (`title`, `item`, `hint`, `subItem`, `glyph`) were removed on 2026-11-10: name `ink`, `ink2` or `ink3`. Night glow is off by default. New components: menu, sheet, dialog, snackbar,
  tooltip, stepper, settings tile and `BookCover`.
- **Phase 2 (app shell) built 2026-11-09.** The tabs are Library, Shelves, Notebook and Settings (`TopLevel`). Folded,
  they sit on a glass bar; unfolded, on a flat rail with the app mark and Settings at the bottom. Favorites is a
  Library chip. The Notebook hosts the old Bookmarks, Annotations and Dictionary pages as sections. The retired routes
  forward through `Destinations` in `app/`.
- **Phase 3 (Library) built 2026-11-09.** Header, global search (`GlobalSearch`, two-letter minimum), stats band
  (`UserSettings.statsBand`), Continue card with ⋯ and the unfolded goal ring, folded `GoalCard`, flat 2:3 `BookTile`s
  in Shelf, Grid or List view (`UserSettings.libraryView`), six `LibrarySort` orders applied everywhere, and the new
  `BookMenu`. The `row-loose` crash is fixed (`ShelfRow.key`). Share file is not in the book menu yet (owner to decide).
- **Phase 4 (Shelves) built 2026-11-09.** Folder cards with flat fanned covers, the ⋯ menu, `ShelvesOrder` sort
  (remembered), New shelf with colours and an "Add books" snackbar, Delete with Undo; shelf pages restyled; `ShelfBook`
  draws the flat cover.
- **Phase 5 (Notebook) built 2026-11-09.** `MarkCounts` (`:core:data`) is the one count of marks every screen
  reads. `NotebookHome` (kind chips, a card per book, export and share as text) and `NotebookBookScreen` (tabs by kind,
  Open, Share, Delete with Undo).
- **Phase 6 (Details) built 2026-11-09.** The main button names where it lands, a ★ beside it, four fact tiles with
  plurals, the Notebook tab, and Export notes and Reset progress in ⋯.
- **Phase 7 (Settings) built 2026-11-09.** A ten-section hub (`SettingsPage`, `SettingsHubRow`); folded it opens pages,
  unfolded it shows list and page side by side. **Room v21** `book_text`: per-book text (`BookTextRepository.effective`),
  set from the reader's Aa and pinch; Settings → Text holds the defaults.
- **Phase 8 (Reader) built 2026-11-09, finished 2026-11-10.**
  - A top bar (Back, a title chip, the ribbon, ⋯) and a six-tool dock above the riffle bar (`ToolDock`) replace the
    right rail; panels open above the dock.
  - In-book search waits for two letters, groups its results by chapter, and falls back to the PDF while Dynamic Flow
    prepares.
  - The selection menu has nine colours over six labelled actions (`HighlightBar`).
  - Translate keeps language chips (`UserSettings.translateLanguages`); Listen has speed segments.
  - Coach marks (`ReaderCoach`, guide id `reader_coach`) point at `Modifier.coachSpot` targets.
- **Phase 9 (sweep) built 2026-11-09, finished 2026-11-10.**
  - Reset progress waits for RESET to be typed, and every icon button has a tooltip.
  - The tour has 11 slides with retaken pictures; the goal is a sheet.
  - What's new reads the `CHANGELOG.md` that `copyChangelog` bundles into the APK (`Changelog` in
    `:feature:library`).
  - `docs/plans/ux-overhaul-parity.md` maps every feature to its new home.
  - Released as 0.6.0-canary.2 (see below).
- **Owner's post-overhaul fixes, 2026-11-10.**
  - Details follows the mock (cover column, actions on one line, fact tiles, Overview chips). **Room v22**
    `book_tags`, a book's own tags (`BookTagRepository`).
  - The reader's dock is one glass card, the riffle with the tools under it (`RiffleBar(footer)`), and its fore
    edge is monochrome via `BoardColors.ink`.
  - Words is one panel (`WordsPanel`: Define, Translate, History); `DefineOverlay` is gone.
  - The folded goal ring shows the reading glasses, then 🎉.
- **Reader panels held to the mock, 2026-11-10 (second round).**
  - Every panel is a `DockPanel`: the dock's width, 10 dp above it (`DockSpace`), with the riffle kept under it.
  - Search has Whole book, This chapter and Match case.
  - Aa is Text, Page and Behaviour in `OptRow`/`SwitchRow`s, with Dynamic Flow as the "View" row.
  - Contents has a Ribbons tab, and the pill shows the time left (`ReadingEstimate`).
- Handover APKs get a new name every build (`app/build/outputs/apk/debug/handover/`) and are installed on the owner's
  phone as well as the emulator.

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

**Delivery (owner, 2026-11-02).** CI gates run at **phase** boundaries, not for milestone tweaks and fixes: tweak
work is verified locally (`spotlessCheck detekt lintDebug testDebugUnitTest` and the `fold8_api37` emulator) and must
say plainly that CI has not run. Tweaks and fixes are delivered in **large batches**; at the end of a batch, build
and hand over the debug APK (`assembleDebug`, fixed dev key ADR-0036), then update the documentation the batch needs
(this file, `CHANGELOG.md`, an ADR for anything significant) in one pass. Work changes hands between sessions, so
leave the repository able to explain itself: what is in flight, what was verified and how, and what is still open.

## Architecture summary

- Android modules (ADR-0012): `:app`, `:core:model` (JVM), `:core:pdf` (PDFium JNI, NDK), `:core:database` (Room),
  `:core:datastore`, `:core:epub` (EPUB parsing, ADR-0020), `:core:reflow` (EPUB pagination and page drawing, ADR-0019),
  `:core:voice` (offline neural TTS on `sherpa-onnx` for Kokoro and Piper voices, ADR-0051), `:feature:reader:riffle`
  (the riffle scrubber, ADR-0022), `:core:data` (repositories, import, reader access), `:core:designsystem`, `:core:pagecurl`
  (vendored), `:feature:library` (library + settings), `:feature:reader` (curl, spreads, page cache),
  `:core:conversion` (the PDF to EPUB converter, plain JVM, ADR-0044), `:core:extract` (yt-dlp on an embedded
  CPython 3.12 through Chaquopy, for audio out of a video page, ADR-0047), `:tools:convert` (the converter on the
  desktop through JNA, the corpus harness; development only, never in the app).
- Monorepo: `android/` holds the app and the conversion engine; the Python server is retired (ADR-0043).
  Output contract: `docs/conversion-spec.md` v1.0.0 and `docs/schemas/flowfolio.schema.json`.
- Conversion (ADR-0009, ADR-0044): runs on the phone. `Converter` (`:core:conversion`) reads a `PdfSource`:
  `PdfiumConversionSource` in `:core:pdf` (JNI, WebP images) on the phone, `JnaPdfSource` (PNG) on the desktop.
  Stages over the `Document` IR: `Extraction`, `DesignSystem`, `Regions`, `Shapes`, `ReadingOrder`, `Blocks`,
  `Headings`/`Linking`, then `EpubWriter`/`ChapterXhtml` and `SidecarBuilder`. Superscripts are marked in the text with
  private-use characters (`Line.marked`, `SUP_OPEN`...) and written as `<sup>`/`<sub>`; `plain()` strips them.
- Dynamic Flow (ADR-0045): `core/data/flow/` holds the bundle (`FlowFiles`, `files/flow/<book>/book.epub`), the
  service (`FlowService`, process `:convert`, broadcasts in `FlowProtocol`), `DynamicFlow` (statuses and notices for
  the app) and `ReadingViews` (each book shows only its current view's highlights and ribbons: locator != null is the
  reflowed view). The reader: `ReaderViewModel.FlowState`, `DynamicFlowSwitch.kt`, `FlowCarrier` ("convert the
  changes"); the converter's `#page-N` page-list markers map PDF pages to places (`ReflowBook.sourcePages`).
- Android: Kotlin 2.4, AGP 9 (built-in Kotlin), Compose + Material 3, single activity, MVVM +
  unidirectional data flow, Hilt (ADR-0007), minSdk 29 / target 37 (ADR-0006). Build config lives
  in `android/build-logic` convention plugins; versions only in `android/gradle/libs.versions.toml` (ADR-0005).
- Licensing (ADR-0003 as amended by ADR-0009): MIT project. No GPL/LGPL/AGPL code or model weights
  anywhere, since everything ships in the app. Matrix: `docs/licenses.md`.

## Design language (ADR-0014, ADR-0015)

**Since ADR-0052 the design reference is the Quiet Library mock (`docs/ux-review/2026-11-09/flowfolio-app-mock.html`,
local only); `docs/design/FlowFolio_Mock.html` is historical.** Keep our 3D page curl;
on the unfolded Fold use a side rail and wider layouts. M5 plan: `docs/plans/phase1-m5-design.md`.

`:core:designsystem` holds everything visual. Use it instead of raw Material colours. Since ADR-0028 a theme is a
`PaletteFamily` (light and dark `Palette`, plus a `SurfaceTexture`); every new token needs a value in all three themes:
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
`glassSource()` (`LocalGlassState`, Haze). Every screen but the reader is a glass source, for the shell's themed `EdgeFades`; screens
wrap in `ScreenEntrance` and mark elements with `Modifier.reveal(order)` (ADR-0034). The splash is skipped when debug extras drive the app from adb.

## Conventions

- Kotlin: ktlint `android_studio` style via Spotless (no trailing commas), detekt with
  `android/config/detekt/detekt.yml`. Packages: `com.flowfolio.<layer>.<feature>`
  (namespace to be confirmed before any release build).
- Python: standard-library scripts in `tools/` only, run with `python -I`; ruff 0.17.1 (line length 120) through
  `uvx` in the pre-commit hooks.
- Commits: Conventional Commits, enforced by the commit-msg hook and CI.
  Scopes in use: `android`, `ci`, `docs`, `tools`.
- ADRs: `docs/adr/NNNN-title.md` from `0000-template.md`; accepted ADRs are never edited, only superseded.
- Corpus PDFs are never committed; only manifest, hashes, baselines.

## Commands

**Testing and handing over builds (owner, 2026-10-26).** Test every change on the `fold8_api37` emulator, folded and
unfolded: inner 2256x2504, cover 1080x2520, density 480, API 37, like the owner's Fold. Start it with
`emulator -avd fold8_api37 -port 5560` (serial `emulator-5560`); `adb -s emulator-5560 emu fold` / `emu unfold`; wake it
with `input keyevent KEYCODE_WAKEUP; svc power stayon true`; screenshots need `screencap -d <id>` (`dumpsys
SurfaceFlinger --display-id`: display 0 unfolded, display 1 folded). Then hand the owner the debug APK: it is signed
with the fixed dev key (ADR-0036, `~/.flowfolio/flowfolio-dev.jks`, `flowfolio.devSigning.*` in
`~/.gradle/gradle.properties`) so it updates the app on the phone in place.

Windows dev machine; PowerShell shown, bash equivalents work in CI.

```powershell
# One-time setup
uv tool install pre-commit; pre-commit install          # hooks: pre-commit + commit-msg

# Android (from android/)
.\gradlew.bat assembleDebug                              # debug APK -> app/build/outputs/apk/debug/
.\gradlew.bat testDebugUnitTest                          # JVM unit + Robolectric Compose tests
.\gradlew.bat spotlessCheck detekt lintDebug             # static checks (spotlessApply to fix format)
.\gradlew.bat connectedDebugAndroidTest                  # instrumented tests; needs emulator/device
.\gradlew.bat :feature:reader:riffle:verifyRoborazziDebug # screenshot tests vs src/test/screenshots (ADR-0024;
                                                          # recordRoborazziDebug after an intended drawing change)
# Baseline profile and macrobenchmarks (:benchmark, on the unfolded Fold, textbook pushed; installs a separate
# `com.flowfolio` app with its own library; uninstall it afterwards). Commit the regenerated profile.
.\gradlew.bat :app:generateBaselineProfile               # -> app/src/release/generated/baselineProfiles/
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark"
# EpubPageTurnBenchmark needs moby-dick.epub in /data/local/tmp/flowfolio; on an emulator add
#   "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR" (numbers only relative).
# Robustness corpus (M5): push samples/corpus/cache/epub-idpf/*.epub and epub-generated/*.epub to the same folder, then
#   .\gradlew.bat :core:reflow:connectedDebugAndroidTest   (RobustnessOnDeviceTest, PaginationOnDeviceTest)
# Textbook-based device tests skip unless the book is on the device:
#   adb push samples\corpus\cache\university-physics-volume-1.pdf /data/local/tmp/flowfolio/
# Regenerate the robustness PDFs (stdlib only):
#   python -I tools/testpdfs/make_test_pdfs.py android/core/pdf/src/androidTest/assets/pdfs
& "$env:ANDROID_HOME\emulator\emulator.exe" -avd flowfolio_api37   # start the local emulator

# Converter corpus (Phase 3). Download the books (stdlib; hashes pinned in samples/corpus/manifest.yaml):
python -I tools/corpus/fetch_corpus.py
# From android/: the converter's JVM tests (robustness PDFs, the 9-book corpus against samples/golden/, ~6 min)
.\gradlew.bat :core:conversion:test :tools:convert:test
.\gradlew.bat :tools:convert:test --tests "*CorpusTest*" "-Pflowfolio.recordBaselines"   # after a deliberate change
# The converter on the desktop (development only): installDist once, then
#   $env:JAVA_OPTS="-Djna.library.path=<repo>/android/tools/convert/build/pdfium"
#   tools\convert\build\install\convert\bin\convert.bat convert|page|probe|unmatched|render <file.pdf> ...
.\gradlew.bat :tools:convert:installDist
# On the device: ConversionOnDeviceTest converts the pushed textbook through JNI (install the :core:pdf test APK with
#   adb install -r -t, then am instrument -w -e class com.flowfolio.core.pdf.ConversionOnDeviceTest
#   com.flowfolio.core.pdf.test/androidx.test.runner.AndroidJUnitRunner); the EPUB is in the test app's cache/.

# All hooks on all files
pre-commit run --all-files
```

Toolchain: JDK 21 (`JAVA_HOME`), the Android SDK (`ANDROID_HOME`) with platform 37, Android Studio, uv, `gh`.
Emulators used here: `fold8_api37` (the reference Fold, see Commands) and `flowfolio_api37` (Pixel 7, API 37).

## CI

`.github/workflows/ci.yml` (ADR-0008): `changes` → `commits`, `android`, `corpus` → `ci-gate` (the single required
check). The android job's `testDebugUnitTest` also runs the converter's JVM tests (PDFium's Linux build is fetched);
the `corpus` job fetches the three CC BY corpus PDFs (cached on the manifest's hash) and runs `CorpusTest` on them.
Locally, `-Pflowfolio.corpus=<dir>` points `CorpusTest` at another folder (e.g. only those three books). Actions are SHA-pinned; Dependabot updates weekly.

## Known issues

1. **Robolectric pinned to API 36** (`app/src/test/resources/robolectric.properties`): Espresso 3.7.0
   calls `InputManager.getInstance()`, which is gone in API 37. API 37 is covered by the instrumented test.
2. **Robolectric on JDK 21 needs `--add-opens/--add-exports`** for `java.base` internals; set in
   `AndroidApplicationConventionPlugin`.
3. **detekt 1.23.8 is 20 months old**; 2.0 is alpha. Works today; re-evaluate (licenses.md).
4. The debug `importPath` hook is ignored while the reader is open, and the emulator sleeps when folded over adb:
   go back to the library and wake the device (`input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard`) before
   importing, or `openBookId` shows "File not found".
5. Gradle prints "deprecated features ... incompatible with Gradle 10", coming from plugins. Track it.
6. **`main` is protected** (2026-11-03): `ci-gate` is a required status check, and force pushes and deletion are
   blocked. Deliberately *not* set: pull requests are not required, and `enforce_admins` is off, so the owner can
   still push straight to `main` -- the working agreement verifies tweak batches locally and runs CI gates at phase
   boundaries, and a PR per tweak would fight that. The protection is what gates outside contributors' pull
   requests. `enforce_admins` being off also leaves a future history rewrite possible (known issue 7).
7. **Git identity is pinned per repository** (2026-11-03). The machine's global config is a personal address;
   `git config user.email` in this checkout is the GitHub noreply one, so commits here never carry a personal
   address again. A fresh clone has to set it again -- the setting lives in `.git/config`, which is not cloned.
   The owner intends to move to an address on their own domain once that is set up; that would be a third mailmap
   pass over the history, or simply a change from then on.
8. ~~PDFium license texts ship as assets but there is no in-app licenses screen yet (needed before release).~~
   Resolved 2026-11-06: an in-app licences screen (Settings → About → Open-source licences) reads the bundled
   `third_party_licenses/` assets and shows the fonts' and PDFium's full texts (PDFium's third-party notices are
   enumerated from the assets at runtime), and lists the remaining shipped components by name and licence. Full
   texts for the non-bundled dependencies (Compose, Hilt, ML Kit, …) are named, not reproduced here; bundle them
   as assets if that ever matters. Verified on `fold8_api37`.
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
    M3 (ADR-0031, 2026-10-22, Material You dark, debug): Moby-Dick 308 MB, Linear Algebra 366 MB (was 468-572), the
    PDF 278 MB, library 280-303 MB. About 90 MB of graphics on any screen is the window's four full-screen buffers.
    The measurement script reopens books at fixed pages and adds reading time: snapshot `databases/flowfolio.db*` with
    `run-as cp` (app stopped) before, and copy it back afterwards.
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
23. The reader bars hide 5 seconds (the default "Hide controls after") after the last use of them, and the system bars
    hide with them; a UI dump or a tap sent later will not find them. Over adb, show them and act in the same command.
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
    class. Selection state is read in draw/placement lambdas so a drag doesn't recompose the reader. `PageCurlState.onTurn`
    (ADR-0026) is called as a turn commits; `InternalState.turnStarting` calls it.
30. Measure smoothness on a non-debuggable build: a debuggable app runs Compose mostly in ART's interpreter, so frame
    times look 2-3x worse than users see. Temporarily set `isDebuggable = false` in the debug build type (same
    package and signature, library kept; don't commit it), install, `cmd package compile -m speed-profile -f`, open
    the book from the library (debug intent extras need a debuggable build), then reinstall the normal debug build.
31. `:benchmark` drives the `benchmarkRelease` / `nonMinifiedRelease` builds (`com.flowfolio`, debug-signed, R8 for
    the first). They accept the adb hooks through `BuildConfig.MEASUREMENT_HOOKS`, set per variant in
    `app/build.gradle.kts` (the plugin overwrites DSL settings on those build types); real release builds keep it false.
    Gradle's connected run uninstalls the test APK and hides crashes: when a run reports 0 tests, install both APKs and
    run `am instrument -w -r -e class ... -e androidx.benchmark.enabledRules BaselineProfile` by hand.
49. **The launcher icon is generated.** `app/src/main/res/drawable/ic_launcher_*.xml` come from
    `docs/brand/icon-concepts/make_vector.py`; `flowfolio_icon.png` (splash, rail) from `render512.html`. Change the
    script and regenerate rather than editing the XML by hand.
48. **Room is at schema v22** (v22: `book_tags`; v21: `book_text`). The migration tests replay the committed schema JSON
    into SQLite (`BookTextMigrationTest`, `BookTagMigrationTest`), since `room-testing` is not a dependency.
32. Room is at schema v14 (v14: `annotations.locator` and `bookmarks.locator`, an EPUB highlight's or ribbon's
    `chapter:offset`; an EPUB highlight's `char_start`/`char_end` are then chapter offsets and its `page` and `rects` only
    where the reader last placed it, ADR-0040). At v13: `shelf_books.position`, a shelf's drag-and-drop order, smallest first, ties newest first).
    At v12: `books.folder`, the folder a book came in with a folder import, and the looked-up `subjects`,
    `print_pages`, `series`, `rating`, `ratings_count`; ADR-0037/0038). At v11: `shelves.color`, a `ShelfColor` name or null for the theme's). At v10: `books.format` (PDF or EPUB) and `books.last_locator` (an EPUB's position, `chapter:offset`;
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
    measured at their full width and scaled down to the column. ADR-0021: `IslandStore` keeps sizes and PNGs in
    `cacheDir/islands/<book>/v<version>-<textPx>-<columnPx>/` (200 MB cap, oldest layouts trimmed); equations are stored
    in black and tinted on load. After the reader's own work, the queue draws every island ahead while the reader is
    still (1.5 s after the last turn). While that runs the WebView keeps the window busy, so `uiautomator dump` fails
    ("could not get idle state"): tap by coordinates, or wait until the book is cached. Bump `IslandStore.VERSION`
    whenever the parser could number islands differently.
34. Room is at schema v15 (`books.flow_mode`: null never chosen, 0 PDF, 1 Dynamic Flow; `books.flow_locator`, the
    place in the reflowed view, while `last_page` keeps the PDF page). A Dynamic Flow book's highlights and ribbons
    with a locator belong to the reflowed view, those without to the PDF. Seeding test rows by hand: bookmark colours
    are `BookmarkColor` names (`SUB_ITEM`, `TITLE`, ...); an unknown name reads back as `SUB_ITEM`. The service logs
    `FF_FLOW <book> ok|<failure> in <ms>`; `dumpsys notification --noredact` shows its progress notification.
36. Conversion spec 1.1.0 / engine 1.1.0 (2026-10-28): `Monospace` measures code fonts; `Blocks` sets code by column
    (`codeText`), lists by marker and depth (`ListItem`), unruled tables (`withTables`, only when the reading order goes
    across), hanging indents; `Line.marked(isCode)` marks inline code (`CODE_OPEN`/`CODE_CLOSE`, paired by
    `ChapterXhtml.balanced`). Bump `SidecarBuilder.ENGINE_VERSION` whenever output changes: `FlowFiles.outdated` then
    makes Dynamic Flow bundles convert again on next open, and `FlowCarrier.reanchor` finds their highlights again. The
    reader draws list markers (`TextStyle.marker`, outside the text) and code panels (`TextStyle.preformatted`). The
    local corpus has five technical books; `technical.pdf` (made by `tools/testpdfs`) is checked by
    `TechnicalBookTest` in CI. Shell heredocs in Git Bash drop backslashes: write edit scripts to files.
    Spec 1.2.0 / engine 1.2.2 (the owner's own books, 2026-10-28): `Line.marked` takes `Mark`s (code, bold, italic);
    paragraphs break on the line step (`Blocks.pitches`), not box gaps; symbol and emoji bullets are kept
    (`<li style="list-style-type: '✅ '">`, read by `ChapterParser`); the body style is the most used one not on shaded
    panels (`DesignSystem.proseBody`); ruled tables are built from rule stacks cut where side borders stop
    (`Regions.bordered`), grown by `withEdges`/`withRowsBelow`. `OwnBooksTest` covers these. The reader sets code in
    `CodeFace`: JetBrains Mono (bundled, `DesignR.font.code_jetbrains_mono`, installed by `ReaderViewModel`), else a
    monospaced system font file; never `Typeface.MONOSPACE`, which is proportional under Samsung font styles.
    Desktop debugging: `convert.bat probe <pdf> <index>` lists paths, `page` lists the book's styles.
35. The converter reads printed page numbers only from folios its `DesignSystem` finds; some books (OpenStax
    *Physics*) have folios it misses, so their Dynamic Flow page labels fall back to the PDF page number, while the PDF
    view's labels come from the app's `FolioScanner`.
25. The reader's panels (Contents, Go to page, ribbon colours, "Aa") are drawn in the reader's window (`ReaderPanels`),
    not as sheets or dialogs, so glass can blur the page; their tags are resource ids. The bars and panels use
    `FlowFolioPalette(dark = tone.dark)`, not the app theme.
37. **This Mac cannot run the converter's JVM tests.** `android/tools/convert/build/pdfium/libpdfium.so` is an x86-64
    **Linux** ELF (fetched for CI); JNA on Darwin arm64 needs a `.dylib`, so `:tools:convert:test` fails three tests
    (`CorpusTest`, `TechnicalBookTest`, `ConversionTest`) with `ExceptionInInitializerError` in `NativeLibrary`
    whatever the app code says. Run the other checks with `-x :tools:convert:test` and say that is what was run.
38. **The `fold8_api37` emulator keeps both panels alive at once**, so the app can be on the cover panel while the
    launcher is on the inner one. SurfaceFlinger's ids (`dumpsys SurfaceFlinger --display-id`) are what `screencap -d`
    wants; the *logical* ids (`dumpsys display | grep mDisplayId`) are what `input -d` wants, and they differ -- the
    cover panel was SurfaceFlinger `4619827551948147201` but logical display **2**. A tap sent without `-d` goes to
    display 0 and silently does nothing. `emu unfold` does not move a running activity: `am force-stop` first, then
    `am start --display 0`.

39. **The emulator's renderer must stay `host`.** `hw.gpu.mode=auto` picks software rendering (`swangle` +
    `lavapipe`) on Apple Silicon, which flickers under the design's blurs at the Fold's resolution. The AVD's
    `config.ini` now pins `hw.gpu.mode=host` (Metal); a backup of the old file sits beside it. `angle_indirect` is
    not a valid mode on emulator 37.2 and silently falls back to `auto`.

40. **Recording crashes the `fold8_api37` emulator.** `MediaRecorder.start()` takes the host audio stack down with
    it, whether the microphone permission was granted through the dialog or `pm grant`. VoiceMark recording therefore
    cannot be checked on an emulator; the pill's clock is held to its behaviour by `VoiceTimerTest` instead, and
    `VoiceRecorder` is an interface so a test can stand in for the microphone.
41. **Emulator wrangling, M5 round 4.** `-gpu host` can wedge on a cold start after a crash ("hanging thread 'QEMU2
    main loop'"); the cause is usually a crash-consent dialog waiting offscreen. Clear the emulator's crash
    database (`/tmp/android-$USER/emu-crash-*.db` on macOS), delete any `*.lock` under the AVD, and start with
    `-no-metrics`. Do not change
    `hw.gpu.mode` in `config.ini` (known issue 39) -- pass `-gpu` on the command line if you must. `emu fold` does
    not reliably move a running activity to the cover panel; capture on the inner panel instead.
42. **The release signing key is LOST** (owner, 2026-11-03), not merely missing from this machine. There is no
    recovery: Android's key rotation signs a lineage *with the old key* to authorise a new one, so without it no
    build can ever install over `v0.3.0-alpha.1`, which that key signed. `CN=FlowFolio Release, O=FlowFolio`,
    SHA-256 `560eb00ca346b33a79d9d9fdd465b0762a8d0d9662b4145406b19e9e06bb7d0d`, is **permanently retired** -- an
    APK presenting it is not ours.
    The cost is near zero and only while the launch is held: **both pre-releases have 0 downloads** (the repository
    was private until 2026-11-03). **ADR-0049 proposes generating a new release key now**, before any
    announcement, with custody written down -- a password manager entry *and* GitHub Actions secrets -- because the
    old key was lost precisely because nothing said where it had to live. It also proposes deleting
    `v0.3.0-alpha.1` and `v0.3.0-alpha.2`, which nobody can update from.
    **Never sign a release with the dev key** (`CN=FlowFolio Dev`, SHA-256 `bf34f1e7...`): it signs every
    sideloaded build and is handed around, so making it the update identity lets anyone holding it publish over a
    reader's install. 0.3.0-alpha.2 was signed that way once, by the owner's decision; `checkReleaseSigning` in
    `app/build.gradle.kts` now makes it impossible to repeat by accident.
    The update check is **resolved** (2026-11-03): the repository is public, the releases API answers 200, and the
    app reports "You are on the newest build" (checked on `fold8_api37` against the installed 300002). While it was
    private the API answered 404 and the app reported every failure as an unreachable page; `UpdateFailure` now
    carries the reason (`OFFLINE`, `MISSING`, `BUSY`, `UNEXPECTED`, `DOWNLOAD`) so the message matches the cause.
46. **Some PDFs report a font size of 1.** A Packt *Learning DevOps* PDF sets its fonts at size 1 and scales them
    through the text matrix, so `StyleKey.size` is 1.0 for every style in the book. Anything that derives a length
    from `size` is then ten times out: it put every character of every code listing in its own column until
    `Blocks.advanceFor` started measuring the listing instead (2026-11-03). **Heading ranking, the body-style
    choice and anything else keyed on size are untested against such a book** -- the prose read correctly, but
    nobody has checked whether its headings are found. Suspect this whenever one book misbehaves and others do
    not; the debug line to add is in that commit's history.
47. **A new entry in an actions data class defaults to doing nothing.** `LibraryActions` and `FolderActions` give
    every handler a `{}` default, so adding one and forgetting to wire it at the construction site compiles,
    renders the menu entry, and silently does nothing when tapped -- which is exactly what happened to "Make
    shelves from this folder" first time (2026-11-03). Tap the new entry on a device before believing it.

43. **Tour screenshots are real and go stale.** `feature/library/src/main/res/drawable-nodpi/tour_*.webp` are
    screenshots of an actual build at 900 px wide (about 1 MB in all), with larger copies in `docs/screenshots/`
    for the README. Retake both whenever the screens they show change; a tour that shows something the app no
    longer looks like is worse than none.

44. **The converter overflows a full-page cover image** (2026-11-03). On the reflowed title page of
    *Introduction to Python Programming* (OpenStax) the cover artwork is laid out wider than the column and is
    clipped mid-word ("Progra..."). Body pages are fine, including code panels, lists and headings; it is the
    full-bleed title graphic that is not fitted to the column. Found while capturing launch screenshots.
45. **Taps did not turn pages in the reflowed view** on `fold8_api37`, 2026-11-03: 26 taps near the right edge
    did nothing, while a drag turned the page every time. Known issue 16 says a single tap turns a page, so
    either that is wrong for Dynamic Flow books or the turn style defaults to one that needs a drag.
    **Observed, not diagnosed** -- check before trusting either reading.

## The repository is public (2026-11-03)

Most of the work is done (2026-11-03): `CLAUDE.md` carries no personal paths or private workflow, `CONTRIBUTING.md`
and `SECURITY.md` are written, issue templates are in place, the README leads with what the app does, and
`docs/plans/README.md` explains why the sign-off reports are addressed to the owner. Gitleaks and
`detect-private-key` pass over the whole tree; nothing tracked is a key, an environment file or a credential.

**The history was rewritten on 2026-11-03** (owner's decision), while the repository was still private and nobody
else had cloned it, in two `git-filter-repo` mailmap passes: the first replaced the personal address carried by 10
commits, the second folded in a second identity the owner had used on the 14 scaffolding commits of 2026-10-16.
The last two commits were squashed into one before the first pass. Every commit now reads
`Mohammed Mourad <102260074+mgmourad@users.noreply.github.com>`; no personal address appears anywhere in the
history, this file included -- do not write the old ones down again. Both passes left the tree byte-identical (same tree hash), both tags were re-pointed and force-pushed,
and both GitHub pre-releases kept their APK and `.sha256` assets.

Two things worth knowing about that rewrite:
- GitHub keeps commits orphaned by a force-push reachable by their exact SHA for a time. Nobody can reach them
  while the repository is private; GitHub Support purges them on request if that matters once it is public.
- `git-filter-repo` turns remote-tracking refs into local branches, so five stale `dependabot/*` branches now exist
  locally. The remote has only `main`. Do not `git push --all`.

The bundled audio is settled (owner, 2026-11-03): the eight ambience beds and the four page-turn `.wav`s are
royalty-free and are not attributed in the repository. Nothing further to do.

**The repository was made public on 2026-11-03**, after a final sweep: gitleaks over every commit reports no
leaks, no absolute personal paths are tracked, `android/gradle.properties` carries only build config and the
ADR-0042 version, and no commit *tree* contains a personal address either -- an earlier draft of this very section
quoted the two addresses it had just removed, which a third `--replace-text` pass redacted from the four commits
that carried them. The in-app update check works from that moment (known issue 42).

Worth doing now that it is public: branch protection on `main` is free on public repositories, so `ci-gate` can
become a required check (known issue 6), and F-Droid submission is possible whenever the owner wants it.

## Decision log

ADRs 0001–0049 in `docs/adr/`: record decisions, monorepo, licensing policy, uv, Android build
structure, minSdk 29, Hilt, CI design, on-device conversion engine (supersedes 0002 in part and 0004),
PDFium via own JNI bridge, vendored pagecurl, Phase 1 modules, reader memory budget, design language and Night
pages (0014), annotations (0016), online services (0017), on-device translation (0018, supersedes the
Translate part of 0017), EPUB pagination engine (0019), EPUB parsing (0020), island image cache (0021), riffle scrubber (0022) and its haptics and sound (0023), screenshot tests with Roborazzi (0024), the owner's recorded paper sounds (0025, supersedes the sound part of 0023), page turn sounds and per-sound volumes (0026), reading fonts (0027), app themes (0028), Rustic without texture (0029), the book's own fonts (0030), EPUB memory and island stand-ins (0031), page sounds on the media stream (0032), the motion system (0033), element entrances and edge fades (0034), edge fades blurring behind the bars only (0035), the fixed signing key for sideloaded dev builds (0036), Google Books as a second details source (0037, its Google part superseded by 0039), multi-file and folder imports with folders as book paths (0038), book details from Open Library only (0039), EPUB highlights and ribbons stored by locator (0040), chapters on their own spread and the reader's chapter laid out first (0041), release channels and versioning (0042), retiring the Python converter (0043), the conversion engine as a plain JVM module with JNA in desktop tooling only (0044, proposed for the M1+M2 sign-off; would supersede the KMP part of 0009), Dynamic Flow (0045, the owner's design), the offline Wiktionary dictionary (0046),
audio extraction with yt-dlp embedded (0047, accepted 2026-11-03: Unlicense source on MIT Chaquopy and PSF CPython,
no ffmpeg because only an address is extracted), updating from the releases page (0048, accepted), re-keying the
release identity after the signing key was lost (0049, accepted; supersedes the key-custody part of 0042), release-channel
renumbering (0050, accepted 2026-11-07: 3–4 alpha, 5–6 canary, 7 beta), and offline neural TTS via `sherpa-onnx` with
Kokoro and Piper voice packs (0051, accepted 2026-11-08).
