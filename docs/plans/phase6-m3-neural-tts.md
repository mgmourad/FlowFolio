# Phase 6 M3 — Narrator Mode: Neural voices (Kokoro via sherpa-onnx)

- **Status:** decided with the owner (2026-11-08). Replaces the on-device Android `TextToSpeech`
  voices, which the owner found robotic, with real neural voices run 100% offline.

## Decision

- **Engine:** Kokoro running on **sherpa-onnx** (Apache-2.0 on-device runtime). Kokoro is the most
  natural small open-source TTS; it is the "Gemini-like" bar the owner wants.
- **Model:** `kokoro-multi-lang-v1_0` (int8, 53 speakers) — chosen so `af_heart` is available
  (it is not in the English-only model). Owner-approved 2026-11-08.
- **Two default voices** are two *speakers from the one model*: `af_heart` (warm female, sid 3) and
  `am_michael` (neutral male, sid 16). One download → two voices (and the model ships 53 total, so
  more are free once downloaded).
- **Delivery:** download-on-first-use (the model is ~160 MB unpacked; too big to bundle). A prompt
  with progress before first narration, cached to app storage, checksum-verified.
- **Extensible — both Kokoro and Piper ship:** the Kokoro model provides 11 English speakers (`Heart`,
  `Bella`, `Nicole`, `Sarah`, `Sky`, `Adam`, `Michael`, `Emma`, `Isabella`, `George`, `Lewis`) from a
  single download, and two **Piper VITS** voices (`Amy` US female, `Alan` UK male) ship alongside it
  as lighter-weight (~64 MB) opt-in voice packs behind the same voice picker (ADR-0051).

## Piper voices alongside Kokoro

- Piper is lighter per-voice (~64 MB) and faster to download on constrained connections; both sources
  are offered in the narrator voice picker (`Kokoro` and `Piper`) so the reader can switch between them
  at any time, including mid-narration.

## Sizes (measured in the spike, 2026-11-08)

| | Kokoro multi-lang, int8 | Piper medium (one voice) |
|---|---|---|
| Model | ~128 MB (`model.int8.onnx`) | ~60–65 MB |
| Voices | ~26 MB (`voices.bin`, 53 speakers) | — |
| Lexicons | ~8 MB (`lexicon-us-en.txt` + `lexicon-zh.txt`) | ~6 MB |
| **Unpacked total** | **~165 MB** | ~70 MB |

The `espeak-ng-data` shipped in the tarballs is **not used** by sherpa-onnx v2.0.1 — drop it.
Kokoro fp32 is ~330 MB and fp16 ~165 MB — we use **int8**.

## Licensing (for the record)

- sherpa-onnx: Apache-2.0. Kokoro weights: Apache-2.0. Piper: MIT (code; voices vary).
- ✅ **No espeak-ng.** sherpa-onnx v2.0.1 phonemizes via a plain `lexicon-*.txt` (CMUDict-based),
  so the GPL-3.0 espeak-ng concern is gone for our path.

## Approach

- Add the sherpa-onnx **Android AAR** (`sherpa-onnx-2.0.1.aar`, 49 MB, arm64-v8a + classes) via
  `implementation(files("sherpa-onnx-2.0.1.aar"))` — **confirmed working in the spike** (native libs
  load, `OfflineTts` synthesizes). Loads the model from absolute file paths via
  `OfflineTts(assetManager = null, config)`.
- Replace `Narrator.kt`'s `TextToSpeech` with `OfflineTts` + `AudioTrack` playback. The rest of M3
  (panel, play/pause/rate/continuous, `ReaderViewModel` state) is already built and stays.
- Add a **voice pack** download manager (URL → app `filesDir`, checksum, progress state) and a
  first-use prompt.
- Voice picker: two default speakers + a per-voice **preview** button (plays a sample sentence) so
  the owner can audition and choose. (I cannot audition audio myself — the preview button is how
  the owner hears each voice.)

## Out of scope (this pass)

- Piper voice packs and other Kokoro languages (future opt-in downloads).
- Reading PDF native text (same reflowed-text path as M3 today).
- Spoken alt for tables/equations (converter follow-up, `phase6-m1.md`).

## Files

- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/Narrator.kt` — rewrite: `OfflineTts` + `AudioTrack` instead of `TextToSpeech`.
- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/VoicePack.kt` — new: download, checksum, cache, progress (StateFlow).
- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/ReaderNarratorPanel.kt` — voice picker: two speakers + preview + "download voice" state.
- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/ReaderViewModel.kt` — speaker id + pack state instead of `android.speech.tts.Voice`.
- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/ReaderPanels.kt` — `NarratorUi` (voices → speakers, download state).
- `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/ReadyReader.kt` — collect new state.
- `android/app/build.gradle.kts` / a new `:core:voice` module — sherpa-onnx AAR + native libs.
- `android/feature/reader/src/main/res/values/strings.xml` — copy.

## Verification

- `spotlessCheck detekt testDebugUnitTest -x :tools:convert:test`, `:app:assembleDebug`.
- On-device: first-use download prompt → download → pick the female default → preview → narrate;
  switch to `am_michael`; confirm no robot-voice (owner listens), pause/rate/continuous still work,
  and it all stays offline in airplane mode.
