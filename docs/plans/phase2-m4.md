# Phase 2, milestone 4: Phase 1 features on EPUB

- **Status:** Built and checked 2026-10-27 ([`phase2-m4-signoff.md`](phase2-m4-signoff.md)), at the owner's request
  ("finish M4 in one shot"); everything is committed with M5, as one Phase 2 sign-off (owner, 2026-10-27).
- **Plan:** [`phase2-plan.md`](phase2-plan.md) (M4); ADR-0040 (EPUB annotations by locator).

## What M4 builds

1. **Selection on EPUB pages.** Press and hold selects a word, dragging and the handles extend it, with the magnifier,
   as on PDFs. The page's text and where each character is come from the layout itself (`ReflowBook.pageGlyphs`): the
   lines of each text slice, with a box per character, and a break without a box between blocks. Every character
   keeps its chapter offset.
2. **Highlights and notes by locator** (ADR-0040). An EPUB highlight is saved as its chapter and first and last
   character offsets (`annotations.locator`, Room v14), not as page rectangles: a new text size, font, margin or fold
   lays the book out again and moves the text to other pages. The reader places each highlight on the pages it now
   covers, drawing its rectangles from the current layout, near the reader only (pages far away get just their page
   number). The page number saved with it follows the reader's last layout, so the library's lists stay in order.
3. **Ribbons by locator.** A ribbon marks the text at the top of the page (`bookmarks.locator`); after a re-layout it
   sits on the page now showing that text.
4. **Share, Copy and Translate** come with the highlight bar, as for PDFs.
5. **The Annotations panel and the library's tabs.** The reader's highlight button shows for EPUBs; the Annotations
   and Bookmarks tabs open an EPUB at the highlight's or ribbon's text, and name its chapter from the book's contents.
6. **The image viewer.** Tapping a picture in an EPUB (not a small ornament) opens it zoomable, like a shrunk
   equation: fitted to the screen, pinched up to four times, panned; a tap or Back closes it. It is decoded from the
   book at up to twice the screen's size.
7. **Right to left.** Check that selection, highlights, ribbons and the image viewer work in a right-to-left book
   (the generated RTL sample), in Curl, Slide and Scroll, folded and unfolded.
8. **Fixed-layout EPUBs** (`rendition:layout` pre-paginated) **move to Phase 8.** Each page needs a WebView drawing at
   a fixed viewport scaled to the screen, with its own zoom and its own text positions for selection: a second
   rendering engine, not cheap. Until then they are reflowed like other EPUBs, which reads acceptably for text-led
   ones.

## Checks

- Unit tests: page glyphs (each character's offset and box, breaks between blocks, a line split across pages),
  placing a highlight after a re-layout, locator round trips, the Room migration.
- On the `fold8_api37` emulator, folded and unfolded: highlight, note, share, copy and translate on Moby-Dick;
  change the text size and see the highlight follow its text; ribbons survive a re-layout; the Annotations panel
  and tabs jump to the text; an image opens zoomed; the RTL sample.
