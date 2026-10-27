# ADR-0037: Add Google Books as a second source for book details

- **Status:** Accepted (product owner, 2026-10-26: "make sure we get all the possible details with a robust api calls";
  chose "Open Library + Google Books")
- **Date:** 2026-10-26
- **Deciders:** product owner, lead engineer
- **Phase:** 2 (between M3 and M4)
- **Amends:** ADR-0017 (book details from Open Library only)

## Context

ADR-0017 fills a book's details from Open Library. It often leaves gaps: newer books and books outside English have
short or missing descriptions, and the lookup never read the printed page count, subjects, series or ratings. The owner
asked for every detail that can be found, from reliable calls.

## Decision

1. **Ask two services at once.** `CombinedLookup` asks Open Library and Google Books (`www.googleapis.com/books/v1/
   volumes`, no API key, no account) at the same time.
   - Open Library's answer comes first; Google Books fills what it leaves empty.
   - The longer description wins.
   - Subjects from both are pooled (at most 8).
   - The rating with more ratings behind it wins.
   - If one service fails, the other's answer stands.
2. **Same privacy rule as ADR-0017.** Each service gets only the ISBN, or the title and author. The lookup stays off
   until the reader agrees, and the consent text and the Settings line now name both services.
3. **Read more of each answer.**
   - Open Library adds the printed page count, series, subjects (catalogue terms such as "Accessible book" are left out)
     and its ratings (`/works/<id>/ratings.json`; search results carry them already).
   - Google Books gives the same fields plus categories. Its HTML description is turned into plain text.
4. **Robust calls.**
   - A dropped connection, a 5xx or a 429 is tried once more after 0.8 s.
   - A Google title search whose result has another title is dropped rather than shown.
5. **Stored in Room v12** as `subjects`, `print_pages`, `series`, `rating` and `ratings_count`. The details page shows
   them, and they survive Edit details.

## Consequences

- More books get a description, subjects and a rating.
- A second company (Google) receives the ISBN, or the title and author, of books the reader looks up. The reader
  agrees to this explicitly.
- Google's keyless quota is shared per IP address. Heavy use can be refused (429); then the retry runs, and after it
  Open Library's answer alone stands.
- No new dependency: `HttpURLConnection` and `org.json`, as before.
