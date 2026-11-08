# Phase 6 M2 — fixed-layout EPUBs

- **Status:** approved (owner: "start M2 automatically").
- **Goal:** a `rendition:layout: pre-paginated` EPUB looks like itself — one spine item per page, drawn at page size —
  instead of being reflowed into wrong-looking text.
- **ADR-0019:** each page is a WebView drawing at page size.

## What exists

- `EpubPackage.fixedLayout` is already set from `rendition:layout` (tested in `EpubParserTest`), but the reader ignores it
  and reflows everything through `EpubSession`/`ReflowBook`.
- The reader already renders WebViews for islands (`IslandHost`/`IslandRenderer`), serving only the book's files with
  `blockNetworkLoads` and drawing the whole document in software. Fixed-layout pages reuse exactly this mechanism.

## Approach (minimal, correct)

A fixed-layout EPUB reads like a PDF: a fixed number of pages, each a bitmap, turned with the same curl, zoomable, but
with no text selection or reflow. One new page source replaces `EpubSession` for these books.

- **`FixedEpubPageSource`** (`feature/reader`): one page per linear spine item. `render(key)` loads that item's XHTML
  into a hidden WebView (the island renderer's file-serving, network-blocked setup), waits for it to finish, and draws
  the whole document at a fixed page size into the page bitmap. Page size comes from the item's `viewport` meta when it
  has one, else a standard 1024×1366 page.
- **`ReaderViewModel.open`**: when `opened.pkg.fixedLayout`, build `FixedEpubPageSource` (like the PDF path) and set
  `reflowable = false`, so the reader shows page turns, zoom and the riffle but not text settings.
- The island WebView stays for reflowable books only; fixed-layout books use their own WebView in the page source.

## Out of scope (this pass)

- Text selection, search and highlights inside fixed-layout pages (the WebView's text isn't plumbed into the reader's
  selection pipeline). Noted as a follow-up.

## Files

- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/FixedEpubPageSource.kt` — new page source.
- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/ReaderViewModel.kt` — branch on `fixedLayout`.

## Verification

- `spotlessCheck detekt testDebugUnitTest -x :tools:convert:test`, `:app:assembleDebug`.
- A fixed-layout EPUB (children's book or comic) opens page-by-page at page size on the emulator/phone, and a
  reflowable EPUB still reflows unchanged.
