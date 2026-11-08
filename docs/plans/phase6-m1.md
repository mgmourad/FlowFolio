# Phase 6 M1 — search inside a book

- **Status:** approved by the owner 2026-11-07 ("Yes, build it").
- **Goal:** find a phrase in the book being read, in both views (PDF and Dynamic Flow), with results that jump to the
  match and highlight it.
- **Decided with this milestone:** the M3 collision is settled early — **"give images a voice"**. Equations and tables
  stay images, but the converter will emit a short spoken `alt` for them, so TTS (M3) and screen readers (M4) can name
  them rather than skip them silently. This lands in M3/M4, not here; recording it now so it is not re-litigated.

## Why there is little to build

The hard parts already exist (Phase 2):

- Reflowed text is held as chapters with character offsets (`Locator` = `chapter:offset`); `ReflowBook.pageOf(locator)`
  turns a locator into a page.
- `ReaderViewModel.pageText(page)` already returns page text for **both** views — the EPUB layout's glyphs for reflow,
  PDFium's `nativePageText` for the PDF.
- The locator → highlight path (ADR-0040) already turns a place into a drawn highlight, and the reader already jumps to
  a page (`goToPage`).

So search is a small index, a results panel, and wiring to the existing jump/highlight paths — not new technology.

## Components

### 1. The searchable text

- **Reflow.** Add `ReflowBook.chapterText(chapter)` returning the chapter's plain text plus an `IntArray` mapping each
  character index to its chapter offset (text blocks contribute their text; images/islands one place). Search each
  chapter in order; a match is a `(chapter, offset, length)` → a `Locator`.
- **PDF.** Search `document.pageText(page).text` per page; a match is a `(page, charStart, charEnd)`.

Both are wrapped in one `BookSearch` that returns a common result: `(locatorOrPage, snippet, chapter/section label)`.

### 2. The engine

- Case-insensitive substring search (`indexOf(query, ignoreCase = true)`), no new dependency.
- A result carries: the place (locator for reflow, page for PDF), a snippet (the surrounding line), and a chapter/section
  label (from the outline for PDF, or the EPUB chapter title for reflow).
- Capped at `SEARCH_CAP` (50) results, with a "N more" tail.

### 3. The UI

- A **Search** button in the reader's top bar (a `Panel.SEARCH`), beside Contents.
- A `DropPanel` holding a search field (auto-focused) and a result list. Each row: chapter/section, snippet, and the
  page. Tapping jumps to the place and highlights the match; the panel stays open so the reader can try another result.
- `Panel.SEARCH` reuses the existing panel machinery (`ReaderPanels`, `PanelDismiss`, the chrome clock).

### 4. Jump and highlight

- Jump: `viewModel.goToPage(page)` (reflow maps the locator to a page via `ReflowBook.pageOf`).
- Highlight: a transient `Annotation` (locator + char range for reflow, page + rects for PDF) placed through the existing
  `viewModel.place(...)` so it draws via `LocalHighlights`; it is cleared when the search closes or the query changes.

## Files

- `android/core/reflow/src/main/kotlin/com/flowfolio/core/reflow/ReflowBook.kt` — add `chapterText`.
- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/BookSearch.kt` — the search engine + result model
  (new).
- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/ReaderViewModel.kt` — search state, results, and
  the jump/highlight call.
- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/ChromeActions.kt` — add `Panel.SEARCH`.
- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/ReaderChrome.kt` — the Search button.
- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/ReaderPanels.kt` — the `Panel.SEARCH` panel.
- `android/feature/reader/src/main/res/values/strings.xml` — copy.

## Verification

- `spotlessCheck detekt testDebugUnitTest -x :tools:convert:test`, `:app:assembleDebug`.
- On `fold8_api37` (and the owner's Fold, folded and unfolded): search in a Dynamic Flow book and a PDF book, jump to a
  result, and confirm the match is highlighted. Unit tests for the search engine (`BookSearch`) and `chapterText`
  offsets.
