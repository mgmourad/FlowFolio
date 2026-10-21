# ADR-0026: Page flips on every turn, with separate switches and volumes for turn and riffle sounds

- **Status:** Accepted (product owner, 2026-10-21)
- **Date:** 2026-10-21
- **Deciders:** product owner, lead engineer
- **Phase:** 2 (milestone M2b)
- **Extends:** ADR-0025 (the recorded sounds and how they play)

## Context

ADR-0025 played the owner's page flip recordings only when the riffle scrubber landed the reader somewhere, under a
single "Paper sounds" switch. The owner wants the flips while reading too, on every page turn, and each kind of sound
under its own switch and volume.

## Decision

- **A flip on every turn.** Curl turns (tap, swipe, a swipe on a spread's left page) and Slide turns play the flip
  forward or backward as the turn begins, not when it settles. The vendored pagecurl gains `PageCurlState.onTurn`,
  called when a turn is committed (a tap, a release that will turn, a left-page drag let go to turn); per CLAUDE.md
  known issue 29 it lives on the outer class, and `InternalState` only calls it through a function. Scroll has no
  turns and plays nothing. Landings (release, Go to page, the arrows, a flag) play one flip as before. Jumps from the
  contents or links play nothing.
- **Two sounds, two settings each.** "Page turn sound" and "Riffle sound", each a switch with a 0 to 1 volume slider
  (default 0.7), in the reader's "Aa" panel and in Settings. The riffle keeps the old "paper sound" DataStore key, so a
  reader who had turned that off keeps riffles off. Letting go of a slider in "Aa" plays the sound at its new
  loudness. The volume scales on top of the riffle's speed-based loudness (ADR-0025).
- One `RiffleSounds` (one `SoundPool`) per open book, shared by the pages, the scrubber and the panel through
  `LocalReaderSounds`.

## Consequences

- Positive: turning pages sounds like paper, and each sound can be quieter, louder or off on its own.
- Negative / accepted risks:
  - A change to vendored pagecurl to maintain (a callback; no new fields on `InternalState`).
  - The Material 3 slider's look differs a little from the mock's controls; a slider in the mock's style can replace it
    later in `:core:designsystem` without touching the screens.
