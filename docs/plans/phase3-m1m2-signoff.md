# Phase 3 (converter v1 on the phone): sign-off report

- **Signed off by the owner 2026-10-28**; released as FlowFolio 0.3.0-alpha.1 (build 300001).
- **Date:** 2026-10-27, M3 added 2026-10-28. **For:** the product owner's sign-off of Phase 3: M1 (engine and
  extraction) and M2 (from structure to EPUB), built together as agreed, and M3, built as your Dynamic Flow design
  (ADR-0045; section "M3" below). Nothing is committed until the sign-off; the release follows it.
- **Plan:** `docs/plans/phase3-plan.md`. **Spec:** `docs/conversion-spec.md` v1.0.0, `docs/schemas/flowfolio.schema.json`.
- **Decisions:** ADR-0042 (release channels), ADR-0043 (Python server retired), ADR-0044 (a plain JVM engine, JNA in
  desktop tooling; **proposed**, for your acceptance with this sign-off).

## What you can try

On the `fold8_api37` emulator the library now holds **27 books** (the 6 from Phase 2, the 9 OpenStax PDFs, 7 Project
Gutenberg EPUBs, the IDPF samples and test EPUBs), with two new shelves: **Textbooks** (U.S. History, University
Physics, Psychology 2e, Principles of Economics 3e) and **Classics** (The Waste Land, Sherlock Holmes, Pride and
Prejudice, On the Origin of Species, Great Expectations).

It also holds **University Physics as converted on the phone**: a 40 MB EPUB. The library has three
copies of it from successive converter builds (the newest is the one the checks below used); the older two can be
deleted.

Since M3, any PDF can be read in **Dynamic Flow**: the "Aa" sheet's switch, a book's press-and-hold menu or the list's
swipe, or Settings > Dynamic Flow for every PDF you open. Physics, Psychology 2e, Principles of Economics 3e and
U.S. History have been converted on the emulator.

## Done-when checks

| Milestone check | Result |
|---|---|
| M1: the textbook's ~1,000 pages extract on the JVM | **Tested.** 959 pages, 32 s on the PC (whole conversion). |
| M1: ... and on the Fold | **Tested on the `fold8_api37` emulator**, not yet on your Fold: 959 pages convert in 58.0 s (reading 24.0 s, layout 2.8 s, images 30.0 s, writing 1.2 s); peak 87 MB Java heap, 132 MB native. The Fold run comes with M3's job. |
| M1: the IR of a sample page checked by hand against the PDF | **Done**, and more: 30 hand-labelled pages (below), plus page 59 of University Physics traced stage by stage. |
| M1: CI builds and tests the engine | **Implemented, not yet run in CI** (nothing pushed): `testDebugUnitTest` runs `:core:conversion` and `:tools:convert` tests, which fetch PDFium's Linux build. The local run of the full CI command is green (see "Checks"). |
| M2: every corpus book converts to an EPUB with no EPUBCheck errors | **Tested**: 9 of 9 books, **0 errors and 0 warnings** (EPUBCheck 5.4.0). The robustness PDFs too, and the EPUB the phone made (WebP images). |
| M2: ... that opens in the Phase 2 reader with its chapters and printed page numbers | **Tested** on the emulator, folded and unfolded: 29 chapters; the footer shows the printed page numbers ("59-60", "32"), from the PDF's page list. |
| M2: the metrics have baselines | **Done**: `samples/golden/baselines/` (9 books) and `samples/golden/labels/` (30 pages). |

## The corpus metrics

7,039 pages in 9 OpenStax textbooks (licences in `docs/licenses.md`). Coverage is the share of text pages whose text
was all found (exit criterion: at least 0.95 at 0.98); "outline" is the share of outline entries with a matching heading
on their page; "labels" are the hand-labelled heading and reading-order scores.

