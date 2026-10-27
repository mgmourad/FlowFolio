# ADR-0040: EPUB highlights and ribbons stored by locator

- **Status:** Accepted (lead engineer, within the M4 plan the owner asked to be finished in one go, 2026-10-27)
- **Date:** 2026-10-27
- **Deciders:** lead engineer
- **Phase:** 2 (M4)

## Context

A PDF highlight is stored as its page, its characters on that page (PDFium's order) and its rectangles as fractions of
the page (ADR-0016). That holds because a PDF page never changes. An EPUB's pages do change: every text size, font,
line spacing, margin, fold or unfold lays the book out again (ADR-0019), and the text moves to other pages and other
places on them. A highlight stored like a PDF's would point at the wrong words after the first change.

The reflow engine already keeps the reading position as a `Locator`, a chapter and a character offset within it, which
survives any layout.

## Decision

1. **An EPUB highlight is stored by its text.** `annotations.locator` (Room v14) is `chapter:offset` of its first
   character. `char_start` and `char_end` are then its first and last chapter offsets, not page positions. `page` and
   `rects` are only where the reader last placed it.
2. **The reader places highlights in the current layout.** For each highlight it finds the pages covering its offsets
   and draws rectangles from the layout's character boxes (`ReflowBook.pageGlyphs`). It works out rectangles only for
   pages near the reader, since that needs each page's character boxes. Pages further away get only their page number,
   which is cheap. A highlight running onto a second page is drawn on both and listed once.
3. **Selection reads the layout.** An EPUB page's text, with a box per character and each character's chapter offset,
   is built from its text slices, so the PDF selection code (`TextSelection`, `TextPick`, the handles and magnifier)
   works unchanged.
4. **Ribbons too.** `bookmarks.locator` is the text at the top of the marked page. After a re-layout the ribbon is on
   the page now showing that text.
5. **Saved pages follow the reader's layout.** Once a layout's pages are final (no chapters estimated), highlights and
   ribbons whose page moved are saved with the new page. Their `updated_at` is unchanged, since the reader changed
   nothing. The library's lists then stay in reading order and show the page last read. Opening one from the library
   makes its locator the book's position (`books.last_locator`), so the book opens at the text whatever its size.

## Consequences

- Highlights and ribbons follow their text through any change of text settings, fold or unfold.
- A selection stays within one page, as for PDFs, since pages never span chapters. A highlight made at a large size
  may still run onto two pages at a smaller one.
- The library's page numbers for EPUB highlights are screen pages at the last size read, unless the book has a page
  list. Chapter names come from the contents (`ReaderRepository.epubChapterTitles`).
- If a book's file is replaced by another edition, its locators may point at other text. The same holds for the
  reading position.
