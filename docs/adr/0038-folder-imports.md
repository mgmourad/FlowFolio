# ADR-0038: Import several files or a whole folder; folders are book paths

- **Status:** Accepted (product owner, 2026-10-26: import multiple files, import a folder recursively, a Folders tab to
  browse it; chose "Follow the import setting" for how a folder's books are stored)
- **Date:** 2026-10-26
- **Deciders:** product owner, lead engineer
- **Phase:** 2 (between M3 and M4)

## Context

Books were imported one file at a time. The owner wants:
- several files picked at once;
- a whole folder imported with every folder inside it;
- a "Folders" chip in the library to browse what was imported, with the same tiles and list, menus and select mode as
  a shelf's page.

## Decision

1. **Pickers.** The + button offers "Import files…" (the system's multi-select picker, `OpenMultipleDocuments`) and
   "Import a folder…" (`OpenDocumentTree`).
2. **The walk.** `DocumentSource.listFolder` walks the folder breadth first with `DocumentsContract`, with no new
   dependency.
   - It finds PDFs and EPUBs by MIME type or extension.
   - Hidden folders are skipped.
   - It stops after 5,000 folders.
3. **Storage follows the import setting.**
   - When copying, each file is copied as before.
   - When reading in place, FlowFolio keeps a persisted read grant on the folder once, and its books are read through
     it, without a grant per file.
   - If the provider refuses, the reader is offered "Copy instead".
4. **Folders are paths, not records.** Each book keeps the folder it came in, as `books.folder` (Room v12, for example
   "Books/Physics", the picked folder's own name first).
   - The Folders view is computed from those paths, so a folder exists while it holds books.
   - Importing a folder again puts already-imported books into it; if they were already in another folder, they stay
     there.
5. **One page for shelves and folders.** A folder's page is the shelf page given a folder path: its folders first, then
   its books, as tiles or a list.
   - Press and hold opens a folder (Open, Remove from library).
   - Select mode chooses folders and books together. Removing a folder removes every book in it, at any depth, after
     confirming.
6. **Batches report once.** Progress shows "Importing n of N", and a single message at the end says how many books were
   added, were already there, or couldn't be opened.

## Consequences

- No new table and no migration beyond one nullable column. Moving a book between folders isn't possible, since folders
  mirror where files were.
- A folder grant held for reading in place isn't released when its books are removed; Android caps grants per app
  (512), which is far from reach.
- Removing a folder deletes FlowFolio's copies of its books (as removing a book does); originals read in place are
  untouched.
