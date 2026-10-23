# ADR-0035: Edge fades blur only behind the bars; the reader's top bar gets one

- **Status:** Accepted (lead engineer, 2026-10-23; owner: "the faded blur layer on the top is touching the top edge of
  the app pages titles. Move a little bit up"; "while the book [is] open, ... put a faded blur behind the top menu when
  triggered so the top elements won't interfere with the book text")
- **Date:** 2026-10-23
- **Deciders:** lead engineer
- **Phase:** 2 (M3)
- **Supersedes:** ADR-0034 in part (how the edge fades blur)

## Context

ADR-0034's edge fades blurred their whole height with Haze's progressive blur, meant to ease out to nothing. On the
Fold (Haze 2.0.1, performance mode) the blur did not ease out: it stayed full to the fade's end, so a line of text
crossing that end was cut in two, half blurred and half sharp. Haze's mask kept the edge soft but dropped the blur.

## Decision

- Each fade blurs only its **held** part: behind the status bar or navigation bar in the app, behind the buttons in the
  reader. Past it, the tint in the theme's (or the paper's) colour fades out on its own, so no blurred edge crosses text.
- The app's top fade reaches 10 dp past the status bar (was 28 dp), clear of the screens' titles.
- **Reader:** while the bars show, a fade in the paper's colour lies behind the top bar, solid behind the buttons and
  fading out 36 dp below them; it fades in and out with the bars instead of sliding.

## Consequences

- Positive: no visible seams; the reader's buttons never sit on the book's text.
- Negative: below the bars the content fades rather than blurs. Revisit if a later Haze makes progressive blur reliable.
