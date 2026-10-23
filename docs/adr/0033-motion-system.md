# ADR-0033: The app's motion system

- **Status:** Accepted (lead engineer, 2026-10-23; owner: "modern and sleek transition animations across the board ...
  pick the best fit")
- **Date:** 2026-10-23
- **Deciders:** lead engineer
- **Phase:** 2 (M3)
- **Extends:** ADR-0015 (the mock's motion tokens, `Motion`)

## Context

Screens changed with one plain cross-fade, whatever the relation between them. The details page and the reader already
have their own object motion (the cover flight, the book opening, the theme and paper reveals), which must keep working.

## Options considered

1. **Material 3's motion patterns, chosen per relation** (chosen): fade through between peers, shared axis into and out
   of pages, container motion where an object carries the transition (our cover flight and book opening).
2. **One slide for everything** (iOS-style push): reads well for drilling in, wrong for switching tabs.
3. **Shared elements everywhere**: the cover flight already does this for the one place it matters; more would cost
   frames and memory on the library's 3D books.

## Decision

- **Tabs (Library, Shelves, Bookmarks, Annotations, Settings): fade through.** The old tab fades out in 90 ms; the new
  one fades in over 260 ms while growing from 94%, on Material's emphasized decelerate curve.
- **Pages (a shelf, a book's bookmarks or annotations, edit details): shared axis X.** The page slides in from the end by
  12% of the width as the old one drifts back the other way, 380 ms, reversed on Back. Android 16's predictive back
  gesture (on by default at target SDK 37) scrubs this transition.
- **Details and reader: a plain fade.** The cover flight and the book opening carry the motion; a sliding page would
  move the spot the cover lands on.
- **Within screens:** items in lists and grids glide to their new places and fade in or out (`animateItem`), so a
  deletion closes the gap smoothly; the header's buttons cross-fade into the selection bar; selection checks pop in; the
  details page's tab content slides in from the side its tab lies on.
- All motion follows the system's animation scale, so "Remove animations" turns it off.

## Consequences

- Positive: each kind of move has one consistent motion; the existing object motion is untouched.
- Negative: page transitions draw two screens for 380 ms; on the 3D-heavy library this is within the frame budget on
  the Fold, and the library is not a page target.
