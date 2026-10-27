# Phase 2, milestone 4 sign-off: Phase 1 features on EPUB

- **Status:** Built and checked by the lead engineer, 2026-10-27; signed off with Phase 2, 2026-10-27
  ([`phase2-signoff.md`](phase2-signoff.md)).
- **Plan:** [`phase2-m4.md`](phase2-m4.md); ADR-0040 (EPUB highlights and ribbons by locator).
- **Checked on:** the `fold8_api37` emulator, unfolded (spread) and folded (cover screen).
- **Books:** Moby-Dick, *Children's Literature* (pictures) and the generated right-to-left sample.

## What M4 built

1. **Selection on EPUB pages.** Press and hold selects a word; dragging and the handles extend it, with the magnifier.
   The page's text comes from the layout: each character, its box and its chapter offset.
2. **Highlights and notes by locator** (Room v14). They follow their text through any re-layout and are drawn on
   every page they run onto, in Curl, Slide and Scroll.
3. **Ribbons by locator**: the text at the top of the marked page.
4. **Share, Copy and Translate** from the highlight bar.
5. **The reader's Annotations panel and ribbon button for EPUBs.** The library's Annotations and Bookmarks tabs open an
   EPUB at the text, named by chapter.
6. **The image viewer**: a tap on a picture opens it fitted, then pinched up to four times and panned.
7. **Right to left**: selection and highlights in Arabic, in a spread that starts on the right.
8. **Fixed-layout EPUBs move to Phase 8** (see the plan).

## Checks

| Check | Result |
|---|---|
| Unit and static checks | **Pass:** every JVM and Robolectric test (new: `PageGlyphsTest`, `LocatorColumnsTest`, `ChapterTitlesTest`, right-to-left detection in `TextSelectionTest`), Spotless, detekt, lint, and the riffle screenshot tests. |
| Selection on a justified page | **Pass after a fix.** `getPrimaryHorizontal` leaves justification out, so at first the tint started a word space early on justified lines. The slack is now shared over the line's spaces, and the tint starts exactly at the word. |
| A highlight through a re-layout | **Pass.** It was made unfolded on spread 20-21, then the phone was folded (a new layout of 1515 pages). It is on page 21, over the same words, and still there in Scroll at a larger text size. |
| Highlight saved by locator | **Pass:** stored as `4:9084` with chapter offsets 9084-9206. |
| Annotations panel, library tab, jump | **Pass.** The panel lists it; the tab names its chapter ("EXTRACTS…", "الفصل 1"); opening it lands on the spread showing it. |
| Ribbon | **Pass:** stored as `4:10318`, the text at the top of the left page; it shows on the riffle strip. |
| Image viewer | **Pass:** the title page of *Children's Literature* opens fitted to the cover screen. Pinching inside it can't be sent over adb. |
| Right to left | **Pass:** Arabic selection follows the reading order across lines, with handles at its right-hand start and left-hand end after a fix. Highlights are drawn in place. |

## Known limits

- A selection stays on one page, as for PDFs. Dragging selects to the character, like PDFs; the handles adjust it.
- Without a page list, the library's page numbers for EPUB highlights are screen pages at the size last read.
- Not checked on the reference Fold: everything here was checked on the emulator. CI has not run (nothing is pushed
  until the Phase 2 sign-off).
