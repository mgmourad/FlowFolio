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

## Finished on 2026-11-10 (formerly deferred)

Each of these was opened on `fold8_api37` unfolded with the final build:
- the riffle header;
- the nine-colour selection menu ("VoiceMark" now reads in full);
- the Translate chips and the (+) sheet (Spanish added and used: "God" → "Dios");
- Listen's speed segments;
- the five coach marks;
- the 11-slide tour with retaken pictures;
- What's new from the bundled CHANGELOG (Settings → Help);
- the reading-goal sheet.

The palette aliases are deleted. The design-system screenshot tests match unchanged, so no colour moved.

**Not opened on a device:**
- "Preparing · N%": no book was converting during the check.
- The post-update What's new window: it needs an update from an older build.

**Not built,** being new features rather than restyles: a narrator sleep timer, a narrator mini bar, and the
spoken-sentence highlight.

## The owner's fixes, 2026-11-10

These were checked on `fold8_api37`:
- Details follows the mock unfolded, with no shadow under the book; tags work through "+ Tag".
- The reader's top bar shows title · chapter · pages left.
- The dock is one card (riffle, then the tools), with a monochrome strip.
- Words is one panel: Define and Translate were both opened from a selection, and History from the dock.
- The folded goal ring shows the glasses, then 🎉. It was checked by briefly setting the emulator's goal to 5
  minutes, then restoring 45.
- Unfolded, Resume and Details share a line.

Not seen on a device: the folded Details layout.
