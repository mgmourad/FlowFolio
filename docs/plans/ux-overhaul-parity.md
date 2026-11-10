# UX overhaul: feature parity (ADR-0052)

The overhaul's rule was that no feature may be removed or regressed. This list shows where each one lives after
Phases 1–9.

**How it was checked:**
- **Seen** means it was opened on `fold8_api37` (unfolded unless noted) during the overhaul.
- **Code** means the entry point was traced in code and its tests pass, but it was not opened on a device in this pass.

CI has not run on any of this.

## Library and shelves

| Feature | Where it is now | Checked |
|---|---|---|
| Import files, folders, folder → shelves | Library header ⊕ (`ImportMenu`), folder card ⋯ | Code |
| Continue reading | Library Continue card, with ⋯ | Seen |
| Reading goal (ring, card, reminder) | Library stats band / `GoalCard`, Details → goal | Code |
| Six sort orders, three views | Library sort/view menu (`LibrarySort`, `LibraryView`) | Seen |
| Favourites | Library chip; book ⋯ | Seen |
| Finished | Library chip; book ⋯ | Code |
| Global search | Library header (two-letter minimum) | Seen |
| Select mode, batch actions | Long-press a book | Code |
| Shelves: new, colour, rename, delete with Undo, order | Shelves tab | Code |
| Shelf page: sort, view, add books, drag order | Shelf page | Code |
| Dynamic Flow per book / per shelf | Book ⋯, shelf card switch, reader Aa | Code |

## Notebook (formerly Bookmarks, Annotations, Dictionary)

| Feature | Where it is now | Checked |
|---|---|---|
| Highlights, notes, VoiceMarks, ribbons, words | Notebook tab, kind chips, a card per book | Seen |
| Open a mark in the reader | Notebook book page, tap the entry | Code |
| Delete a mark | Notebook book page, with Undo | Code |
| Export (Markdown/CSV), share | Notebook ⋯; Details ⋯ Export notes | Code |
| Old routes (`bookmarks`, `annotations`, `dictionary`) | Redirect to the Notebook (`Destinations`) | Code (`DestinationsTest`) |

## Details

| Feature | Where it is now | Checked |
|---|---|---|
| Read / continue (the main button names where it lands) | Details | Seen |
| Edit details, Open Library lookup | Details ⋯ Edit | Code |
| Add to shelf, favourite, finished, locate file, remove | Details ⋯ | Code |
| Reset progress | Details ⋯, typed RESET (Phase 9) | Code |
| Notebook for the book | Details Notebook tab | Seen |

## Reader

| Feature | Where it is now | Checked |
|---|---|---|
| Contents, Search, Aa, Listen, Notebook, Words | The dock above the riffle bar (the rail is gone) | Seen |
| Ribbon toggle and colours | Top bar ribbon; ⋯ Ribbon colour | Seen |
| Go to page | Riffle bar; ⋯ Go to page | Seen |
| Riffle scrubber, detour mark | Bottom bar (unchanged drawing) | Code |
| Selection: highlight, note, VoiceMark, define, translate, copy, share | Selection toolbar (unchanged) | Code |
| Per-book text | Aa ("This book" / "Use defaults" / "Make default"), pinch | Code |
| Dynamic Flow ↔ PDF | Aa | Code |
| Narrator | Dock → Listen | Code |
| Atmosphere | Aa / Settings → Atmosphere | Code |

## Settings

All of the old Settings rows sit in the ten-section hub (`SettingsPage`). Each was opened once on `fold8_api37`
unfolded in Phase 7: Appearance, Text, Reading, Sound & voice, Atmosphere, Library, Dynamic Flow, Updates, Storage,
Help & about (licences, tour, What's new).

## Deferred from the mock (the features exist; only the new styling is missing)

- **Riffle header restyle:** the current header already shows the chapter and the page, and opens Go to page.
- **Nine-colour selection menu:** the current selection toolbar keeps every action.
- **Translate language chips and the (+) sheet:** the Words panel translates as before.
- **Listen speed segments:** the existing speed control remains.
- **Coach marks:** the first-use guides from Phase 6 M0 remain.
- **Intro (11 slides):** the existing first-launch tour remains, but its screenshots predate the overhaul (known
  issue 43).
- **What's new from the CHANGELOG:** the existing update check's "What's new" link remains.
- **Reading goal sheet:** the existing `ReadingGoalDialog` remains.
- **Deleting the palette aliases** (`title`, `item`, `hint`, `subItem`, `glyph`): they still map onto the semantic
  tokens. Removing them is a mechanical sweep across every screen, best done on its own.
