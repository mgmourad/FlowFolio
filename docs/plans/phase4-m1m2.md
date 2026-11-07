# Phase 4 M1+M2 plan: pixel verification and self-healing

- **Status:** Draft, for the owner's approval
- **Phase plan:** `PLAN.local.md`, Phase 4 — "trust the conversion"

## Goal

- **M1 — pixel verification.** The converter can measure, per page, whether its output *looks* like the original —
  not merely whether every character is present.
- **M2 — self-healing.** Where that comparison fails badly, the converter falls back to an image of the original
  automatically, instead of emitting wrong text.

## Why

The Packt bug (code listings rendered as columns of single letters) passed text coverage, the corpus gate and the
metrics, because every character *was* present — just in the wrong place. Text coverage counts characters; it never
checks layout. Pixel coverage closes that gap.

## What exists today

- `textCoverage` (per page) in `flowfolio.json`, and `Metrics.coveredShare` gates it in CI. Character-based only.
- `PdfSource.renderImages(index, crops, scale)` renders crops of the **original** PDF page on both JVM (JNA, PNG) and
  Android (JNI, WebP); a full-page crop is already possible.
- Rich geometry in the IR: `Span.lefts` (per-character x positions), `Span.box`, `Line.box`, `Block.box`,
  `RawPage.boxes`.
- No pixel comparison anywhere in the tree (grep confirms).

## The open question: what does "render the converted page" mean?

The converter's output is *reflowed* XHTML, whose layout differs from the PDF by design. A literal pixel diff between
the PDF page and the reflowed EPUB page is therefore not meaningful. Three candidate mechanisms, settled by a spike:

- **A — geometric verification** (JVM, no new dependency). Verify the converter's *inferred layout* against the
  original glyph geometry it already extracted. Example: a code block's cell width (`Style.advance`) must match the
  actual character spacing in the PDF (`Span.lefts`). Directly targets the Packt class of bug, cheap, CI-friendly.
- **B — true pixel coverage** (JVM, new dependency). Render the original (PDFium) and the converted page through a
  headless HTML renderer, and compare. Most literal, but adds a headless browser (a dependency, needing an ADR and
  owner approval) and requires the reader's CSS to be replicated.
- **C — on-device render** (Android instrumented test). Render the converted page with the reader's own engine and
  compare to PDFium. Most faithful — it renders exactly what the reader shows — but it is a device gate, not the JVM
  corpus gate.

## Milestone 1 — pixel verification

1. **Spike (time-boxed).** Reproduce the Packt bug as a committed fixture, then try mechanism A against it (and B/C
   only if A is insufficient). Decide the mechanism.
2. **Implement the pixel metric**, computed per page, added to the sidecar as a `pixelCoverage` field and to
   `Metrics` in `tools/convert`.
3. **Wire it into the corpus gate** with baselines in `samples/golden/`, so a page below the pixel threshold fails CI.
4. **Prove it:** a deliberately-broken conversion is caught by CI, not by a reader (the "done when" test).

## Milestone 2 — self-healing

1. Where the comparison fails badly, the offending **block** (or the whole **page**) goes out as an image of the
   original instead of as wrong text.
2. Record it in the sidecar (reuse the existing `fallback` mechanism, add the *reason*), so Phase 5's health badge can
   show it.
3. Preserve the spec's invariant: a region the converter can't handle goes out as an image — **never silently wrong
   text**.

## Done when

- Pixel coverage is in the sidecar, `Metrics`, and the CI gate, with baselines.
- A deliberately-broken conversion is caught by CI, not a reader.
- Where the comparison fails, the output is an image, recorded in the sidecar.

## Decisions needed

- **The rendering mechanism** (A, B or C) — the spike's output. If it is B, a headless HTML renderer is a new
  dependency and needs an ADR plus your approval (the "ask before adding any dependency" rule).
- **ADR-0050** (release-channel renumbering, superseding ADR-0042's channel section) is Phase 4 housekeeping and can
  be done in parallel.