| Book | Pages | Coverage | Fallback pages | Chapters | Outline | Headings | Figures | Equations | EPUBCheck | Labels | s/page (PC) | EPUB MB (PNG) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| calculus-volume-1 | 769 | 1.0000 | 0 | 14 (outline) | 1.000 | 1344 | 1012 | 2874 | 0/0 | 1.00 / 0.88 | 0.045 | 77 |
| high-school-statistics | 871 | 1.0000 | 0 | 25 (outline) | 0.971 | 2717 | 869 | 1267 | 0/0 | 1.00 / 0.95 | 0.021 | 52 |
| introduction-intellectual-property | 201 | 1.0000 | 0 | 11 (outline) | 1.000 | 356 | 73 | 72 | 0/0 | 1.00 / 1.00 | 0.025 | 45 |
| introductory-business-statistics-2e | 627 | 1.0000 | 0 | 19 (outline) | 0.969 | 1811 | 627 | 975 | 0/0 | 1.00 / 1.00 | 0.026 | 40 |
| physics | 875 | 1.0000 | 0 | 28 (outline) | 0.992 | 2269 | 599 | 1405 | 0/0 | 1.00 / 1.00 | 0.025 | 93 |
| principles-economics-3e | 995 | 1.0000 | 0 | 45 (outline) | 0.996 | 1867 | 372 | 706 | 0/0 | 1.00 / 0.95 | 0.025 | 84 |
| psychology-2e | 755 | 1.0000 | 0 | 21 (outline) | 1.000 | 1148 | 354 | 440 | 0/0 | 1.00 / 1.00 | 0.021 | 88 |
| university-physics-volume-1 | 959 | 0.9990 | 1 | 29 (outline) | 0.980 | 2130 | 999 | 2554 | 0/0 | 1.00 / 1.00 | 0.033 | 130 |
| us-history | 987 | 1.0000 | 0 | 44 (outline) | 1.000 | 1722 | 600 | 314 | 0/0 | 1.00 / 0.93 | 0.055 | 214 |

All books: **0.9999 of 7,024 text pages covered**; one page (University Physics) went out as a page image.

EPUB sizes above are the PC's PNG images; on the phone (WebP) University Physics is **40 MB instead of 130 MB**.

## Found and fixed while checking

Reading the converted book page by page on the emulator found problems the metrics alone did not:

- Equation images kept the page's cream paper, glaring on Night paper. The reader now draws converted line art in the
  text colour with the paper left clear (`inkFilter`), on every paper.
- Link underlines drawn as shapes became blue bars in the text; they are dropped now.
- Equation numbers ("2.7") stood alone as paragraphs; they now sit with their equation, as in print.
- A multi-line equation was split into four pieces (one taken for a figure); it is one equation now.
- Superscripts and subscripts were flattened ("10⁷ m" read "107 m"); they come through as `<sup>`/`<sub>` (197 and
  35 in University Physics), and the reader now sets them smaller as well as raised.
- Bold mid-sentence words ("and associative,") became headings; chapter titles set on coloured bands or over photos
  were taken for sidebars or figure labels; titles split over two lines lost their second line. All fixed; the labelled
  heading score went from 0.50-0.80 to 1.00 in every book.
- The contents list wasn't always in reading order (EPUBCheck warnings) and linked to a file outside the spine
  (an error). Fixed; 0 errors and 0 warnings now.

Each fix has a unit test (`StagesTest`, 13 tests) and the corpus gate caught one regression on the way (US History's
chapter titles with years), which was fixed before the baselines were recorded.

## Checks

- `.\gradlew.bat spotlessCheck detekt lintDebug testDebugUnitTest assembleDebug` (the CI command), run locally
  2026-10-27: **BUILD SUCCESSFUL**. That includes 13 stage tests, the robustness and device-EPUB checks, and the
  corpus gate (9 books, EPUBCheck 0/0, sidecars valid against the schema, no regressions). Lint caught one real bug on
  the way: the WebP formats used on the phone need Android 11; Android 10 now uses its single WebP format (that path is
  untested, since the emulator runs Android 17).
- 2026-10-28, after M3: the CI command again, **BUILD SUCCESSFUL** (0 test failures), and the new `corpus` job's check
  on the three CC BY books, passing (1 min 32 s). `FlowServiceOnDeviceTest` passes on the emulator.
- Not run in GitHub CI yet (nothing is pushed until your sign-off).
- On the emulator: `ConversionOnDeviceTest` (the robustness PDFs refused with typed reasons or converted, the textbook
  converted), passing; the reader checked folded and unfolded, Night paper.

