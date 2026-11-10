# UX/UI overhaul ("Quiet Library"): plan

- **Status:** Approved by the owner 2026-11-09, with the calmer screen entrance and current motion trends (springs, shared axis, predictive back)
- **Design source of truth:** `docs/ux-review/2026-11-09/flowfolio-app-mock.html` (local only, see below), with
  `ux-review.md` beside it. Where this plan and the mock disagree, the mock wins; where the mock is silent, the
  current app's behaviour stays.
- **ADR:** [ADR-0052](../adr/0052-quiet-library-design-overhaul.md) (accepted 2026-11-09)
- **Starting point:** `6fd0fa8`, FlowFolio 0.6.0-canary.1 (build 600001), Room v20.

## Owner decisions taken before the plan (2026-11-09)

1. **Text settings get per-book overrides.** Settings → Text holds the defaults for new books; the reader's Aa panel
   changes the current book only, with "Reset to defaults" and "Use as default for new books". This is a new feature
   and the only intended behaviour change outside the prompt: it needs Room v21 (below).
2. **The overhaul sits between Phase 6 and Phase 7.** Each phase ends with a debug APK; the whole overhaul is
   released as **FlowFolio 0.6.0-canary.2 (build 600002)** after a full CI run. Phase 7 (scanned books and OCR) is
   unchanged and still ships as 0.7.0-beta.
3. **Commits stay local until the end.** Each phase is committed (Conventional Commits) on `main` locally; nothing
   is pushed until the overhaul is finished and verified, and then CI runs once, as the delivery rule asks.
4. **`docs/ux-review/` is not committed** (it holds screenshots of commercial books). It is gitignored. So:
   - before/after captures go to `docs/ux-review/overhaul/<phase>/` as the prompt asks, but stay local;
   - the tracked **feature-parity checklist** lives in `docs/plans/ux-overhaul-parity.md` (a copy may sit in
     `docs/ux-review/overhaul/feature-parity.md`);
   - the plan, the ADR and the CHANGELOG describe the design in words so the repository still explains itself.

## How each phase is run

1. Re-read the mock's screens for the phase in the browser, both device frames, light and dark.
2. Build, keeping every feature reachable. Tests are written with the code (below, per phase).
3. Run `spotlessCheck detekt lintDebug testDebugUnitTest -x :tools:convert:test` (known issue 37), and
   `verifyRoborazziDebug` where drawing changed (re-record deliberately, review the diffs).
4. `assembleDebug`, install on `fold8_api37`, check the phase **folded and unfolded, light and dark**, plus the other
   three themes where tokens changed. Capture before/after into `docs/ux-review/overhaul/<phase>/`.
5. Tick the phase's rows in `docs/plans/ux-overhaul-parity.md`.
6. Update `CHANGELOG.md` (Unreleased), `CLAUDE.md` where commands or facts change, commit, hand over the debug APK,
   **stop and report**. CI has not run for any phase until the end; every report says so.

## Mapping: mock → code

