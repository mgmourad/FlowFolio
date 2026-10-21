# Phase 2, M2 follow-up: EPUB links and footnote popovers

- **Status:** Approved by the product owner, 2026-10-21 ("finish the EPUB links and footnote popovers first before
  going to M3"). Planned for M2, moved after M2b at the M2 sign-off (`phase2-m2-signoff.md`, open item 1).
- **Goal (phase 2 plan, goal 4):** internal links, and footnotes in a popover instead of a jump.

## What exists

- `:core:reflow` keeps every `<a href>` as a `LinkSpan` on the text, with the href resolved in the book
  (`OEBPS/ch02.xhtml#fn3`) or absolute (`https://...`).
- Footnote and endnote bodies (`epub:type` or `role` footnote / endnote) leave the flow and are kept by id;
  `ReflowBook.footnote(href)` finds one, `ReflowBook.locatorOf(href)` finds a place in the reading order.
- PDF links reach the reader as rectangles on the page image (0 to 1), routed by `PageFrames.route`: a jump, a web link
  (confirmed in a dialog, http, https and mailto only), or a page turn.

## Plan

1. **Where links are on a page** (`:core:reflow`, `PageLinks.kt`): for each text slice of a page, each `LinkSpan` on its
   lines gives one rectangle per line it covers, in the page box's pixels. Robolectric test: areas exist for the links
   on a page, lie inside the column and carry their hrefs.
2. **What a link does** (`EpubSession.links(page)`): rectangles move by the page margins and are scaled to the page
   image, as PDF links are. An absolute link is a web link; a link to a footnote opens its popover; a link into the
   reading order jumps to its page; anything else (a file outside the reading order) does nothing.
3. **One link model in the reader** (`ReaderLink`, `LinkTarget`: page, web, note), with PDF links converted on load.
   EPUB links load with the pages being planned and are dropped when the book is laid out again.
4. **The footnote popover**: a glass card in the reader's window, in the paper's palette, next to the tapped reference
   (above it in the lower half of the screen, below it in the upper half). It shows the note's text with its bold and
   italics and scrolls if long. A tap outside, Back, or a page turn closes it.
5. **Checks**: unit tests for routing (note, page, web, nothing), the Robolectric link areas, a Compose test for the
   popover, and the Fold with the IDPF samples (Accessible EPUB 3 for internal links, The Waste Land for notes).

## Not in this slice

- A "back to where you were" control after following a link (PDFs don't have one either).
- Links on images (`<a>` around `<img>`), and links inside a footnote's popover.
