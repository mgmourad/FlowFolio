# Phase 2, milestone 1: EPUB in the library

- **Status:** Started 2026-10-20, after the owner accepted ADR-0019 and ADR-0020 ("Start milestone 1").
- **Parent plan:** [`phase2-plan.md`](phase2-plan.md)

## Goal

EPUB books can be imported, appear in the library like PDFs, and show their details. Reading them comes in M2. Until
then, opening one shows a clear "EPUB reading is coming" message instead of an error.

## Work

1. **The `:core:epub` module** (ADR-0020): the spike's parser, made product-quality.
   - The archive: from a file, or from a file descriptor for read-in-place books. Paths stay inside the book, entries
     are capped by inflated size, and zip exceptions (such as Android's path-traversal check) become refusals.
   - The package document:
     - metadata: title, creators, language, identifiers (an ISBN when one is present), publisher, description, date;
     - the manifest and spine;
     - reading direction and fixed layout;
     - the cover: EPUB 3 `cover-image`, the EPUB 2 `<meta name="cover">`, or the first image of the first page.
   - Navigation: the EPUB 3 nav document (`toc`, `page-list`, `landmarks`), or the EPUB 2 NCX (`navMap`, `pageList`).
   - Errors: not an EPUB, DRM-protected, or damaged.
   - Robolectric unit tests build books in memory, including every case from the hostile corpus.
2. **Model and Room v9:**
   - `BookFormat` (PDF or EPUB) on `Book`;
   - `books.format`, through an AutoMigration that defaults existing rows to PDF.
3. **Import** (`:core:data`):
   - the format is read from the file's first bytes, not its name;
   - an `EpubInspector` writes the cover and fills the details from the book's metadata: author, language,
     publisher, description, ISBN and date;
   - EPUBs are stored as `<id>.epub`;
   - new import outcomes, "not a book FlowFolio can open" and "DRM-protected", each with its own message.
4. **App and library:**
   - the file picker, "Open with", the share sheet and "Locate file" accept `application/epub+zip`;
   - the page count shows printed pages from the page list when the EPUB has one, and is hidden otherwise (real page
     counts depend on text size, so they come in M2);
   - the reader shows the "coming in the next update" message for EPUBs.
5. **Checks:**
   - unit tests and an on-device import test;
   - import of the IDPF samples and our generated books on the Fold, with screenshots of the library and details;
   - CHANGELOG, CLAUDE.md, and the licences matrix (no new dependency).

## Out of scope for M1

Pagination and reading (M2), typography (M3), and annotations on EPUBs (M4).