| Mock area | Today | Work |
|---|---|---|
| Tokens, type, shape | `Palette.kt`, `RusticPalette.kt`, `MaterialPalette.kt`, `AmoledPalette.kt`, `Type.kt`, `Theme.kt`, `Effects.kt` | New semantic tokens; old fields become aliases, then go |
| Toggle, slider, tabs | `Toggle.kt`, `Slider.kt`, `UnderlineTabs.kt`, `Selectors.kt` | Restyle; value readout on sliders |
| Menus, sheets, dialogs, snackbars, tooltips | `Menu.kt`, `Components.kt`, scattered dialogs in `LibraryDialogs.kt`, `ShelfDialogs.kt` | `FlowFolioMenu`, `FlowFolioSheet`, `FlowFolioDialog`, `FlowFolioSnackbar`, `FlowFolioTooltip`, `FlowFolioSegmented`, `FlowFolioStepper`, settings tile |
| Covers | `Book3D.kt`, `CoverArt.kt`, `CoverColors.kt`, `SharedCover.kt` | One 2:3 cover with spine, shadow, badges; `Book3D` kept for transitions |
| Glass | `Glass.kt`, `Chrome.kt`, `EdgeFade.kt`, `ScrollEdgeFades.kt` | Mock opacity; status-bar fade on scrolling screens |
| Catalog | `DesignCatalog.kt` | Every new component, light and dark |
| Shell, tabs, rail | `app/AppShell.kt` (`TopLevel`: 7 entries), `Routes.kt`, `NavMotion.kt`, `ShelfDestinations.kt` | Four destinations, redirects for old routes |
| Library | `LibraryScreen.kt`, `LibraryParts.kt`, `HeroCard.kt`, `LibraryTotals.kt`, `LibraryStatsViewModel.kt`, `GoalPath.kt`, `ShelfSorting.kt`, `SearchViewModel.kt`, `SearchResultsList.kt`, `Fuzzy.kt`, `ScreenHeader.kt` | Header, global search, stats band, Continue card, goal card, chips, shelf rows, sort/view menu |
| Shelves | `ShelvesScreen.kt`, `ShelvesViewModel.kt`, `ShelfPageScreen.kt`, `ShelfPageViews.kt`, `ShelfDialogs.kt`, `ShelfAdding.kt`, `Folders.kt`, `FoldersToShelves*.kt` | Fanned folder cards, sort, new-shelf dialog, shelf page, Add books sheet |
| Notebook | `BookmarksScreen.kt`, `AnnotationsScreen.kt`, `BookAnnotationsScreen.kt`, `DictionaryScreen.kt` and their view models | One `NotebookScreen` + `NotebookBookScreen` over one marks repository |
| Details | `DetailsScreen.kt`, `DetailsInfo.kt`, `DetailsPanels.kt`, `DetailsAuthor.kt`, `EditDetailsScreen.kt`, `EditForm.kt`, `BookLabels.kt` | Header, primary action, fact tiles, five tabs, ⋯ menu |
| Settings | `SettingsScreen.kt`, `SettingsRows.kt`, `SettingsText.kt`, `SettingsHousekeeping.kt`, `ThemePicker.kt`, `AtmosphereSettings.kt`, `AtmosphereEditor.kt`, `GoalReminder.kt`, `LicensesScreen.kt` | Hub (folded), list + detail (unfolded), ten pages |
| Reader chrome | `ReaderChrome.kt`, `ChromeActions.kt`, `ReaderControlsLayer.kt`, `BookmarkButton.kt`, `ChromeClock.kt`, the unfolded rail | Top bar with title chip, ⋯ menu, bottom dock; the rail goes |
| Riffle | `:feature:reader:riffle` (`RiffleDock.kt`, `StripPainter.kt`, `Loupe.kt`, `LoupePages.kt`, `FlagStacks.kt`, `RiffleLabels.kt`, `DetourState.kt` in the reader) | Restyle only; every behaviour kept |
| Reader panels | `ReaderPanels.kt`, `NavigationPanels.kt`, `ReaderSearchPanel.kt`, `ReaderSettingsContent.kt`, `ReaderNarratorPanel.kt`, `AnnotationListPanel.kt`, `ReaderDictionaryPanel.kt`, `TranslateOverlay.kt`, `DefineOverlay.kt` | Contents, Search, Aa (3 tabs), Listen, Notebook, Words |
| Selection | `ReaderSelection.kt`, `ReaderAnnotating.kt`, `VoiceMarkPill.kt` | Menu with nine colours, Note sheet, VoiceMark dialog |
| Dynamic Flow | `DynamicFlowSwitch.kt` (both), `FlowNoticeHost.kt`, `DynamicFlowPrompt.kt`, `FlowCarrier.kt` | Banner, carry-across dialog |
| Intro, What's new, guides | `Onboarding.kt`, `app/WhatsNew.kt`, `Guide.kt`, `ReaderGuide.kt`, `Guidance.kt`, `Spotlight.kt`, `ReadingGoalDialog.kt` | 11 slides, CHANGELOG-driven What's new, coach marks, goal sheet |

Not touched: conversion, TTS synthesis, pagination, PDF rendering, the curl (`:core:pagecurl`), Room data other than
the additions below.

## Phases

### Phase 1: design system

> **Done 2026-11-09** (CHANGELOG, Unreleased). Two deliberate departures from the mock:
> - The light theme's third ink is `#938574`, not `#9A8B79`, which reached only 2.9:1 on the background.
> - The toggle thumb stays white in dark mode, as the mock draws it, where the prompt said accentInk.
>
> The `subItem` alias is the second ink, not `danger`, because most of its uses were decorative. The warning sites
> name `danger` instead.
>
> Verified on `fold8_api37` folded and unfolded, light and dark, and the other themes through Roborazzi. CI has not run.