## Known limits (for later milestones and phases)

- **Tables and display equations are images** this phase (by plan: Phase 4 tables, Phase 5 MathML). Small vector
  icons in feature boxes count as "equations" in the metrics (about 300 in U.S. History).
- **The front matter** of a converted book (title page credits) reflows as one justified paragraph.
- **Book details lookup mismatches** (a Phase 2 feature, seen while loading the corpus): Open Library matched OpenStax
  *Physics* to Aristotle and *Statistics* to Darrell Huff. Worth a look before Phase 3 ends.
- The labelled set is 30 pages; reading-order score is 0.88-1.00 (the lowest: Calculus, an index page in three
  columns).
- Not measured yet: conversion on your Fold, memory in a separate process, the reader's smoothness during a
  conversion. All are M3.

## M3: Dynamic Flow (added 2026-10-28)

M3's "Convert" entry became your Dynamic Flow design (ADR-0045): one book, read as the PDF or reflowed, with the
conversion in the background.

| What you asked for | Result |
|---|---|
| Settings: "Turn on Dynamic Flow upon opening", off by default, with a hint | **Tested.** Settings > Dynamic Flow. Its hint explains what it does, that the first time takes a minute or so, and that the original PDF stays. A second switch, "Ask before switching views", follows the "Don't show this again" choice; its hint says which answer was remembered. |
| One book, no visible copy | **Tested.** The EPUB is hidden in the app's storage and deleted with the book; the library still shows one book. |
| "Aa" switch PDF ↔ Dynamic Flow, even with the setting off; a decent glyph | **Tested** folded and unfolded: a card above the Text/Page/Controls tabs with its own glyph (a page whose lines flow into waves), the status ("Preparing… 21%", "On: the text flows to fit your screen") and the switch. |
| Progress ring on the switch; a small bar by the page number | **Tested**: the ring fills as the book converts (turns while it waits); with the sheet closed, the page number has the glyph and a slim bar with a glint. |
| Notifications in the app and on the phone, at the start and the end | **Tested**: a glass banner drops from the top ("Preparing Dynamic Flow", "Dynamic Flow is ready", tap to open), and the phone shows a progress notification ("Psychology 2e · 21%") and "ready". The notification permission is asked at the first conversion. |
| Each view shows only its own highlights, notes and bookmarks | **Tested**: the reader, the Annotations and Bookmarks tabs and the library's counts follow the book's current view. |
| The switch warns, with "convert the changes" and "Don't show this again"; then a second notice that it runs in the background | **Tested** both ways on *Physics* (OpenStax): PDF → Dynamic Flow carried a highlight with its note and a bookmark ("Your notes came across: 2 of 2"), and back again (the highlight was found on the right PDF page with its exact outline). "Switch without them" plus "Don't show this again" switched straight over and remembered the answer. |
| "Turn on Dynamic Flow" in the press-and-hold menu and the list swipe, PDFs only | **Tested**: the menu entry and a "Flow" swipe action (it reads "PDF" once Dynamic Flow is on). |
| Conversion in its own process | **Tested**: `com.flowfolio.dev:convert`; *Physics* (875 pages) in 110 s, Psychology 2e in about a minute, while reading on. |

Also in M3:

