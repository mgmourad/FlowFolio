# Phase 4 M3 plan: fallbacks worth keeping

- **Status:** Draft, for the owner's approval
- **Phase plan:** `PLAN.local.md`, Phase 4 — this is the work that replaces the dropped old Phase 5, now that images
  are the long-term answer for tables and equations.

## Goal

The places the converter gives up on (whole-page fallbacks, tables, equations, figures) are *good to read*, not merely
present. Five items, mapped against the code as it stands:

| # | Item | Where it lives today | Status |
|---|---|---|---|
| 1 | Fix known issue 44 — a full-page cover image overflows the column and is clipped mid-word | `ChapterXhtml.picture` + `book.css` (`ff-fallback`/`ff-figure` width) | **open** |
| 2 | Fallback images stand up to zooming, not re-encoded into mush | `Converter.images` (`figureScale = 2f`, `pageScale = 1.5f`) and the WebP/PNG encoders | **open** |
| 3 | Captions stay with their figure, table or equation | `ChapterXhtml.write` pairs a caption with a picture (`PICTURES = {FIGURE, TABLE}`) | **mostly done**; equations are not paired |
| 4 | A hint that a picture can be opened — today you have to guess | the reader's `FigureZoom` / picture tap target | **open** |
| 5 | A table image wider than the column uses the full width, not shrunk to fit | `book.css` `ff-table` width | **open** |

## What each item needs

1. **Cover overflow (issue 44).** The title-page cover artwork is laid out wider than the text column, so it is
   clipped. Root cause to confirm: the `<figure class="ff-figure">` image has no `max-width` (or the wrong one) in
   `book.css`, so a full-bleed crop renders at its natural width. Fix in `book.css` (constrain `ff-figure img` to the
   column, `max-width: 100%`), then verify the OpenStax *Introduction to Python Programming* title page.

2. **Resolution.** `figureScale` (2 px/pt = 144 dpi) and `pageScale` (1.5 = 108 dpi) bound how sharp a crop is when
   pinched. Raise the fallback/page scale and confirm WebP stays lossless for line art; measure the size cost on a
   textbook so the EPUB does not balloon.

3. **Captions with equations.** `PICTURES` excludes `EQUATION`, so a caption beside a display equation is emitted as a
   loose `<p class="ff-caption">` instead of a `figcaption`. Add `EQUATION` to the pairing, and confirm equations with
   number labels are unaffected (their labels are not captions).

4. **"Tap to open" hint.** The reader already opens a tapped picture in `FigureZoom`; nothing tells the reader it is
   tappable. Add a small affordance (a glyph or a one-line hint) to picture blocks in the reflowed view, consistent
   with the design system.

5. **Full-width tables.** A table crop wider than the column should span the available width rather than shrink. Fix in
   `book.css` (`ff-table` sizing) and verify a wide table in a technical book.

## Order

Items 1–3 are converter/CSS and land together (engine version bump). Items 4–5 are reader work and land with a reader
change; they can follow. Item 2's size/quality tradeoff wants a measurement on a textbook before committing the scale.

## Done when

The cover no longer clips (issue 44); a fallback image holds up when pinched; a caption never separates from its
figure, table or equation; a tappable picture is visibly tappable; a wide table uses the full width.
