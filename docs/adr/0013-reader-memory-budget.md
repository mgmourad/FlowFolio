# ADR-0013: Reader memory budget relative to the app's floor

- **Status:** Accepted (product owner, 2026-10-17)
- **Date:** 2026-10-17
- **Phase:** 1 (milestone 3)
- **Evidence:** measurements on the reference Fold (SM-F976U), `android/tools/measure-reader.ps1`

## Context

The Phase 1 plan set a flat target: total app memory ≤ 250 MB while reading. Measuring on the Fold showed that the
app needs 131 MB (cover screen) and 181 MB (inner screen) with no book open, most of it the system's display buffers
for a 2256×2504, 120 Hz screen. A flat number therefore says more about the screen than about the reader.

Experiments on the unfolded Fold, 150 page turns each (total PSS; reader overhead over the 181 MB floor):

| Lever | Single page | Spread |
|---|---|---|
| Baseline (32-bit pages, blurred shadow) | 394 MB (+213) | 329 MB (+148) |
| Smaller prefetch (drop the second unit ahead) | 396 MB (+215) | 336 MB (+155) |
| Shadow off | 358 MB (+177) | 290 MB (+109) |
| 16-bit pages | 325 MB (+144) | 286 MB (+105) |
| All three | 281 MB (+100) | 259 MB (+78) |

Frame timing stayed at ≥ 98% of frames on time at 120 Hz in every run. 16-bit pages were visually identical to
32-bit on text, equations, and line art; photographs may show faint banding (to be checked on a photo-heavy page).

## Options considered

1. **Keep 250 MB flat:** unreachable on the inner screen without removing the curl's realism and more.
2. **Raise the flat number:** arbitrary, and wrong for small phones and large tablets alike.
3. **Budget the reader's own cost over the app's floor on the same screen and fold state.**

## Decision

Option 3. While turning pages, the reader may add at most **110 MB** over the app's floor (library screen, same
screen and fold state), and total memory may grow at most **10%** between turn 50 and the last turn. Plus:

- Page images are **16-bit (RGB_565), GPU-backed**.
- The curl uses **gradient fold shading** instead of the blurred shadow layer (vendored pagecurl change).
- Prefetch stays as before (visible unit, next, previous, one more ahead): dropping one saved nothing measurable.

`tools/measure-reader.ps1` measures the floor and reports PASS/FAIL against these limits. Memory is sampled as
*retained* memory (the debug app collects garbage just before each sample); without that, garbage awaiting collection
read as +17 MB of "growth" in one run.

## Result with this decision (Fold, 300 turns, 2026-10-17)

| Scenario | Floor | Reader overhead (≤ 110 MB) | Growth (≤ 10%) | Frames on time (120 Hz) |
|---|---|---|---|---|
| Folded, single page | 126 MB | +58 MB | 0.7% | 98.4% (p95 12 ms) |
| Unfolded, single page | 173 MB | +101 MB | −0.3% | 98.3% (p95 12 ms) |
| Unfolded, spread | 173 MB | +78 MB | 2.9% | 98.3% (p95 12 ms) |

Visual checks: the Chapter 1 galaxy photograph at 16-bit showed no visible banding at 1:1; the gradient fold shading
reads clearly as a curling page.

## Consequences

- The target is fair across devices: the floor scales with screen size and refresh rate, the reader's cost does not.
- Small risk of colour banding in photo-heavy books; revisit with a per-book or per-page 32-bit option if seen.
- The gradient shadow is less soft than the blur; judged acceptable for the memory saved.
