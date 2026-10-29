# ADR-0045: Dynamic Flow, a reading mode of the PDF itself

- **Status:** Accepted (product owner, 2026-10-27: the design below is theirs)
- **Date:** 2026-10-27
- **Deciders:** product owner, lead engineer
- **Phase:** 3 (replaces the plan's M3 item 2, a "Convert to FlowFolio" preview button)

## Context

The converter (ADR-0044) turns a PDF into EPUB 3 on the phone. The plan's M3 would have offered a "Convert to FlowFolio"
button that made a second book. The owner chose instead (2026-10-27) that conversion is a **mode of the same book**,
called **Dynamic Flow**: one book in the library, read either as the PDF or as its reflowed EPUB.

## Decision

1. **One book, two views.** A PDF book keeps its library entry. Its converted bundle (`book.epub`, `flowfolio.json`)
   lives in app-private storage (`files/flow/<bookId>/`), is never listed as a book, and is deleted with the book. The
   book remembers its view: `books.flow_mode` (Room v15) is null (never chosen), 0 (PDF) or 1 (Dynamic Flow).
2. **Settings: "Turn on Dynamic Flow upon opening"** (off by default). On: opening a PDF whose view was never chosen
   converts it and reads it in Dynamic Flow. Off: PDFs open as PDFs. Either way the "Aa" sheet has a **Dynamic Flow**
   switch for the open book, and the library's press-and-hold menus and list swipe offer **Turn on Dynamic Flow** for
   any PDF.
3. **Conversion runs in the background, in its own process** (`:convert`): a foreground service, so a big book keeps
   converting while the reader reads on and PDFium's parsing of an untrusted file can't take the app down. It converts
   one book at a time, queues the rest, pauses while the phone is hot (severe thermal status) or the battery is low and
   not charging, and starts again from the top when the book is next opened if the process was stopped. No new
   dependency: WorkManager would need its multi-process add-on for the same result.
4. **While a book converts** the reader stays on the PDF. The Dynamic Flow switch shows a progress ring; with the sheet
   closed, a small progress bar sits beside the page number. A notification on the phone (with progress) and a notice
   in the app say when it starts and when it is done; when done, the reader moves to Dynamic Flow at the same place.
5. **Places carry across views** through the converter's page markers (`#page-N` in the page list): a PDF page maps to
   the start of that page in the EPUB; a Dynamic Flow position maps back to the PDF page it is in. Reading in Dynamic
   Flow keeps `last_page` at the PDF page, so the library's progress means the same in both views; the EPUB's own place
   is `books.flow_locator`.
6. **Highlights, notes and bookmarks belong to a view.** Each view shows only its own: Dynamic Flow's are those with a
   locator (ADR-0040), the PDF's those without. The library's Annotations, Bookmarks and per-book lists follow the
   book's current view. Switching views warns that what was made in this view won't show in the other unless the
   reader **converts the changes** (a "don't show again" choice remembers the answer); converting them runs in the
   background with the same notifications, matching each item's text near its mapped place in the other view.

## Consequences

- Positive: no duplicate books; the PDF stays the reference; conversion never blocks reading.
- Negative / accepted risks: a book's storage roughly doubles while Dynamic Flow is kept (the bundle is about a third of
  the PDF's size with WebP); converted annotations can miss when the text differs between views (they are counted and
  reported, never silently dropped); a stopped conversion restarts from the first page.
- Follow-ups: Phase 6's product flow (progressive delivery, health badge) builds on this view switch.
