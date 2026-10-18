# Phase 1, milestone 5: adopt the design mock

- **Status:** Approved by the product owner, 2026-10-17
- **Design reference:** [`docs/design/FlowFolio_Mock.html`](../design/FlowFolio_Mock.html) (ADR-0015)

## Owner decisions

- The mock is the design reference for all milestones and phases.
- Our 3D page curl stays the default turn. Slide and Scroll are added as options; the mock's flip is not used.
- The reader uses the mock's glass top and bottom bars, replacing the print-style chrome. Pages stay full-bleed on
  the Fold.
- Fonts: Fraunces (display) and Instrument Sans (UI), bundled, SIL OFL.
- Navigation: a floating glass bar at the bottom when folded, a glass rail on the left when unfolded.
- Book metadata: read from the file, plus an online lookup (Open Library). The lookup is off by default, asked on
  the first import, and has a switch in Settings.

## Fold translation

- **Folded (cover screen):** the mock one to one.
- **Unfolded:**
  - a left rail replaces the bottom bar;
  - library shelves run wider;
  - book details uses two panes (cover and actions on the left, tabs on the right);
  - shelves shows more columns;
  - the reader keeps its full-bleed spread, with glass bars spanning it.

## Slices

Each slice is verified on the Fold with screenshots.

1. **Foundations.**
   - The mock's tokens: dark and light palettes, surfaces, card, button and shelf gradients, glow and shadow values,
     easing curves and durations, radii, grain texture.
   - Fonts with Fraunces' SOFT/WONK/opsz settings.
   - Components: glass surface, card, chip, pill, icon button, segmented control, tabs with a sliding indicator,
     toggle, gradient progress bar, progress ring, primary and ghost buttons.
   - The 3D book and shelf plank, plus glyphs from the mock.
   - A debug-only component catalog to check them.
2. **App shell.**
   - An animated splash (about 1.2 s, cold start only, tap to skip).
   - The bottom bar or rail, depending on the fold.
   - Screen entrances and the circular-reveal theme switch.
3. **Library, Shelves and Book details.**
   - Library: "Continue reading" hero, shelf rows of 3D books, chips, search.
   - Shelves: user collections.
   - Book details: blurred backdrop, swaying cover, tabs.
   - Transitions: the cover flight and the book-opening hinge.
4. **Reader.**
   - Glass bars, ribbon bookmark and scrubber bar.
   - Bookmarks get a colour chosen when adding them, from the app's palette roles (owner, 2026-10-18).
   - An Aa sheet with paper swatches (Auto, Paper, Sepia, Night, Black), with images kept as printed.
   - Page turn styles Curl, Slide and Scroll, and an edge bounce.
5. **Bookmarks and Settings.**
   - Bookmarks: ribbon bookmarks.
   - Settings: grouped as in the mock, with Neon glow on/off and "Conversion: On this device".
6. **Data (Room v4).** Shelves (`shelves`, `shelf_books`) and `books.author` moved into slice 3 as Room v3, so
   Shelves could be real collections there.
   - Ribbons, book type, reading sessions.
   - Editable metadata: subtitle, edition, publisher, ISBN, description, language, release date, author bio.
7. **Online lookup** (own ADR).
   - Open Library, by ISBN, or by title and author.
   - Android's built-in HTTP and JSON; the INTERNET permission.
   - Off by default.

## Deferred (hidden until built)

- Highlights and notes: need PDF text selection. A separate feature, planned next.
- Reading reminders: need notification permission and background work.
- Reflow-only features: the Reflow/Original switch, text size, hyphenation, publisher fonts, Conversion health,
  the "Converted" chip and auto-convert. These come in Phase 3.

## Budgets

The reader keeps ADR-0013's budget. Blurred backdrops and 3D books are measured on the library and details screens
too.
