# Phase 6 M3 — Narrator Mode (text-to-speech)

- **Status:** approved by the owner (2026-11-07). Offline forever, the most realistic voice available, both
  continuous (auto page-turn) and page-by-page reading. "Give images a voice" applies: equations/tables are images, so
  narration reads the prose and skips them (their spoken alt is the converter's follow-up, recorded in `phase6-m1.md`).

## Approach

- **Voice:** Android `TextToSpeech` (offline, no new dependency). Pick the most natural voice the device offers: prefer
  the Google engine (`com.google.android.tts`), then the highest-quality locale-matching voice, else the default.
- **`Narrator`** (`feature/reader`): wraps `TextToSpeech`. `speak(text, onDone)`, `pause`, `resume`, `stop`, `setRate`,
  and the voice list for the picker.
- **`NarratorViewModel`**: state (`playing`, `rate`, `voice`, `continuous`); reads the current page's reflowed text via
  `ReaderViewModel.pageText(page)`. On the utterance finishing, `continuous` turns to the next page and reads on;
  otherwise it stops.
- **UI:** a Narrator button in the reader's top bar opens a `Panel.NARRATOR` drop panel (play/pause/stop, a rate slider,
  a voice picker, a Continuous toggle). A small pill shows while narrating.

## Out of scope

- Reading the PDF view's text (this pass reads the reflowed view; a PDF's `nativePageText` is the same path, added
  later if wanted).
- Spoken alt for tables/equations (converter change).

## Files

- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/Narrator.kt` — the TTS wrapper.
- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/NarratorViewModel.kt` — state + flow.
- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/ReaderNarratorPanel.kt` — the panel.
- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/ChromeActions.kt`, `ReaderChrome.kt`,
  `ReaderPanels.kt` — the button + panel wiring.
- `android/feature/reader/src/main/res/values/strings.xml` — copy.

## Verification

- `spotlessCheck detekt testDebugUnitTest -x :tools:convert:test`, `:app:assembleDebug`.
- On the phone: open a Dynamic Flow book, start narration, confirm it reads the page, pauses/resumes, changes rate and
  voice, and (with Continuous on) advances pages on its own.