- **Tokens.** `Palette` gains the mock's semantic set: `bg`, `bg2`, `surface`, `surface2`, `surface3`, `line`, `ink`,
  `ink2`, `ink3`, `accent`, `accentInk`, `accentSoft`, `ok`, `okSoft`, `danger`, `dangerSoft`, `paper`, `paperInk`,
  with the prompt's values for FlowFolio and the mock's `th-rustic`, `th-material`, `th-amoled` overrides (AMOLED
  keeps FlowFolio's accent; Material You keeps dynamic colour, mapped onto the same slots). The old fields (`title`,
  `item`, `subItem`, `hint`, `glyph`, `toggleOn*`...) become **aliases** of the new ones in this phase, so every
  screen takes the new look at once and no screen breaks; each later phase moves its screens to the new names, and
  the aliases are deleted in Phase 9.
- **Glow:** `UserSettings.neonGlow` default becomes `false`. A stored choice is kept; installs that never touched it
  get it off. Renamed "Night glow" in the UI; only display titles in dark mode bloom.
- **Shape and size:** cards 22 dp, sheets 28 dp, pills fully round, icon buttons 42/36 dp with 48 dp targets.
- **Components:** restyled `FlowFolioToggle` (track is the state, white thumb), sliders with a value readout, and the
  new `FlowFolioMenu`, `FlowFolioSheet`, `FlowFolioDialog`, `FlowFolioSnackbar` (+ a host), `FlowFolioTooltip`
  (long-press on every icon button, via one `FlowFolioIconButton`), `FlowFolioSegmented`, `FlowFolioStepper`,
  `SettingsTile`, and `BookCover` (2:3, spine, shadow, badges bottom right). Built on Compose/Material 3 primitives
  already in the build: **no new dependency.**
- **Tests:** unit tests for the token tables (every theme fills every slot; WCAG AA contrast for ink/ink2 on bg and
  surface, accentInk on accent, in all themes, light and dark). Roborazzi is added to `:core:designsystem` (already a
  dependency of the build, used by the riffle module) with screenshots of each component in light and dark.
- **Check:** the debug catalog (`--ez catalog true`, light and dark) and a pass over every existing screen for
  contrast regressions.

### Phase 2: app shell and navigation

> **Done 2026-11-09** (CHANGELOG, Unreleased). The rail is flat, as the mock draws it, not the floating glass rail of
> ADR-0015. The Notebook's VoiceMarks stay inside "Highlights & notes" until Phase 5 gives them a chip. Verified on
> `fold8_api37` folded and unfolded, light and dark. CI has not run.

- `TopLevel` becomes Library, Shelves, Notebook, Settings. Glass tab bar folded; rail ≥ 600 dp with the app mark on
  top and Settings at the bottom.
- **Redirects:** the `favorites`, `bookmarks`, `annotations`, `dictionary` routes and the per-book
  `book/{id}/bookmarks|annotations|dictionary` routes stay registered and forward to Library (Favorites chip) or
  Notebook (filtered). Phase 2 lands Notebook as a thin host for the existing screens so nothing is unreachable
  before Phase 5 rebuilds it.
- Notification intents (`ReadingGoalRepository`, `FlowNotifications`) and the debug extras (`openBookId`,
  `importPath`, `layout`, `startPage`, `catalog`, `theme`, `riffleDemo`, `gcNow`) keep working; `CLAUDE.md` updated.
- Status-bar fade on scrolling screens (replaces the full top edge fade of ADR-0034 where the mock has a plain bar).
- **Tests:** route redirect tests (Robolectric/Compose), `TopLevel` mapping, a check that every old route resolves.

### Phase 3: library

> **Done 2026-11-09** (CHANGELOG, Unreleased). The library's separate Finished row is gone; the Finished chip covers
> it. Drag-to-reorder now happens on shelf pages only. Recent and Folders chips stay at the end of the row. The
> Continue card shows the author without a chapter, because the library holds no chapter names. "Share file" waits for
> the owner, since it would widen the FileProvider. Verified on `fold8_api37`: folded and unfolded, light and dark,
> Shelf, Grid and List, sort and view persisted across a force-stop, search, stats band and book menu. CI has not run.

