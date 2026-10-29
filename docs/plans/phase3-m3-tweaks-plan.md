# Phase 3, M3 tweaks: plan (M3T1, M3T2, M3T3)

- **Status:** approved by the owner 2026-10-28, with the answers below; Phase 3 committed and released first.
- **Source:** the owner's list "Tweaks and features" and their annotated screenshot of the library (2026-10-28), plus
  two reference pictures: a route from a pin to a flag (the goal path's look) and an open-book glyph ("Progress").
- **Rule kept:** every tweak is checked on `fold8_api37` folded and unfolded, then on the owner's Fold; each milestone
  ends with a short report and an APK.

The three milestones go from no new data to new data and new dependencies. Each one stands alone, so you can try it
and sign it off before the next starts.

## M3T1: polish and navigation (no new data, no new dependencies)

**Built 2026-10-29**, checked on `fold8_api37` folded and unfolded, installed on the owner's Fold. Two deliberate
differences from the list below: the read-time bar on the Continue reading card comes with M3T3's goals (the card shows
the read time with its glyph until then), and the star and check buttons come with M3T2's data. The riffle book is drawn
in the strip's own canvas so it keeps the old handle's touch target, sway and wiggle.

Reader:
1. **Contents panel cascades in**: its rows come in one after another, on the motion system's timings (ADR-0033).
2. **Riffle bar's side buttons redrawn**: the arrows are centred optically, in a new, quieter control style that
   matches the bar.
3. **Riffle book glyph and glass marker**:
   - The ribbon and the pin are replaced by an animated book that riffles its pages as you slide it.
   - The place on the bar is a separate mark: a vertical 3D glass bar glowing in the theme's colour, never wider than
     the book glyph.
   - Both stay in sync with the slide.
   - Screenshot tests (Roborazzi, ADR-0024) are re-recorded.
4. **"Aa" sheet**:
   - A glyph for every setting.
   - The Page and Controls tabs slide over each other.
   - The Dynamic Flow section stands out with a glowing outline in the theme's colour (the outline animated only with
     an offscreen layer, as the GPU-memory rule says).
5. **Dynamic Flow warning only when it matters**: turning Dynamic Flow on or off asks the "convert the changes"
   questions only when the book has bookmarks, highlights or notes in the view you are leaving.

App shell and library:
6. **The tab bar and rail never hide**: opening a book's annotations, a shelf's page or a details page keeps the side
   rail (unfolded) or the bottom bar (folded) in place, so page titles always sit in the same spot. The reader stays
   full screen.
7. **Icons only** in the tab bar and the rail, with each name kept for TalkBack and as a tooltip on press and hold.
8. **Shelves**: opening a shelf zooms from its folder into the shelf page (a shared-element transition).
9. **Library header**: search, sort and import move up, level with the title "Library".
10. **Continue reading card** (from your drawing):
    - The title and author set smaller.
    - "Progress" with the open-book glyph, and "Read time" with a clock glyph, each with its bar.
    - A Dynamic Flow button that glows when the book is in Dynamic Flow and is dim when it isn't.
    - The star and check buttons are placed but stay hidden until M3T2.

## M3T2: finished books, favourites and search (database v16, no new dependencies)

1. **Favourites**: a star on each book, a Favourites page in the tab bar (laid out like a shelf page), the star on
   the Continue reading card, and in the press-and-hold menus.
2. **Finished**:
   - "Mark as finished" in every press-and-hold menu and on the Continue reading card.
   - A "Finished" filter chip after "Not started".
   - A "Finished Books" shelf at the bottom of the library.
   - A finished book leaves "In progress".
3. **Glyphs on the filter chips** (All, In progress, Not started, Finished, Recently added). The Finished tab shows
   its books as tiles only.
4. **Second column of stats** next to the current one: favourites, finished books, dictionary words (the translations
   saved so far, then definitions too from M3T3).
5. **Search everything**: the search icon opens one search over books (title, author, series, subjects), shelves,
   bookmarks, highlights and notes, favourites, and translated or defined words.
   - It is fuzzy (forgives typos and partial words).
   - Results update as you type, grouped by kind with counts, and each opens its place.
   - Matching runs on the phone over what the app already stores; no new library.
6. **Database**: Room v16, `books.favorite` and `books.finished_at` (a migration and its test).

## M3T3: Reading Goal and Dictionary (database v17; two decisions needed, see questions)

1. **Reading Goal icon**: top right of the library, an open book with a target and an arrow in its centre (drawn
   from your description).
2. **Reading Goal window**: a centred window, not a sheet, with two sections.
   - *Set Reading Goal*:
     - an iPhone-style wheel for hours and minutes;
     - a period: a day, a week or a month;
     - Reset;
     - "Remind me", which shows a time of day and the days to remind on.
   - *In progress*: one row per book being read, each with a progress bar whose colour moves along the theme's
     gradient as the book nears its end. A row opens the book's details.
3. **Goal path in the library header**: a curved path from reading glasses to an open book (styled like your
   pin-to-flag picture) that fills as you near the goal, its colour changing on the way. Folded: the second column of
   stats (favourites, finished, dictionary) on the right, beside the first, and the goal path right under the three
   new stats. Unfolded: laid out for the wider screen.
4. **Book details**:
   - "Add Reading Goal" next to Continue and Add to shelf opens the same window's first section, for that book only.
   - A second progress bar, for time read against that book's goal, shown only when the book has one. When time read
     goes past the goal, a tip under the bar suggests resetting the goal.
5. **Reminders**: notifications titled "Time to read <book title>", with a short public-domain quote about reading
   or knowledge (a list of about 60, each with its author), at the times you set.
   - Scheduled with Android's own AlarmManager (no new dependency) and set again after a restart.
   - Android 13 and later ask for notification permission when the first reminder is set.
6. **Define**: in the selection menu, only when the selection is a single word, an English definition shown from an
   offline dictionary.
7. **Dictionary page** in the tab bar:
   - Laid out like Annotations: one group per book; each entry shows the word, its translation, and its English
     definition.
   - Searchable, and each entry opens its place in the book.
   - Translations already saved (Room `translations`) appear there from day one.
8. **Database**: Room v17, `reading_goals` (a book or the whole library, minutes, period, reminder) and `definitions`.

Dictionary (owner's choice): English Wiktionary, the most comprehensive, downloaded the first time Define is used
(CC BY-SA; a short spike measures its size and builds the on-phone index first). An ADR records it, and `licenses.md`
the data and its attribution.

## Order and size

| Milestone | Main areas | Risk |
|---|---|---|
| M3T1 | reader panels, riffle bar, app shell, library header and card | Riffle glyph and glass marker (custom drawing and screenshot tests); persistent shell (navigation rework) |
| M3T2 | data, library, menus, new page, search | Fuzzy search quality and speed on a large library |
| M3T3 | goals, notifications, dictionary data | Dictionary size and licence; exact reminder times with Android's battery rules |

Before M3T1: Phase 3 is committed and released as FlowFolio 0.3.0-alpha.1 (owner, 2026-10-28).
