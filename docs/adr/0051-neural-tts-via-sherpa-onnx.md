# ADR-0051: Offline neural TTS via sherpa-onnx (Kokoro and Piper)

- **Status:** Accepted (product owner, 2026-11-08, with the Phase 6 sign-off)
- **Date:** 2026-11-08
- **Deciders:** product owner, lead engineer
- **Phase:** 6 (M3 — Narrator Mode)

## Context

Phase 6 M3 adds **Narrator Mode** — reading a book aloud offline, either page by page or continuously across pages with
automatic page turns.

An initial implementation used Android's built-in `android.speech.tts.TextToSpeech`, selecting the highest-quality
offline voice installed on the device. On the owner's reference Fold (`RFGL7318RKK`), the system voices sounded
robotic and uneven, falling short of the natural audiobook-like bar the owner wanted for long-form reading.

Bundling a neural TTS model inside the APK would add 60–165 MB to every install even for readers who never use Narrator
Mode. In addition, many open-source TTS pipelines depend on `espeak-ng` for phonemization, which is licensed under
**GPL-3.0** and is therefore barred from the app by ADR-0003.

## Options considered

1. **Keep Android's `TextToSpeech` engine.** Zero binary size and zero dependencies, but voice quality depends entirely
   on the OEM's installed engine and sounds noticeably synthetic on long passages. Rejected by the owner after device
   testing.
2. **Cloud TTS APIs (Google Cloud TTS, ElevenLabs, OpenAI).** High quality, but requires network connectivity, recurring
   API costs or user keys, and sends book text off the device — violating FlowFolio's offline-first and privacy
   principles. Rejected.
3. **`sherpa-onnx` (`k2-fsa/sherpa-onnx`, Apache-2.0) running int8 ONNX neural models downloaded on first use, with
   CMUDict lexicon phonemization.**
   - `sherpa-onnx` v2.0.1 is Apache-2.0 and ships prebuilt Android JNI libraries (`libsherpa-onnx-jni.so`,
     `libonnxruntime.so`) that load models directly from app storage (`assetManager = null`).
   - Phonemization uses plain text lexicons (`lexicon-us-en.txt` / `lexicon-en-us.txt`, CMUDict-derived) plus
     supplemental regular inflections generated on-device — **no `espeak-ng` (GPL-3.0) code or data is used**.
   - Supports both **Kokoro** (`kokoro-int8-multi-lang-v1_0`, Apache-2.0, 11 US/UK English speakers in one ~165 MB
     download) and **Piper VITS** (`en_US-amy-medium`, `en_GB-alan-medium`, MIT, ~64 MB per voice), giving the reader
     a choice between two distinct neural architectures.

## Decision

Adopt **`sherpa-onnx` v2.0.1** in a dedicated `:core:voice` Android library module, with voice packs downloaded on first
use:

1. **Build-time fetch (`SherpaOnnxConventionPlugin`).** Like PDFium (ADR-0010), `sherpa-onnx-2.0.1.aar` is downloaded
   from GitHub Releases at build time, verified against its pinned SHA-256
   (`a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`), unpacked into `:core:voice`'s build directory,
   and never committed to git.
2. **Download-on-first-use voice packs (`VoicePackDownloader`).** Models are downloaded over HTTPS to
   `getExternalFilesDir(null)/voice/` (falling back to `filesDir/voice/`), verified by SHA-256 before extraction
   (`org.apache.commons:commons-compress` `1.27.1`, Apache-2.0, for `.tar.bz2`), written atomically via `.part` staging
   files with automatic retry, and cached for offline use forever after.
3. **Two neural sources (`VoiceSpeakers`, `VoicePacks`).**
   - **Kokoro (`kokoro-int8-multi-lang-v1_0`, 24 kHz, Apache-2.0):** 11 English speakers (`Heart`, `Bella`, `Nicole`,
     `Sarah`, `Sky`, `Adam`, `Michael`, `Emma`, `Isabella`, `George`, `Lewis`) backed by one shared model download.
   - **Piper VITS (22.05 kHz, MIT):** `Amy` (`en_US-amy-medium`) and `Alan` (`en_GB-alan-medium`) as independent
     lighter-weight downloads sharing a verified English lexicon.
4. **Streaming synthesis, prosodic cadence, and audio conditioning (`Narrator`, `SpeechText`, `PcmAudio`).**
   - Book text is normalized (curly quotes, dashes, ligatures, PDFium `\u0002` and soft `\u00AD` hyphens, line-break
     and cross-page hyphenated words rejoined, running headers/folios stripped) and segmented into full sentences with
     structural pauses (`480 ms` after titles, `340 ms` after subtitles, `320 ms` after paragraphs, `150 ms` between
     sentences, and `0 ms` across mid-sentence page breaks).
   - Synthesis streams through `OfflineTts.generateWithCallback` into a bounded `ArrayBlockingQueue` feeding a persistent
     `AudioTrack`, pre-queueing the next page's opening sentence before auto-advancing so page turns have zero gap and
     play the reader's page-turn sound.
   - Raw vocoder PCM is conditioned by a 2-pole Butterworth low-pass filter at `8.2 kHz`, a soft downward expander
     below `-41 dBFS`, and `6 ms` raised-cosine edge fades to eliminate 8-bit vocoder quantization hiss.
   - `ChunkCallback` implements `Function1<FloatArray, Int?>` explicitly (with matching R8 `-keep` rules) so `kotlinc`
     emits the exact `invoke([F)Ljava/lang/Integer;` signature looked up by `libsherpa-onnx-jni.so`.

## Consequences

- **Positive:** Natural, expressive offline narration across both US and UK voices from two neural families (Kokoro and
  Piper); zero model weight bloat in the base APK for users who do not use Narrator Mode; instant pause/resume and live
  speed/voice switching; seamless mid-sentence page transitions; permissive licensing throughout (Apache-2.0 and MIT, no
  `espeak-ng`).
- **Negative / accepted:** `libonnxruntime.so` and `libsherpa-onnx-jni.so` add ~25 MB per ABI to the APK; first use of a
  voice family requires a one-time download (~156 MB for Kokoro, ~64 MB per Piper voice) with an non-intrusive progress
  overlay.