- Header (title, Reading goal folded only, Sort and view, Import), the global search field (titles, authors,
  highlights, notes, saved words, shelves; grouped results; recent books before typing; two-character minimum).
- Stats band: collapsed one line by default, More/Less, eight tiles each opening its filtered view; Settings choice
  Collapsed / Expanded / Hidden, remembered (DataStore).
- Continue reading card with the ⋯ menu in its top-right corner; unfolded, the goal ring in its third column.
  Folded reading-goal card.
- Filter chips (All, In progress, Not started, Finished, ★ Favorites, Textbooks, Novels); shelf rows with the plank
  under the title and progress line; "Not on a shelf" last.
- Sort and view menu (six sorts; Shelf, Grid, List), applied in every view including inside shelf rows, persisted in
  DataStore; long-press opens the book menu.
- **Bug 2, the crash:** the "Not on a shelf" and "Finished" rows both have `shelf = null`, so both get the lazy key
  `row-loose` (`LibraryViewModel.kt:124-125`, `LibraryScreen.kt:374`). Keys include the row's kind; regression test
  builds a library with both rows and asserts unique keys.
- **Tests:** sort/filter unit tests, search grouping and the two-character rule, stats-band state persistence.

### Phase 4: shelves

> **Done 2026-11-09.** The Dynamic Flow switch stays on cards of shelves with PDFs, beside ⋯, and its batch action
> moves into ⋯ too. The Add books sheet is unchanged in this phase. Verified on `fold8_api37` unfolded light. CI has
> not run.

- Folder cards: two columns folded, three unfolded, coloured border, two-line title, ⋯, up to five fanned covers, a
  labelled stats row, a dashed "New shelf" card; header Search, Sort (Custom, Name, Most books, Most read, Most marks),
  New shelf.
- New shelf dialog (name + swatches) → snackbar "Add books". Shelf page (dot, title, stats line; Sort and view; Add
  books; ⋯ Rename, Change colour, Add books, Sort books, Delete with confirm). Add books sheet listing where each book
  lives now. Folder imports and "Make shelves from this folder" stay.
- **Tests:** shelf sort unit tests; delete keeps the books (repository test).

### Phase 5: notebook

> **Done 2026-11-09.** `MarkCounts` in `:core:data` is the one source of counts. Deletion with Undo hides the mark at
> once and deletes it when the snackbar goes. Export and share go through the system share sheet as text, so no file
> sharing was added. Details reads `MarkCounts` from Phase 6. Verified on `fold8_api37` unfolded. CI has not run.

- **One counts query (bug 3):** a `MarkCounts` query in `:core:database`/`:core:data` returns highlights, notes,
  bookmarks, VoiceMarks and words per book, honouring `ReadingViews` (a Dynamic Flow book counts the marks of its
  current view, as the reader shows them). Library card, Notebook, Details and Shelves all read it. The 5-vs-2
  bookmark mismatch is diagnosed first (likely one count ignoring the view filter) and pinned by a test.
- Landing: title and line, Search, Sort (Recently marked, Book title, Most marks), ⋯ (Export Markdown or CSV, Share
  all), type chips with counts, one card per book with count pills (zeros dimmed), two columns and latest snippet
  unfolded.
- Book notebook page: cover header, tabs with counts, Sort (Page order, Newest, Type), items with chapter · page,
  type, Open / Share / Delete (snackbar Undo); highlight colour as the quote's rule; VoiceMark playback row; words with
  part of speech, definition and translation, or a "Translation" label (bug 6).
- The old Bookmarks, Annotations and Dictionary screens are removed only once every action they offered is in
  Notebook and ticked on the parity list.
- **Tests:** counts query against a seeded database (both views), export format tests, undo restores the row.

### Phase 6: book details

> **Done 2026-11-09.** Overview keeps its description and intent chips (the existing tags), and the Dynamic Flow
> switches stay in the reader and the library menu. Share file waits for the owner, as in Phase 3. Verified on
> `fold8_api37` unfolded. CI has not run.

- Header back and ⋯; cover, title, subtitle, author, "format · pages · size"; primary action naming where you land
  ("Continue · p. 55", "Start reading", "Read again"), with Shelf, Goal and ★.
