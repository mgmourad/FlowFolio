# ADR-0053: Keep Narrator Mode playing in the background with a media session and two players

- **Status:** Accepted
- **Date:** 2026-11-10
- **Deciders:** product owner, lead engineer
- **Phase:** 6.5 (between Phase 6 and Phase 7), shipped in 0.6.0-canary.4

## Context

The owner asked (2026-11-10) that Narrator Mode keep reading after leaving the app and locking the phone. Until
then the narrator lived in the reader's view model with no foreground service, so Android could freeze or kill it in
the background, and nothing outside the book could pause it. The owner also asked for:

- the phone's own player: lock screen, quick settings and headsets;
- album art made from the book's cover, fitted rather than stretched, with the app icon on its bottom right;
- the title "FlowFolio: <Book Title> - <Author> - <Chapter>";
- previous and next moving by paragraph, page, chapter or subchapter, chosen in Settings, Sound & voice;
- a persistent notification player of FlowFolio's own, with a playback speed control, since the phone's player has
  no speed;
- no seekbar anywhere.

## Options considered

1. **AndroidX Media3** (`media3-session`, `MediaSessionService`). It is the modern library and handles the
   notification for you. But it is a new dependency (and needs owner approval). It wants a `Player` to drive, and our
   narrator is a streaming TTS pipeline, not a media item with a duration. Its automatic notification is the media
   notification only, so FlowFolio's own speed player would be extra work anyway.
2. **The framework's `MediaSession`, `Notification.MediaStyle` and a foreground service of type `mediaPlayback`.**
   These have been in the platform since well before minSdk 29, and need no dependency. The narrator stays where it
   is.
3. **Move the whole narrator into a service.** This makes playback independent of the reader. It means moving
   page text, pagination, page turning and highlight sync out of `ReaderViewModel`, a large refactor for no change
   the reader would notice.

## Decision

Option 2.

- **`NarratorService`** (`:feature:reader`) is a `mediaPlayback` foreground service started when narration starts.
  It holds the `MediaSession` and a partial wake lock while the voice speaks.
- **Two notifications:** the session's MediaStyle notification (the phone's player) and FlowFolio's own
  `DecoratedCustomViewStyle` player with previous, play/pause, next, a speed chip showing the rate, and close.
- **The narration stays in the reader.** `NarrationHub`, an in-process bridge, carries `NowPlaying` out and
  `NarrationCommands` back. The reader keeps turning pages and keeping its place exactly as before.
- **No seekbar:** the session never publishes a duration, so Android draws none.
- **Speed** in the phone's player is a `PlaybackState.CustomAction`. In FlowFolio's player it is the chip.
- **Skip targets** come from `NarratorSkips`, which is pure Kotlin and unit-tested. They use the speech segments'
  pauses for paragraphs and the book's outline for chapters (depth 0) and subchapters (any depth).
- **Album art** comes from `NarratorArt` and `ArtGeometry`: the cover fitted inside a 720 px square on a softened copy
  of itself, with the app icon as a badge. Notifications carry a 256 px copy, because a notification's bitmaps
  travel in its parcel.

## Consequences

- Positive: narration survives the home button and a locked screen, and the phone's player, headsets, Bluetooth
  controls and FlowFolio's own player all reach the same commands. No dependency was added.
- Negative / accepted risks:
  - Leaving the book (Back to the library) still stops narration, because the narrator lives in the reader's view
    model.
  - If Android destroys the activity under memory pressure, narration stops with it. The foreground service makes
    that rare.
  - Quick settings crop the album art to a wide strip (Android's choice), so the badge can be cut there; the
    square art is intact on the lock screen and in FlowFolio's player.
  - FlowFolio's own player needs the notification permission on Android 13+; the media notification does not.
- Follow-ups (owner, Phase 7 M4 polish):
  - narration that carries on after leaving the book, if the owner wants it;
  - lock-screen art checked on the owner's Fold (the emulator has no screen lock set).
