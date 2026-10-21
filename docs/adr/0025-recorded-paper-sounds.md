# ADR-0025: Use the owner's recorded paper sounds for the riffle scrubber

- **Status:** Accepted (product owner, 2026-10-21: recorded the four sounds and asked for them)
- **Date:** 2026-10-21
- **Deciders:** product owner, lead engineer
- **Phase:** 2 (milestone M2b)
- **Supersedes:** the sound part of ADR-0023 (its haptics mapping stands)

## Context

ADR-0023 chose generated paper sounds: short flicks as pages pass, a thump at chapter starts and a whoosh per landing
sheet. Tried on the Fold, the owner did not like them and recorded four sounds instead: a page flip forward and one
backward, and a riffle through pages forward and one backward. They are the owner's own recordings, so the project can
ship them under its MIT licence with no third-party terms.

## Options considered

1. **Keep the generated flicks and tune them.** Small files, but the owner has rejected the character of the sound.
2. **Play the recordings as one-shots per step of a drag.** A one-second riffle restarted every few frames would stutter.
3. **Loop the riffle recording while the ribbon drags, and play a flip per landing.** The loop's loudness and playback
   rate follow how fast pages pass, so a slow drag sounds like a slow riffle and a fast one like a fast riffle.

## Decision

Option 3.
- `tools/sounds/prepare_recorded_sounds.py` (numpy, scipy) converts the originals (24-bit, 44.1 and 96 kHz) to 16-bit
  44.1 kHz stereo WAV, trims silent ends with 10 ms fades, brings each to the same loudness, and crossfades each riffle's
  end into its start (150 ms) so it loops without a seam. The results (about 1 MB in all) are `res/raw/flip_forward`,
  `flip_backward`, `riffle_forward` and `riffle_backward` in `:feature:reader:riffle`; the originals stay with the owner.
- While dragging, `RiffleSounds` loops the riffle for the drag's direction (toward the end of the book or its start),
  switching recordings when the direction changes. Its rate follows the measured speed around 25 units a second (0.75x
  to 1.6x) and its volume rises with speed (0.35 to 0.9). It fades out over about 100 ms once no page has passed for
  120 ms, or when the ribbon is let go.
- Each landing (a release, Go to page, a nudge arrow, a flag) plays the flip in its direction once.
- No sound at chapter starts any more; the chapter haptic (ADR-0023) still marks them.
- Unchanged from ADR-0023: `SoundPool`, nothing in silent or vibrate ringer modes, and the "Paper sounds" setting.

## Consequences

- Positive:
  - The sounds are the owner's choice and real paper.
  - One stream loops during a drag instead of a burst of short sounds.
- Negative / accepted risks:
  - About 1 MB of audio in the APK (the generated sounds were 16 KB). Compressing to Ogg would need an encoder; not
    worth a new tool for now.
  - Playing the loop faster raises its pitch as well as its pace (`SoundPool` has no time stretch).
- Follow-ups:
  - **M2b (product owner):** listen on the Fold and say whether the speed range and loudness feel right.
