# Tightening plan: TM1 and TM2

- **Status:** Approved by the owner 2026-11-12; TM1 released as 0.7.0-beta.3 and TM2 as 0.7.0-beta.4 (both
  2026-11-12). TM2 was released on the owner's instruction without the final test pass; see the CHANGELOG. Both signed
  off with the project, 2026-11-12 (`project-signoff.md`).
- **Where TM1 differs from this plan:**
  - Night glow is removed as a setting, but the design system keeps its glow-capable palette (always off). Removing
    the fields would touch every component for nothing visible.
  - The scan notice is decided in `DynamicFlow.convert`, not only in the reader, so the library's and shelves' Dynamic
    Flow switches ask too.
- **What these are:** tightening milestones, an unplanned phase taken as discovered, after Phase 7. They are not
  feature phases: they harden what exists. Two milestones, each ending with a polish check, a debug APK on the phone and
  the emulator, and a beta release (ADR-0042/0050: **0.7.0-beta.3** after TM1, **0.7.0-beta.4** after TM2).
- **Owner decisions (2026-11-12):**
  1. Reader chrome goes dark glass on every paper, departing from the Quiet Library mock (an ADR records it).
  2. A regular PDF pauses a running OCR conversion rather than converting alongside it.
  3. APK size: one APK per processor type, plus downloadable extras offered in **their own first-launch popup**,
     separate from the tour (most people skip the tour).
  4. A book deleted from a synced folder stays in the library; nothing is deleted automatically.
  5. Widgets are the usual Android widgets; no Fold cover-screen widgets.
- **Rules that hold throughout:**
  - Every change is verified on `fold8_api37`, folded and unfolded, with screenshots for anything visual.
  - The Fold itself is used where the emulator cannot answer (speed).
  - Local checks run every time (`spotlessCheck detekt lintDebug testDebugUnitTest`, the PDF corpus and the OCR gate).
  - CI runs before each release.
  - Each new dependency is brought to the owner first.

## TM1: the reading surface and conversion

The things you touch while reading, and the conversion queue. Mostly in `:feature:reader`, `:feature:library` and
`core/data/flow`.

### 1. Quick fixes
- **Shelves:** the folder card's height fits its content (fanned covers, name, count, ⋯) rather than a fixed tall
  square, in both fold states.
- **Settings → Appearance:** the Library stats band stops wrapping when folded.
- **Night glow is removed.** Since the Quiet Library overhaul it lights nothing: one shelf pill reads a glow that is
  "none".
  - Removed: the switch, `UserSettings.neonGlow`, and the palette's glow fields.
  - A stored value is simply ignored.
- **Keyboard search, everywhere** (library search, global search, shelf search, the reader's in-book search, the
  dictionary):
  - the keyboard's Search/Enter key runs the search and hides the keyboard (`ImeAction.Search`);
  - one shared search field, so new search bars behave the same.

### 2. Gestures
- **Swipe between tabs wherever there are tabs:** Notebook, Details, Words (Define/Translate/History), Contents
  (Contents/Ribbons), Aa (Text/Page/Behaviour), the Settings pages that have tabs.
  - Done as one shared `FlowFolioTabs` + pager component, as book details does now, so the next tabbed screen gets
    it free.
- **Swipe down to dismiss** the riffle bar and every reader panel (Aa, Search, Contents, Narrator, Words, Go to
  page...).
  - The panel follows the finger and closes past a threshold, else springs back.
  - A panel whose list is scrolled dismisses only when the list is at its top, or from the panel's header, so
    scrolling results up never closes it by accident.

### 3. Dark reader chrome (ADR-0056)
- The top bar, the dock and its riffle, every reader panel, and the selection menu become **dark glass on every
  paper** (light, sepia, dark):
  - a stronger scrim under the blur;
  - text and icons from the dark tokens.
- **Check:** every text and icon against the three papers at WCAG AA (4.5:1 for text, 3:1 for icons), measured
  from screenshots.
- ADR-0056 records the departure from the mock.

### 4. Conversion: disclaimer, progress, priority
- **Measure first:** convert a 50-page English scan and a 50-page Arabic scan in the release build on the Fold
  (known issues 22, 30 and 55).
  - The numbers feed the estimate below and the Phase 7 sign-off.
  - If either is far over 2 s a page, the fix (batching recognition, threads, `ocrScale`) joins this milestone.
- **Before a scanned book converts, a disclaimer** says that:
  - the text may not be exactly right;
  - it will take about N minutes for this book (from the page count and the measured speed);
  - the reader can keep reading the original or read another book;
  - a notification arrives when it is done.
  - "Convert" and "Not now", plus "Don't show this again" for all books. A book is known to be scanned from its
    first pages, read before converting.
