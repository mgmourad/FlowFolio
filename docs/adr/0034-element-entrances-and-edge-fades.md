# ADR-0034: Element entrances on every visit, zoom through between tabs, and edge fades

- **Status:** Accepted (lead engineer, 2026-10-23; owner: "the elements of every page I transition to [should] animate
  like when you unlock an iphone screen and the icons fall [on]to the home screen ... across the entire app and while
  the book is open for reading"; "add a blurred fade on the top and the bottom of the screen ... of the same color as the
  theme")
- **Date:** 2026-10-23
- **Deciders:** lead engineer
- **Phase:** 2 (M3)
- **Supersedes:** ADR-0033 in part (the tab transition); amends the owner's 2026-10-18 rule that a screen's entrance
  plays only the first time

## Context

Screens entered with the mock's staggered rise (`reveal`), only on their first appearance, so a cover flying home lands
on a still shelf. The owner wants the entrance on every visit, in the manner of the home screen's icons settling after
unlocking, and the app's content not to run into the status bar, the camera and the navigation bar.

## Options considered

1. **One entrance clock per visit; each element falls from 112% to its size, staggered** (chosen). Elements read the
   clock in the draw phase, so the wave costs no recompositions; elements composed after their turn (rows scrolled into
   view later) simply appear, so a list never animates while scrolling.
2. **Per-element animations started on composition** (the old `reveal` without its "first time" rule): rows scrolled
   into view would animate one by one, long after the screen arrived.
3. **Shared-element or physics-based entrances**: heavier, and the cover flight already covers the one shared element.

For the edges: a gradient alone (no blur) reads as a band of colour; a blur alone leaves the bars' contrast to chance.
Chosen: Haze's progressive blur (strongest at the edge, none where the fade ends) under a gradient of the colour the
background has at that edge. Haze is already a dependency.

## Decision

- **Entrances** (`ScreenEntrance`, `Modifier.reveal(order)`): every screen and page, every visit. An element starts
  `order` x 40 ms in and fades in while shrinking from 112% to its size over 560 ms on Material's emphasized curve.
  Library: the header, chips and Continue Reading, then each shelf's name and its books one by one; lists and grids by
  position. The details page settles around the cover flying in.
- **Still landings stay:** a screen a cover is flying home to (Back from details, closing a book) does not play it.
- **Tabs: zoom through.** The old tab fades out and recedes to 96% in 110 ms; the new one settles from 105% over 420 ms.
  Pages keep the shared axis, details and reader the plain fade (ADR-0033).
- **Reader:** each time the bars show, their buttons fall in one after another and the riffle dock follows; the bars
  fade as they slide.
- **Edge fades** (`EdgeFades`, in the app shell): status bar height + 28 dp at the top, navigation bar height + 24 dp
  at the bottom, on every screen but the reader, under the tab bar and rail.

## Consequences

- Positive: one consistent, lively entrance everywhere; content melts into the theme at the screen's edges.
- Negative: while falling, elements scale with no offscreen layer (an offscreen layer would clip glows and shadows to
  their bounds); a layer exists only while an element is falling, then it is dropped. Text glows (GPU blurs, Known
  issue 21) are re-drawn at each scale during that half second; GPU memory is checked on the Fold after tab switches.
  All screens but the reader are now recorded for the glass blur (the fades need it), a small cost on pages.
