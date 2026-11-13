# ADR-0059: Narrator Mode on the phone's speech engine

- **Status:** Accepted (owner, 2026-11-13). Supersedes ADR-0051 (offline neural TTS with sherpa-onnx, Kokoro and
  Piper). Amends ADR-0054 (OCR's ONNX Runtime now comes from Microsoft's AAR, not from sherpa-onnx) and ADR-0058 (the
  Full edition no longer bundles voices; the downloads offer no longer offers them).
- **Date:** 2026-11-13
- **Deciders:** product owner, lead engineer

## Context

After testing the finished app, the owner was still not satisfied with Narrator Mode:
- pauses where a reader would not pause;
- acronyms said wrongly, and "live" said "lyve";
- a reader who loses their place, because nothing on the page shows what is being said.

The cause is how the text was prepared for the voice, not the Kokoro voice itself. sherpa-onnx 2.0.1 can only say
words in its lexicon (known issue 50), and the lexicon holds one pronunciation per spelling. So "live", "read" and
"US" can never follow the sentence they are in. FlowFolio worked around this with `SpeechWords`, `SpeechNumbers`,
`FlowFolioLexicon` and an inflection generator, and every new book found more words the rules missed. Cutting
sentences at commas, semicolons and colons to keep each utterance short caused the odd pauses.

ReadEra, the app that inspired FlowFolio, reads with the phone's own speech engine: Google's Speech Services on most
phones. That engine:
- starts at once, with nothing to download;
- handles numbers, acronyms and words spelled alike but said differently from context;
- offers both online voices and offline ones;
- reports the word being spoken (`UtteranceProgressListener.onRangeStart`).

## Decision

1. **Narrator Mode uses Android's `TextToSpeech`.**
   - It prefers Google's engine (`com.google.android.tts`) where it is installed, and the phone's default otherwise.
   - The manifest declares a `TTS_SERVICE` query, so the app can see the engines on Android 11+.
   - Any engine voice in the phone's languages, or in English, can be chosen. The choice is saved as
     `UserSettings.narratorVoice`.
   - An online voice that loses its connection carries on in the offline voice for the same language.
2. **The text is given whole.**
   - `SpeechText` now cuts only between sentences, never at commas, semicolons or colons, and splits a run-on
     sentence only past 1,000 characters.
   - Numbers, acronyms and symbols are left for the engine to read.
   - Pauses are added only after paragraphs and headings.
   - Each sentence is one utterance, and the next page is queued behind the current one.
3. **The spoken text is highlighted on the page.**
   - `SpokenPlaces` matches each spoken sentence to the page text word by word. It handles words broken across lines,
     and recovers after text the narrator skips.
   - The reader draws the sentence in teal and the word being said in orange, in both the PDF view and Dynamic Flow.
     The marks follow the voice and move with the page turns.
4. **sherpa-onnx, Kokoro and Piper are removed.**
   - Removed with them: the voice packs and their downloads, the lexicon workarounds, Commons Compress, and the
     Kokoro files in the Full edition.
   - Voices already downloaded (in external files `voice/`, up to 400 MB) are deleted on first start. VoiceMarks live
     in internal files `voice/` and are not touched.
5. **OCR keeps its own bridge (`OrtBridge`), now on Microsoft's `onnxruntime-android` 1.28.0 AAR** (MIT).
   - This is the same version the JVM tests use, so known issue 53's symbol clash is gone.
   - Only the AAR's `libonnxruntime.so` and C headers are taken, fetched at build time and pinned
     (`OnnxRuntimeConventionPlugin`).

## Consequences

- **Size:**
  - The Full edition is about 117 MB smaller.
  - Essential is about 3 MB smaller (64-bit ARM). sherpa-onnx's two libraries (9 MB) are gone, but Microsoft's
    runtime build is 6 MB larger than the one sherpa-onnx carried.
- **Google dependence:**
  - The voice is Google's on most phones, and online voices send the text to Google.
  - A phone without Google's services uses whatever engine is installed (for example RHVoice or eSpeak NG), and the
    panel's "More voices" opens the phone's speech settings.
  - Narrator Mode is no longer fully offline unless an offline voice is chosen. FlowFolio itself ships no voice and
    no network code for it.
- **Highlighting by word** depends on the engine reporting word ranges. Where it does not, the sentence is still
  highlighted.
- Speed and voice apply from the sentence being read, which is said again.
