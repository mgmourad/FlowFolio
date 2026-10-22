# ADR-0029: Rustic theme in plain colours, without the wood grain

- **Status:** Accepted (product owner, 2026-10-22: "the pattern in the rustic theme of the app is not good. I'd
  prefer sticking to colors without any patterns.")
- **Date:** 2026-10-22
- **Deciders:** product owner, lead engineer
- **Phase:** 2 (M3)
- **Supersedes:** the wood-grain texture in ADR-0028's Rustic theme (the rest of ADR-0028 stands)

## Context

ADR-0028 laid a generated wood grain behind the Rustic theme's screens. On the Fold the owner found the pattern
distracting and asked for plain colours.

## Decision

- Rustic's light and dark palettes use `SurfaceTexture.NONE`: the radial background gradient and the theme's colours
  only. The wood-grain texture and its generator are removed; `SurfaceTexture` is `GRAIN` (FlowFolio) or `NONE`.
- The shelf plank keeps its own gradient (part of the mock's design in every theme).

## Consequences

- Positive: calmer screens; one generated bitmap fewer.
- Negative: none known. A texture can come back as a new `SurfaceTexture` value if the owner wants one later.