- **CI corpus gate**: a `corpus` job fetches the three CC BY books (Physics, High School Statistics, Introduction to
  Intellectual Property; cached on the manifest's hash) and checks them against the baselines. The NC-SA books stay a
  local check. New action: `actions/cache` v6.1.0 (MIT, released 2026-06-26), pinned by SHA like the others.
- **Tests**: `FlowServiceOnDeviceTest` (the service in its own process: a good PDF becomes a whole bundle; a damaged,
  password-protected, empty or missing file each reports its reason and leaves nothing behind), `FlowMatcherTest`,
  `ReadingViewsTest`, the source-page mapping in `ReflowBookTest`, and the new settings.
- A crash fixed on the way: recording reading time for a book no longer in the library.

### Measured on the emulator (debug build, 2026-10-28)

- **Conversion time**, in the `:convert` process while the reader stays open: *Principles of Economics 3e* (995 pages)
  108 s, *Physics* (875 pages) 110 s, *U.S. History* (987 pages, many photos) 185 s, *Psychology 2e* (755 pages)
  about a minute. The 5-minute target for a
  1,000-page textbook is met on the emulator; the Fold still has to be measured.
- **Memory of the conversion process** (Economics, sampled every 4 s): median 139 MB PSS, peak 212 MB (106 MB native,
  69 MB Java) near the end, when the images are encoded; about 60 MB once done, until Android reclaims the idle
  process. The reader's own process read 255 MB at the same time: the conversion's memory stays out of it.
- **Reader smoothness during a conversion** (30 page turns each, Economics): frame time P50 17 ms in both runs, P90
  23 ms during against 21-25 ms without; janky frames 5.5% during against 9.4-11.6% without. No visible effect. These
  are debug-build numbers on an emulator (CLAUDE.md, known issue 30): only the comparison means anything.

Found and fixed during these checks:

- The phone's "Dynamic Flow is ready" notification vanished when the last queued book finished (it shared the
  progress notification's id, which the service removes when it stops). It is now posted after the service leaves
  the foreground; checked with U.S. History ("Dynamic Flow is ready ... Tap to read").
- Over the reader the in-app banner was see-through (the reader isn't blurred behind glass, by design), so the book
  title showed through its text. It now has its own near-solid fill.

Not done: **the Fold timing** (the phone wasn't connected); conversion on a real phone may be slower than on this
emulator.

## Conversion quality round (2026-10-28, after your review)

You found titles, bullet lists, code blocks and text charts converting badly, the library's Dynamic Flow entries
switching without the warning, and Dynamic Flow bookmarks and notes not opening at their place. The corpus had only
OpenStax textbooks, with no code in them, so I added five technical books (OpenStax *Introduction to Python
Programming*, *Introduction to Computer Science*, *Principles of Data Science*; *Think Python*; *Pro Git*; all
non-commercial licences, local only) and a technical test PDF of our own that CI checks. What was wrong, and what it is
now (conversion spec 1.1.0, engine 1.1.0):

| You saw | Cause | Now |
|---|---|---|
| Code a mess | Code fonts not recognised (TeX's `SFTT`, `M+ 1mn`): code was read as prose, joined into paragraphs; shaded code boxes taken for sidebars; the reader set code in the body font, justified and hyphenated | Monospaced fonts found by measuring the glyphs; each listing keeps its lines, indentation and blank lines (*Think Python*: 0 → 700+ listings, *Pro Git*: 0 → 1,000+); code in prose is `<code>`; the reader shows code in a monospaced face on a soft panel, scaled to fit, short listings kept on one page |
| Charts drawn with text | Spaces and columns thrown away | Every character goes back to its column: `git log --graph` and box diagrams come out exactly as printed |
| Bullets flowing inline | An item's second line broke it off; each item became its own list; the reader drew no bullets | Items keep all their lines; lists nest by depth, numbered lists stay numbered (with their start), symbol-font and drawn bullets count; the reader draws the bullets and numbers |
| Titles and subtitles | Decorative shapes became images inside headings; running headers on one-page sections became headings; chapter labels ("Chapter 5") ended the previous chapter; table header rows became headings | All fixed; a chapter's label is its title's first line |
| No warning from the library | The menu and swipe switched directly | The same questions as the "Aa" switch, and the notes are carried |
| Bookmarks and notes don't jump | The jump set the EPUB place, which Dynamic Flow doesn't read | Opens Dynamic Flow at the item (checked: a ribbon opened from the Bookmarks tab, ten pages away) |

Also: glossary and reference entries (a hanging indent) stay whole; tables without rules are tables, drawn in the page's
ink; words hyphenated at a line's end are joined; the Bookmarks and Annotations tabs name Dynamic Flow items by their
chapter. Books converted before this round are converted again the next time they are opened, and their highlights
found again by their words.

Checked: the corpus gate on all 14 books (no regressions; every book 100% of its text covered, the technical books
matching 99-100% of their outlines), `TechnicalBookTest` (8 checks), the reader on the emulator (*Think Python* and the
test book, lists, code panels, the glossary, a ribbon jump), the library's warning and carry.

Known limits: tables are still images (Phase 4); a listing too wide even at the smallest code size wraps, and its
wrapped line starts at the left edge; a ribbon made in Dynamic Flow shows no page number in the Bookmarks tab (its
chapter instead).

## Round on your own books (2026-10-28, on your phone)

You asked me to try Dynamic Flow on four of your books (*Claude_Summary_INDEXED*, *CORP_DEVOPS_WORKBOOK*, *AZ-204_notes*,
*Learning Helm*). I converted each on the desktop, compared it with the page images, fixed what was off, then put the
new APK on your phone and read the three books you had in Dynamic Flow there (conversion spec 1.2.0, engine 1.2.2):

| What was off | Book | Why | Now |
|---|---|---|---|
| Prose shown as code | Claude Summary | Its text is set in a code font (JetBrains Mono), and there is more code (Menlo) than prose, so the code font was taken for the body | The body is the most used style that isn't sitting on shaded code panels |
| Every line its own paragraph, or two paragraphs run together | DevOps workbook, AZ-204 | Word's line boxes are as tall as the line spacing, or tight to the letters, so the gap between them says nothing | Paragraphs break where the step from line to line is wider than the page's usual one |
| Bold and italic words lost; lines with a few bold words became headings | all | Only code was kept inside a paragraph | `<strong>` and `<em>`; a heading must be (nearly) all in a heading style |
| ✅ 🔹 📌 ❶ bullets dropped or read as text; Word's "o" sub-bullets read as code | DevOps, AZ-204, Claude | Only the usual bullet glyphs were known | Kept as each item's own bullet (the reader draws them); Word's Courier "o" is a second-level bullet |
| "Java‐ Script", "sta‐ bility" | Learning Helm | Typesetters' hyphen (U+2010) and words broken over a page turn weren't joined | Joined, over lines and page turns |
| Tables cut into a strip per column, a last row left out, a title swallowed | DevOps, AZ-204 | Word draws each cell's borders column by column | One table each, its outer rules and shaded header row included; two tables one above the other stay two |
| A logo or the highlight behind a title shown as a picture | DevOps | Drawings in the margins or behind text counted as figures | Repeated margin logos are page furniture; a highlight behind text is not a figure |
| Section separator lines turned the text between them into "tables" | Claude Summary | Lines of the same length down the page looked like table rules | Rules too far apart for one row are not a table (Claude Summary: 133 images became 46, the rest is text again) |
| After the new conversion, a book opened dozens of pages from your place | Claude Summary | Your place was kept as a position in the old text | It is found again from the page you were on (checked: back at 127-128) |
| Colour-coded icons (🟢🟡🔴) and dark header rows in tables shown grey | DevOps | Every table picture was drawn in the page's ink, like an equation | A table with colour of its own keeps it (your choice); only plain black-and-white tables follow your paper's ink |
| Code on your phone not lined up | all (phone only) | Your phone's font style replaces the system's monospaced font with a proportional one | Code is set in JetBrains Mono, bundled with the app (your choice; SIL OFL 1.1, 187 KB), the same on every phone |

Checked: `OwnBooksTest` (9 cases built from these books), the corpus gate on all 14 books (no regressions; *Physics*
now matches every outline entry), spotless and detekt, and on your Fold: *Claude Summary* (code panels, text charts),
the DevOps workbook (title, lists), AZ-204 (nested and numbered lists, italics, a screenshot). Your reading places are
back where they were; none of these books had Dynamic Flow highlights or ribbons, so nothing needed re-anchoring.

Still open: *Learning Helm*'s numbered callouts (❶ ❷ in the code and the notes under it) are drawn in a way the
converter can't see yet, so they are missing; listings too wide even at the smallest code size wrap (each wrapped line
set in, like an editor's soft wrap); tables are still pictures (Phase 4).

## What I need from you

1. Try Dynamic Flow on the APK and sign off Phase 3 (M1, M2 and M3), or tell me what to change.
2. Accept ADR-0044 (the engine as a plain JVM module, JNA for desktop development only).
3. Then, as ADR-0042 sets out: commit, push, wait for green CI (the first run of the corpus job), build the signed
   release APK and publish **FlowFolio 0.3.0-alpha.1 (build 300001)** as a GitHub pre-release.
