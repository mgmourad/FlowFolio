# ADR-0016: Keep annotations in our database, selected from PDFium's text layer

- **Status:** Proposed
- **Date:** 2026-10-18
- **Deciders:** product owner, lead engineer
- **Phase:** 1 (milestone 5 follow-up)

## Context

The owner asked for annotations (2026-10-18):
- highlight text in a book in one of 4 or 5 colours;
- add notes;
- share a highlight as a quote with the book's title, author and page;
- an Annotations tab and a per-book list;
- quick access from the reader.

The M5 plan deferred highlights and notes until PDF text selection existed. The owner also asked for books that read
right to left (Arabic and similar).

Constraints:
- No new dependencies unless agreed.
- No GPL code.
- The reader's memory budget (ADR-0013).
- Books may be read in place (ADR-0012 storage setting), so FlowFolio must not write to the user's files.

## Options considered

1. **Write PDF highlight annotations into the file** (PDFium `FPDFAnnot_*`). The highlights would be visible in
   other readers.
   - It modifies the user's files, which is impossible for read-in-place books on read-only providers.
   - It changes the file's SHA-256, which is the book's id.
   - Saving a large textbook means rewriting it.
2. **Keep annotations in FlowFolio's database, located by PDFium's text layer.**
   - The page's text and per-character boxes come from `fpdf_text.h`, which is already in our PDFium build.
   - The selection is stored as character offsets plus rectangles as fractions of the page.
   - The app draws highlights over the rendered page.
3. **A third-party text-selection or annotation library.** All candidates we know wrap PDFium or MuPDF (AGPL). They
   would add a dependency for logic we can write in a few hundred lines.

## Decision

Option 2, with these parts:
- **JNI.** `nativePageText` returns a page's text and a box per character (rotation applied, as fractions of the
  page). `PdfDocument.pageText` exposes it.
- **Selection.** The pure functions in `TextSelection` do hit-testing, word bounds, per-line rectangles and quote
  cleanup.
- **Storage.** Highlights live in Room schema v6, table `annotations`: page, printed label, character range, text,
  rectangles, colour, note and times. The same schema adds `books.right_to_left` for the reading direction.
- **Drawing.** Highlights are drawn in each page pane, so they turn with the page:
  - multiplied into light paper;
  - a translucent wash on dark paper.
- **Right to left.** The reader mirrors the book layer, so the curl lifts from the left and a spread starts on the
  right, and mirrors each page back. Gestures follow the mirror; page frames, edge bounce and the scrubber are flipped
  to match.

## Consequences

- **Positive:**
  - No new dependency.
  - Works for copied and read-in-place books.
  - Book ids stay stable.
  - Highlights follow every paper colour.
  - Sharing and the Annotations tab work offline.
- **Negative / accepted risks:**
  - Highlights are not visible in other PDF readers; an export can come later.
  - Scanned PDFs without a text layer cannot be highlighted.
  - In the first version, selection is not available in Scroll mode or while zoomed in.
- **Follow-ups:**
  - Export annotations (Markdown or PDF), owner to decide.
  - Selection in Scroll and zoom.
  - Reflow mode (Phase 3) must map highlights to its own text.
