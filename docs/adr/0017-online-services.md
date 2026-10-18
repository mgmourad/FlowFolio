# ADR-0017: Use two online services, Google Translate in a web view and Open Library for book details

- **Status:** Proposed
- **Date:** 2026-10-18
- **Deciders:** product owner, lead engineer
- **Phase:** 1 (milestone 5, slice 7)

## Context

Until now, FlowFolio had no network access. Two owner requests need it:

1. **Translate a selected passage** (2026-10-18). It must use Google Translate and show the result as an overlay,
   without leaving the app.
2. **Book details from the internet** (M5 plan, slice 7). This is an Open Library lookup by ISBN, or by title and
   author. The owner decided it is off by default, the app asks about it at the first import, and Settings has a
   switch for it.

Our rules still apply:
- no new dependencies without agreement;
- no secrets in the repository;
- the user decides what leaves the phone.

## Options considered

### Translate

1. **Google Translate's web page in an in-app WebView** (`translate.google.com/?sl=auto&tl=<phone language>&text=...`).
   - Pros: no API key, no dependency, the real Google Translate.
   - Cons: depends on Google's page layout, and the user sees Google's page rather than our own styling.
2. **Google Cloud Translation API.**
   - Needs an API key and billing, and a key cannot be kept secret inside an app.
   - Rejected.
3. **ML Kit on-device translation.**
   - Pros: private and works offline.
   - Cons: it is Google's but not "Google Translate", adds a dependency, and downloads about 30 MB per language.
   - Kept as a possible follow-up for privacy.

### Book details

1. **Open Library's public JSON API** over `HttpURLConnection` and `org.json`, both built into Android.
   - Pros: no key, no dependency, open data (CC0 for most records).
2. **Google Books API.**
   - Needs a key for reliable quotas.
   - Its data has terms of use.

## Decision

### Translate

- Translate uses option 1. The WebView only loads `https://*.google.com` pages; any other link is refused.
- The panel says the passage is sent to Google.
- The WebView keeps no file or content access.

### Book details

- Details use Open Library: `/isbn/<isbn>.json` when the book has an ISBN, else `/search.json?title=&author=`.
- Then `/works/<id>.json` for the description and `/authors/<id>.json` for the bio.
- **Off by default.** Turn it on in Settings > Library > "Look up book details online". The first import asks once.
- **Sent:** only the ISBN, or the title and author, from that book.
- **Suggestions, not overwrites.** Results only fill fields that are empty; anything the reader typed is kept.
- **Manual lookup.** "Look up online" in Edit details runs it on demand and shows what it found before saving.

Both services use the INTERNET permission, which the app now declares.

## Consequences

- **Positive:**
  - No dependency, no key and no account.
  - Translate is the real Google Translate.
  - Details fill themselves in for books with standard metadata.
- **Negative / accepted risks:**
  - Translate sends the selected passage to Google, and the panel says so.
  - Google may change its page, and its consent screens show inside the panel.
  - Open Library coverage is uneven for textbooks; when nothing is found, nothing changes.
- **Follow-ups:**
  - On-device translation (ML Kit) as a privacy option, for the owner to decide.
  - Cover images from Open Library: not now, since our covers come from page 1.
