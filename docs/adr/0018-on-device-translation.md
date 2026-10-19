# ADR-0018: Translate selected text on the phone with ML Kit

- **Status:** Proposed
- **Date:** 2026-10-19
- **Deciders:** product owner, lead engineer
- **Phase:** 1 (milestone 5, after slice 7)
- **Supersedes:** the Translate part of [ADR-0017](0017-online-services.md). The Open Library part still stands.

## Context

ADR-0017 showed Google Translate's web page in a WebView. The owner rejected it on 2026-10-18 because it looks like
Google's page, not ours. They asked instead for:
- an API behind our own panel, in our design;
- the source and target languages shown and changeable;
- a history of earlier translations.

When asked, the owner chose **ML Kit on-device translation** over a cloud API: a cloud API needs a key, and a key
cannot be kept secret inside an app.

## Decision

- **Translation:** `com.google.mlkit:translate` 17.0.3.
- **Language detection:** `com.google.mlkit:language-id` 17.0.6, added by the lead engineer so the panel can show
  the detected source language. Disclosed to the owner afterwards; same vendor and terms.
  - Below 4 words it needs 90% confidence, otherwise 50%. When unsure, the source falls back to the phone's language
    and the reader picks it.
- **How it works:**
  - Models download from Google the first time a language pair is used. They are about 30 MB per language, and
    English is always present.
  - After that, translation runs entirely on the phone.
- **The panel** (`TranslateOverlay`, glass, our type and colours) shows:
  - the passage;
  - the From and To chips, each with a menu, and a swap button;
  - the result, with Copy and Share;
  - progress while it detects, downloads and translates;
  - "Recent" history, with Clear.
- **Settings:** the last chosen target is remembered (`UserSettings.translateTo`).
- **History** is stored in Room v8, table `translations`, and shows the newest 50.

## Consequences

- **Positive:**
  - The passage never leaves the phone and nothing goes to Google Translate's website.
  - It works offline once the models are downloaded.
  - The panel matches the app.
- **Negative / accepted risks:**
  - **Install size:** about 17.4 MB more on an arm64 phone (`libtranslate_jni.so` 16.4 MB and
    `liblanguage_id_l2c_jni.so` 1.0 MB, stored uncompressed). Before approval the estimate given was about 4 MB,
    which was wrong.
  - **Downloads:** each new language downloads about 30 MB.
  - **Quality** is a little below online Google Translate.
  - **Usage data:** ML Kit sends anonymous usage and diagnostics data to Google (through Google's data transport).
    This covers API calls and performance only, never the text. The privacy notice must say so before release.
  - **Licence:** ML Kit is closed source, under the Google APIs Terms of Service and the ML Kit terms.
    It is not GPL, so ADR-0003 allows it; it is recorded in `docs/licenses.md`.
- **Follow-ups:**
  - Manage downloaded languages (list and delete them) in Settings.
  - Show the usage data in the in-app licences/privacy screen (known issue 8).
