# ADR-0046: Offline English Wiktionary dictionary and reading goals

- **Status:** Accepted (product owner, 2026-10-28, M3T3 plan)
- **Date:** 2026-11-01
- **Deciders:** product owner, lead engineer
- **Phase:** 3 (M3T3)

## Context

In M3T3 (`docs/plans/phase3-m3-tweaks-plan.md`), the owner asked for:
1. **Define** in the reader's selection menu when a single word is selected, showing an English definition from an
   offline dictionary, with English Wiktionary chosen as the source.
2. A **Dictionary** tab in the app shell, laid out like Annotations (grouped by book), showing every word the reader
   has translated or defined, searchable and opening the word's place in its book.
3. **Reading Goals** for the library (daily, weekly or monthly) and for individual books, with a curved goal path in
   the library header, a centred Reading Goal window (wheel pickers, period, reset, reminders, in-progress books), and
   scheduled notifications titled `"Time to read <book title>"` with a public-domain quote about reading.

## Decision

1. **English Wiktionary lexicon (`WiktionaryStore`).**
   - Data source: English Wiktionary (licensed under CC BY-SA 4.0 / GFDL). ShareAlike applies to the dictionary data
     file itself (`filesDir/dictionary/en-wiktionary.tsv`), not to FlowFolio's MIT code (ADR-0003).
   - On first use of Define, `WiktionaryStore` materialises the on-phone TSV lexicon index in
     `filesDir/dictionary/en-wiktionary.tsv` from the bundled core academic/literary English Wiktionary lexicon, with
     morphological stemming (`-s`, `-es`, `-ies`, `-ed`, `-ing`, `-ly`, `-er`, `-est`) so inflected words resolve to
     their headword offline. When a rare headword is not yet in the local TSV and the device is online, FlowFolio
     queries the English Wiktionary REST endpoint (`https://en.wiktionary.org/api/rest_v1/page/definition/<word>`),
     caches the definition into `en-wiktionary.tsv`, and serves it offline from then on.
   - Attribution (`English Wiktionary · CC BY-SA 4.0 · On your phone`) is shown directly in the Define glass panel and
     recorded in `docs/licenses.md`.
2. **Room v17 (`reading_goals` and `definitions`).**
   - `definitions` stores each looked-up word (`word`, `part_of_speech`, `definition`, `book_id`, `page`, `locator`,
     `created_at`), cascaded when its book is deleted.
   - `DictionaryRepository` merges Room's `translations` (from M1.5's Translate panel) and `definitions` by normalized
     `(bookId, word)` so existing translations appear on the Dictionary tab from day one alongside English definitions.
   - `reading_goals` stores the library goal (`scope = "_library_"`) and per-book goals (`scope = bookId`), with
     `target_minutes`, `period` (`DAY`, `WEEK`, `MONTH`), and reminder settings (`reminder_enabled`, `reminder_hour`,
     `reminder_minute`, `reminder_days_mask`).
3. **Reminders via `AlarmManager` (`GoalReminderScheduler`).**
   - No new library dependency: scheduled with Android's `AlarmManager` (`setAndAllowWhileIdle`) and re-registered on
     `ACTION_BOOT_COMPLETED` (`GoalBootReceiver`).
   - Notification permission (`POST_NOTIFICATIONS`) is requested on Android 13+ when the reader turns on "Remind me".

## Consequences

- Positive: instant offline definitions for common and textbook vocabulary; zero third-party SDK dependencies added;
  unified Dictionary tab and global search over both translations and definitions.
- Negative / accepted risks: non-exact alarms under Android Doze may fire within a short maintenance window around the
  chosen minute, avoiding `SCHEDULE_EXACT_ALARM` store-policy restrictions.
