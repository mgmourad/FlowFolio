# 2026-11 — sherpa-onnx (Kokoro TTS) on Android

**Verdict: viable.** sherpa-onnx integrates as a single AAR and synthesizes Kokoro offline from
`filesDir`, faster than real-time on the Fold. This confirms the download-on-first-use design.

## What was tested

- AAR `sherpa-onnx-2.0.1.aar` (49 MB, classes + `jni/` for arm64-v8a etc.) added to a throwaway
  `:spikes:sherpa-onnx` app via `implementation(files("sherpa-onnx-2.0.1.aar"))`.
- Kokoro `kokoro-int8-en-v0_19` (English, 11 speakers) + `lexicon-en-us.txt` pushed to
  `getExternalFilesDir(null)/kokoro/`.
- `OfflineTts(assetManager = null, config)` loads the model **from absolute file paths** (not
  assets), then `generate(text, sid, speed)` → `GeneratedAudio` → `AudioTrack`.

## Result (on the Fold, unfolded)

- Load: **812 ms**, `sampleRate=24000`, `speakers=11`, `lang=en-us`.
- `sid=6` (am_michael): 5.71 s audio in 3.29 s (~1.7× real-time).
- `sid=0` (af): 5.59 s audio in 4.88 s.
- WAV output verified non-silent (rms 1414, 96 % samples non-zero). Same sentence, two speakers →
  two different durations, so `sid` genuinely switches the voice.

## Key facts for the plan

- **File-path loading works** (`assetManager = null` → `newFromFile`), so models download to app
  storage at runtime — no asset bundling, no espeak-ng.
- **espeak-ng is gone in v2.0.1.** Kokoro phonemizes via a plain `lexicon-en-us.txt` (6.3 MB,
  CMUDict-based, 168 k entries). This removes the GPL-3.0 espeak-ng licensing concern.
- **Real sizes** (int8 English model): `model.int8.onnx` 128 MB, `voices.bin` 5.5 MB,
  `tokens.txt` 1 KB, `lexicon-en-us.txt` 6.3 MB → **~140 MB unpacked**. The `espeak-ng-data`
  (18 MB) shipped in the tarball is unused and can be dropped.
- **`af_heart` is not in `kokoro-en-v0_19`.** Its 11 speakers are `af, af_bella, af_nicole,
  af_sarah, af_sky, am_adam, am_michael, bf_emma, bf_isabella, bm_george, bm_lewis`. `af_heart`
  only exists in the multi-lang models (`kokoro-multi-lang-v1_0`, int8 132 MB, 53 speakers; or
  v1_1, int8 147 MB, 103 speakers). If the owner wants `af_heart` specifically, use the multi-lang
  model; otherwise `af_bella`/`af_nicole` are the closest in the smaller EN model.

## Files

- `android/spikes/sherpa-onnx/` — throwaway spike app (`-Pspikes`, never in CI). AAR is gitignored.
- `android/settings.gradle.kts` — adds `:spikes:sherpa-onnx` under the `-Pspikes` gate.
