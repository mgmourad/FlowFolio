# ADR-0014: Adopt the owner's design language and offer Night pages

- **Status:** Accepted (product owner, 2026-10-20); colour table and typeface superseded by ADR-0015 (2026-10-17). The design-system structure
  and Night pages remain.
- **Date:** 2026-10-17
- **Deciders:** product owner, lead engineer
- **Phase:** 1

## Context

The owner supplied a reference image for the app's look in dark and light mode: colour roles (title and menu,
item, hint, sub-item and other), glyphs (shown as a bell), on and off toggles, rounded surfaces, and the effects:
a glow in dark mode, a soft shadow in light mode. Until now the app used stock Material 3 colours.

The owner also asked what dark mode should do to the book. The answer was that pages stay as printed by default,
with an option for "night pages": text inverted, photos never inverted.

## Options considered

1. **Theme Material 3 only (colour scheme).** Little code, but Material has no glow or shadow on text and glyphs,
   and its switch looks nothing like the design.
2. **Own design tokens on top of Material 3 (chosen).** A palette of the design's roles is mapped onto Material's
   colour scheme, so stock components pick it up. Lit text styles, an own toggle, own outline glyphs and a themed
   dialog cover what Material can't.
3. **Fully custom component set.** Most control, but much more code to maintain for little visible gain now.

Night pages:

1. **Invert the whole page bitmap.** Photos invert too, which the owner ruled out.
2. **Invert lightness except where PDFium reports image objects (chosen).** PDFium lists a page's objects with
   their bounds. Those areas are saved, the rest is mapped, then the saved areas are put back. This is done
   natively in the render call, so no extra bitmap is held.

## Decision

- **Palette:** colours read pixel-exact from the owner's image.

  | Role | Dark | Light |
  |---|---|---|
  | Background | `#1C0A4D` | `#FFEBC4`, edge `#FFBA59` |
  | Title, menu | `#FF8400` | `#8E5314` |
  | Item | `#00FFFF` | `#135A5A` |
  | Hint | `#9E9E9E` | `#544E47` |
  | Sub-item | `#C70C36` | `#C70C36` |
  | Glyph | `#AC2793` | `#4227AC` |
  | Toggle off thumb | `#363086` | `#7B3729` |
  | Toggle off track | `#9E9E9E` | `#9E9E9E` |
  | Toggle on thumb | `#FF8400` | `#FF7600` |
  | Toggle on track | `#32C88C` | `#32A5C8` |

- **Effects:** dark mode lights text, glyphs and toggles with an 18 px glow in their own colour. Light mode casts a
  soft drop shadow.
- **Typeface:** the system font (owner's choice).
- **Glyphs:** drawn from our own SVG-style paths, so there is no icon library dependency.
- **Night pages:** off by default, a reader setting, active only in dark mode.
  - Lightness is inverted with hue kept (a blue link stays blue), then tinted from night paper `#18084A` to ink
    `#E9E4F5`.
  - `#18084A` is the background colour moved to the nearest RGB_565 value, so 16-bit pages (ADR-0013) match the
    screen around them exactly.
  - Image objects, and form objects that contain images, keep their printed colours.

## Consequences

- **Positive:**
  - One place (`:core:designsystem`) holds the look.
  - Stock Material components follow the palette automatically.
  - Night pages costs no extra memory.
- **Negative / accepted risks:**
  - Photos are kept as whole rectangles. A figure whose image includes white margins or labels shows a white band
    on night paper.
  - Diagrams drawn as vector line art are treated as text and do invert (as in iOS Books).
  - Each night render does one pass over the pixels on the PDFium thread.
- **Follow-ups (lead engineer, later in Phase 1 or Phase 2):**
  - Optionally darken near-white margins inside images.
  - Theme the reflow (FlowFolio) mode with the same palette when it arrives.
