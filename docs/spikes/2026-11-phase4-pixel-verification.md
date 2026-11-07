# Spike: geometric verification for Phase 4 pixel coverage

**Date:** 2026-11-06 · **Status:** Done, feeds Phase 4 M1

## Question

Phase 4 M1 wants a "pixel" metric that catches conversions where the text is all present but in the wrong place —
the Packt bug (code listings as columns of single letters) that passed character coverage, the corpus gate and the
metrics. Before committing to a mechanism, does **geometric verification** catch that class of bug without a headless
browser (mechanism A from `docs/plans/phase4-m1m2.md`)?

## Finding

**Yes.** The Packt bug's root cause is a *misread font size*: the PDF sets fonts at size 1 and scales them through the
text matrix, so every style reaches the converter as `size=1.0`, while the glyph boxes PDFium reports sit at their
rendered ~10pt. The code listing then laid out on `size * 0.6` = 0.6pt cells.

That mismatch is directly measurable from data the converter already holds:

- `Span.box` is the union of the rendered glyph boxes (`Extraction.lineOf`, `box = box.union(b)`) — ~10pt tall for the
  Packt book.
- `StyleKey.size` comes from `fontSizes` (`Extraction.styleOf`) — `1.0` for the Packt book.

So the ratio **glyph-box height ÷ declared size** is ~1 for a healthy book and ~10 for the Packt book. A threshold on
that ratio flags it.

## The check

`GeometricVerify` (`:core:conversion/stage/`) — `sizeRatio(line)` returns the median glyph-box height over the style's
declared size; `sizeIsConsistent(line)` flags a ratio outside `1/SIZE_TOLERANCE..SIZE_TOLERANCE` (`SIZE_TOLERANCE =
2.5`).

`GeometricVerifyTest` proves it on both cases: a 10pt style over 10pt glyphs is consistent; the Packt size-1 style
over 10pt glyphs is flagged (ratio 10).

## What this means for M1

Mechanism A is viable and CI-friendly (pure JVM, no new dependency, uses `Span.lefts`/`Span.box`/`StyleKey.size` that
already exist). The M1 metric generalises this one check into a per-page **geometric coverage**: the share of a page's
text lines whose inferred model agrees with their glyph geometry, written into `flowfolio.json` beside
`textCoverage` and gated in `Metrics`/CI.

Open questions to settle while building M1 (not blocking):

- **Which checks** make up the metric beyond size-consistency — code cell width (`Style.advance`) vs glyph spacing is
  the obvious second one, since that was the Packt bug's *symptom*.
- **Tolerance** — `2.5×` is a spike guess; the real threshold should be set from the corpus (healthy books' ratio
  distribution), like the 0.98 text-coverage threshold.
- **Granularity of the fallback** (M2) — flag the block, or the page; the existing `Page.fallback` mechanism is the
  hook.