- **Live progress on the book's cover** in the library, shelves and Continue card while it converts: a thin bar
  along the bottom of the cover, from the progress `DynamicFlow` already broadcasts.
- **Priority instead of a queue:** a regular PDF asked for while a scanned book converts no longer waits.
  - The OCR job pauses at its next page boundary, the regular book converts, then OCR resumes.
  - Only one conversion holds PDFium and the memory at any time.
- **Resume:** the paused OCR job, and one Android stopped, carry on from the last finished page instead of starting
  over.
  - Finished pages' OCR results are kept beside the bundle and checked against the engine version.
  - This also removes the risk that a long scan never finishes inside Android's 6-hour daily limit for this kind of
    service.

### 5. Updates that know the processor
- The in-app updater picks the release asset matching the phone's processor (`Build.SUPPORTED_ABIS`), and falls
  back to the universal APK.
- It ships in beta.3 so that every phone can pick correctly before TM2 publishes per-processor APKs.

**TM1 is done when:**
- every item above is verified on the emulator in both fold states;
- the Fold timings are recorded;
- 0.7.0-beta.3 is released with green CI.

## TM2: the library, the app's size, and widgets

### 6. Folder sync
- **A folder read in place** picks up books added to it whenever the library opens, and on pull-to-refresh.
- **A folder copied into the app** keeps its permission to the source folder; **pull-to-refresh** on Library and on
  Shelves rescans every imported folder and copies in what is new.
- **New books go to the shelf made from their folder** ("Make shelves from this folder"), nested folders included.
- **Matching is by content** (the book's hash, as today), so a renamed file is not imported twice.
- A file deleted from the source folder leaves its book in the library (owner's decision 4).
- A snackbar reports the result: "3 new books from Career Books".

### 7. A smaller app (ADR-0057)
- **One APK per processor type** in each release: arm64-v8a (almost every phone, the Fold included), armeabi-v7a
  (old phones), and x86_64 for emulators.
  - The universal APK stays as well, under the old file name and listed first, so phones still on beta.2's updater
    keep working.
  - Expected arm64 size: about 150 MB instead of 357 MB.
  - ADR-0042's release checklist gains the extra files and their `.sha256`.
- **WordNet becomes a download** (19 MB), like the voice and OCR packs.
  - The offline Wiktionary and the online sources keep working without it.
- **The first-launch resources popup**, its own window, shown once after the first launch whether or not the tour
  was watched. It presents the optional downloads, each with its size and a toggle:
  - the scanned-books pack;
  - Narrator voices;
  - the WordNet dictionary.
  - Design: animated feature cards with real screenshots; "Download selected" and "Later". It stays reachable from
    Settings → Storage.
  - Downloads run in the background with the existing progress handling.
- No native code is downloaded after install: what runs is what the signed APK carried (decision 3).

### 8. A demo book
- One short, freely licensed book preloaded: a 20–40 page OpenStax chapter (CC BY) with headings, a figure, a table
  and an equation, so Dynamic Flow has something to show.
  - Its licence goes on the licences screen.
  - It starts Phase 5's first-book walkthrough.
- **About 2–5 MB** back on the APK. The owner approves the chosen chapter before it is added.

### 9. Widgets
- Three widgets with **Jetpack Glance**, a new dependency brought to the owner first (licence, release date,
  alternatives):
  - **Continue reading:** the cover, title, progress, and a tap to open at the page.
  - **Reading goal:** today's progress ring and the streak.
  - **Narrator:** the book and chapter, play/pause, skip back/forward (Settings' Skip/Reverse behaviour) and speed.
    - It drives the existing media session while narration plays.
    - Play when nothing is playing opens the book and starts narrating, since narration lives in the reader
      (ADR-0053).
- Each widget comes in a small and a medium size and follows the app's theme.

### 10. The tour, last
- The tour's screenshots are chosen by **screen shape**: phone-shaped screens see folded pictures, wide screens see
  unfolded ones.
- One slide shows both: "Folded or open, FlowFolio fits".
- All pictures are retaken after TM1 and TM2's visual changes, under known issue 43's rules (no status bar).

**TM2 is done when:**
- every item above is verified on the emulator in both fold states;
- the per-processor APKs are proved to update in place over beta.3;
- 0.7.0-beta.4 is released with green CI.

## Risks and how they are handled

- **Resuming OCR mid-book** touches the converter's flow (`Converter.convertProgressively`). It is limited to
  scanned pages, whose OCR result is self-contained per page, and covered by a test that kills and resumes a
  conversion.
- **Per-processor APKs and the updater:** the updater ships one release early (TM1); the universal APK stays for
  the transition.
- **Swipe-to-dismiss inside scrolling panels** is where gestures usually go wrong; each panel is checked by hand on
  the emulator, folded and unfolded.
- **Dark chrome** changes the look of every reader screenshot, which is why the tour pictures come last.
