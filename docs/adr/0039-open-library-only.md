# ADR-0039: Book details from Open Library only; Google Books removed

- **Status:** Accepted (product owner, 2026-10-26: "Skip the key; take Google Books out and keep Open Library only")
- **Date:** 2026-10-26
- **Deciders:** product owner, lead engineer
- **Phase:** 2 (between M3 and M4)
- **Supersedes:** ADR-0037 in part (Google Books as a second source)

## Context

ADR-0037 added Google Books, keyless, beside Open Library. Tried against the live service the same day, every keyless
request was refused with HTTP 429, "Queries per day" exhausted. Without a key, Google Books draws on one daily quota
shared by every keyless caller, so it can't be relied on. A key would mean a Google Cloud project, and a key shipped in
the app, which the owner declined.

## Decision

1. **Open Library is the only online source for book details again.** `GoogleBooksLookup` and `CombinedLookup` are
   removed, and `BookLookup` is `OpenLibraryLookup`. No request goes to Google, and the consent text and the Settings
   line name Open Library only.
2. **What ADR-0037 added to Open Library stays.**
   - Search asks for every field by name (`fields=…`). Without it, search answers lack subjects, page count and
     ratings.
   - Subjects are cleaned: catalogue terms and underscore tags are left out.
   - Ratings come from `/works/<id>/ratings.json`.
   - A dropped connection, a 5xx or a 429 is retried once.
   - Room v12's `subjects`, `print_pages`, `series`, `rating` and `ratings_count` remain, filled from Open Library.

## Consequences

- One fewer company receives what books the reader looks up.
- Books Open Library knows little about (newer titles, many outside English) keep their gaps. A key-based second source
  can be reconsidered later with its own ADR.
