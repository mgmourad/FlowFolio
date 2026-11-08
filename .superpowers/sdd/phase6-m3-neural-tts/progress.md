# SDD ledger — plan: docs/plans/phase6-m3-neural-tts.md

Executing inline (native). Branch: feat/m3-neural-tts. Base of work: b823655 (plan finalization).

## Pre-flight

- Plan tasks (from plan doc + prose): (1) sherpa-onnx AAR + :core:voice module; (2) VoiceEngine wrapper + speaker map; (3) PCM conversion/playback; (4) voice-pack download manager; (5) wire into reader (replace TextToSpeech, voice picker, download prompt); (6) on-device verify.
- Interfaces: (1) produces `:core:voice` + `VoiceEngine`/`VoiceSpeakers`/`VoicePackDownloader`; (2)-(4) consume (1); (5) consumes all; (6) consumes all.
- Rulings: none yet.