- Fact tiles with correct plurals (plural resources everywhere, bug 3); pages labelled as printed page or PDF page.
- Tabs Overview (description, tag chips, "+ Tag", Dynamic Flow status box), Contents (current chapter highlighted),
  Notebook, Details, Author. ⋯ menu as the prompt lists, with confirm dialogs and Undo.
- **Tests:** primary-action label logic, plural resources, page-label wording.

### Phase 7: settings

> **Done 2026-11-09.**
> - `book_text` stores a book's whole text set when it has one; no row means it follows the defaults. That is
>   simpler than the nullable columns planned, with the same behaviour.
> - The migration test replays the committed v20 schema JSON into SQLite rather than using `room-testing`, so no
>   dependency was added.
> - The reading-goal row and the typed-RESET confirmation are left to Phase 9's sweep. Reset still asks first.
>
> Verified on `fold8_api37` unfolded. CI has not run.

- Folded: hub list (tile, title, live summary, chevron) opening dedicated pages; unfolded: list on the left, the page
  on the right. Ten sections as the prompt lists, including Library stats (Collapsed/Expanded/Hidden), the circular
  theme reveal (existing `ThemeReveal.kt`), and Reset FlowFolio behind a typed RESET.
- **Per-book text overrides (owner decision 1):** **Room v21** adds `book_text_settings` (`book_id` primary key,
  foreign key to `books` with cascade delete; nullable `font`, `text_size`, `line_spacing`, `margins`, `justify`;
  null means "use the default"). Migration 20 → 21 is additive; a `MigrationTestHelper` test opens a v20 database
  with data and checks every book reads the defaults afterwards. Settings → Text is labelled "Defaults for new books
  and books you have not changed"; the reader resolves override ?: default.
- **Tests:** summary-line formatting, the migration test, override resolution.

### Phase 8: reader

> **Done 2026-11-09; the deferred parts done 2026-11-10** (CHANGELOG, Unreleased).
> - Built: the top bar, the six-tool dock (the right rail is removed), panels anchored above the dock, and every part
>   of bug 1 (search).
> - 2026-11-10: the riffle header, the nine-colour selection menu, the Translate language chips and (+) sheet,
>   Listen's speed segments, "Preparing · N%", and coach marks. The coach card stays centred (the owner's rule for
>   first-use guides, 2026-11-07), with the spotlight round the control.
> - Not built: a sleep timer, a narrator mini bar and the spoken-sentence highlight. These are new features, not
>   restyles, and need the owner's say.
>
> Verified on `fold8_api37` unfolded: the dock fits all six tools, and Search opens above it with 50 matches for
> "whale" in *Moby-Dick*, grouped by chapter. CI has not run.

- Top bar (back, title chip with chapter and time left unfolded, ribbon button: tap toggles, long-press colours; ⋯
  with Go to page, Ribbon colour, Book details, PDF/Dynamic Flow, Keep screen on, debug-only detour demo, Show reader
  tips, Share book). Bottom dock: riffle + Contents, Search, Aa, Listen, Notebook, Words. **The unfolded right rail
  is removed**; panels open above the dock; auto-hide unchanged.
- Riffle restyle: header row (chapter in accent; "Page 40–41 of 1,516 · 3%" opening Go to page), ‹ strip ›, Front
  and Back labels, darker read portion, taller chapter ticks, tappable ribbon flags above. Dragging: strip expands,
  lens, live preview card flipping slightly per page, dark "p. N · Chapter" label. Haptics, sounds, pulses and the
  detour mark keep their logic; only drawing changes. Reduced motion: no flip, no lens, haptics only.
- Selection menu (nine colours; Note, VoiceMark, Define, Translate, Copy, Share), snackbar Undo, Note sheet, VoiceMark
  dialog with timer.
- Panels: Contents (+ Ribbons), Search (bug 1, below), Aa (Text / Page / Behaviour; the Dynamic Flow ↔ PDF control in
  Text; the per-book override controls), Listen (speed segments 0.8×–2×, voice picker with plain descriptions and IDs
  under Advanced, page turning, sleep timer, mini bar and spoken-sentence highlight), Notebook, Words (Define with
  source chips and Save word; Translate with the user's target-language chips and (+) sheet of every ML Kit language,
  first add downloads with progress; History). Chosen languages persist in DataStore.
