# ADR-0032: Page sounds on the media stream, with a pool that repairs itself

- **Status:** Accepted (lead engineer, 2026-10-22, after the owner's report)
- **Date:** 2026-10-22
- **Deciders:** lead engineer
- **Phase:** 2 (M3)
- **Supersedes:** the audio attributes in ADR-0023 / ADR-0025 (usage `ASSISTANCE_SONIFICATION`)

## Context

The owner reported that the page flips once went silent and stayed silent whatever they did (app volume, phone
volume, ringer) until the phone restarted. The sounds were played by a `SoundPool` as system sonification. On Samsung
phones that stream has its own "System" volume, can be muted by Do Not Disturb or a mode, and can be routed to a
connected watch or earbuds; none of the volume keys the owner tried control it in the reader. Separately, a
`SoundPool` whose audio tracks died with the audio service plays nothing and says nothing until it is rebuilt. The pool
was already rebuilt each time a book opened, so the restart pointed to the system side.

## Decision

- The page and riffle sounds play as `USAGE_GAME` / `CONTENT_TYPE_SONIFICATION`: the media stream, whose volume the
  hardware keys control. While the reader is open, the volume keys set the media volume
  (`Activity.volumeControlStream`).
- They still stay silent in silent and vibrate ringer modes, as before.
- If the pool refuses a sound it has loaded (`play` returns 0), it is released and built again, and the sounds load
  again.
- The log says why sounds are off (`FlowFolioSounds`: the ringer mode, or a rebuilt pool), so a silent flip can be
  explained from `adb logcat`.

## Consequences

- Positive: the volume keys in the reader now do what the reader expects; sounds no longer depend on the system-sounds
  volume, its routing or Do Not Disturb's system-sound rule; a dead pool repairs itself.
- Negative: page sounds follow the media volume, so music at a high volume also means loud flips (each sound keeps its
  own volume slider in Settings).
- The original fault could not be reproduced; this removes the likely causes rather than proving which one it was.