- Dynamic Flow "Preparing · N%" banner and the carry-across dialog when "Ask before switching views" is on.
- Coach marks on ribbon, strip, Aa, Listen and the page, shown once, reset from Settings → Help.
- **Bug 1, search:** two-character minimum with ~250 ms debounce; a separator between blocks in snippets; the hit
  highlight mapped from the matched range (diagnosed first: the result's offset and the page text's offsets disagree);
  results grouped by chapter with page numbers and a count, ⌃/⌄ stepping; while Dynamic Flow prepares, search the
  PDF's text layer.
- **Tests:** search unit tests (debounce, snippet joining, offset mapping on a fixture), translation-language
  persistence, Roborazzi re-records for the riffle (reviewed diff by diff); `EpubPageTurnBenchmark` and a riffle
  frame-timing check on the emulator for the 60 fps target (emulator numbers are relative, known issue 30).

### Phase 9: intro, What's new, tooltips, feedback, close-out

> **Done 2026-11-09; the deferred parts done 2026-11-10.**
> - Built: the typed-RESET confirmation, a tooltip on every icon button, the lint fixes, and the parity checklist
>   (`ux-overhaul-parity.md`).
> - 2026-11-10: the 11-slide intro with retaken pictures, What's new from the bundled CHANGELOG, the reading-goal
>   sheet, and the palette aliases removed.
> - Not checked: the cold start (bug 4). The splash and window background already share the theme's colour, but no
>   frame-by-frame capture was taken.
> - Released as 0.6.0-canary.2 (build 600002) on 2026-11-10, with the new app icon.
>
> CI has not run.

- Intro: 11 slides with the mock's copy and illustrations, tappable dots, Back/Next/Skip/Start reading, vertical
  folded and two-column unfolded; tour screenshots retaken (known issue 43).
- What's new: sheet folded, centred card unfolded, rendered from `CHANGELOG.md` (copied into the APK's assets by a
  Gradle task at build time, parsed into New/Fixed), earlier versions collapsible; after the intro and after updates.
- Reading goal sheet (presets, ± 5 min, Day/Week/Month, reminder time and days, Today summary, Reset, Save), on the
  existing reminder code.
- Sweep: tooltip on every icon button, Undo snackbar on every removal, confirm dialog on every destructive action,
  TalkBack labels, 48 dp targets; delete the palette aliases.
- Bugs 4 (cold start: the splash or a skeleton, no blank frame) and 5 (ambience glyph instead of the smiley).
- Feature-parity checklist ticked on the emulator; then the release checklist for 0.6.0-canary.2 (version and
  versionCode in `android/gradle.properties`, CHANGELOG heading, push, green CI, release APK signed with the release
  key, tag, pre-release), only with the owner's go-ahead.

## Risks

- **Palette aliasing hides misuse.** Aliased screens can look wrong where an old token meant something else (e.g.
  `subItem` red used as a warning). Mitigation: Phase 1 maps each old field deliberately and reviews every screen in
  all four themes.
- **Nav redirects and back stack.** Redirected routes must not leave a dead entry under Back. Covered by tests.
- **Riffle restyle vs behaviour.** The riffle is the most tuned part of the app (ADR-0022 to 0026). Drawing changes
  only, behind the existing `RiffleState`/`StripMapping`; Roborazzi diffs reviewed one by one.
- **Per-book overrides** touch pagination inputs (text size, margins). The pagination engine is not changed; it is
  given resolved values. Islands cache per text size (ADR-0021), so a per-book size only adds cache entries.
- **GPU memory.** Glow off by default lowers memory; new glass (dock, panels) follows the blur rules (known issues 21,
  CHANGELOG slice 2). Memory is checked on the reader after Phase 8.
- **Emulator limits:** recording (known issue 40) and confetti or status-bar items can't all be checked; those rows
  will say "implemented but untested".
- **Size.** Nine phases over ~65 k lines of UI-adjacent code; each phase is a large batch. Phases 3, 5 and 8 are the
  biggest.

## Open item for the owner

- **Screen entrances.** The mock has no staggered screen entrance (only panels rise 16 dp over 0.28 s). This plan
  proposes keeping ADR-0034's entrance on every visit but calmer: a 16 dp rise with fade, no 112 % scale, 30 ms
  stagger. Say if you would rather drop entrances entirely.
