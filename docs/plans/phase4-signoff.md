# Phase 4 sign-off: trust the conversion

- **Status:** Signed off by the product owner, 2026-11-06.
- **Plan:** `PLAN.local.md`, Phase 4 — "trust the conversion". Milestone plans: [`phase4-m1m2.md`](phase4-m1m2.md)
  (pixel verification and self-healing), [`phase4-m3.md`](phase4-m3.md) (fallbacks worth keeping),
  [`phase4-m4-syntax-highlighting.md`](phase4-m4-syntax-highlighting.md) (the owner's on-the-fly code-colouring tweak).
- **Spec:** `docs/conversion-spec.md`, `docs/schemas/flowfolio.schema.json`.
- **Decisions to ratify with this sign-off:** ADR-0050 (release-channel renumbering, superseding ADR-0042's channel
  section). It is `Proposed`; the recommendation is to accept it (see below).
- **Measured on:** the reference Fold (SM-F976U, folded cover screen, Android 17) for the reader work; the JVM corpus
  gate on Linux for the conversion engine (its tests do not run on macOS arm64, known issue 37).

## What Phase 4 delivered

The phase's goal was that the converter could tell when it had got a book wrong, and that the places it gave up on
were good to read. The first half of that changed shape as it was built; the second half landed as planned. It is
summarised by milestone.

- **M1+M2 — geometric verification (diagnostic), self-healing reconsidered.** `GeometricVerify` adds
  `geometricCoverage` to the sidecar and `Metrics`: per page, the share of text whose glyph boxes agree with their
  declared size — the `sizeRatio` check that would have caught the Packt bug (fonts reported as `size=1.0` against
  ~10 pt glyphs, a ratio of 10, where character coverage never noticed). **The metric is recorded, not gated.** The
  CI gate and the self-healing fallback it was meant to drive were removed during the build: the size-vs-glyph signal
  has false positives (text-matrix scaling in OpenStax PDFs), so it mis-flags healthy pages. A *real* pixel diff
  (mechanism B, a headless HTML render) is the honest version of this idea and is deferred. See "Exit criteria".
- **M3 — fallbacks worth keeping.** Covers pass through as full-page images: a first/last page that is a *design*
  (graphics with little text), not a title, index or colophon page that merely sits on an edge. Fallback and figure
  crops are sharper (figure 3 px/pt = 216 dpi, page 2 px/pt = 144 dpi); captions pair with equations as well as
  figures and tables; a table wider than the column spans the full width; a pulsing magnifier hint marks pictures
  that open when tapped. The converter is bumped to engine **1.4.0**.
- **M4 — syntax highlighting** (the owner's tweak). `CodeHighlighter` (pure JVM) detects the language of a code
  listing from its keywords and comment markers across seven languages and tokenises keywords, strings, comments and
  numbers; the reader colours them.
- **M4 — known conversion defects.** Spurious spaces in justified text ("ver y"): the word-space threshold was raised
  (`WORD_GAP_EMS` 0.25 → 0.32). The other two are **consciously accepted**: the `size=1.0` heading question and the
  taps-didn't-turn-pages report were dismissed by the owner ("the DevOps book is working fine for now"; "taps turn
  pages fine").
- **M5 — the update check.** The app checks the releases page when it opens or returns to the foreground (throttled),
  drops an in-app banner when a newer build exists, and tapping it opens Settings at the update section, which now has
  a "What's new" link for the release notes.
- **Owner's report during M5.** Folded (single-page) page turns now show the *back* of the sheet — the next page —
  while it curls, the way the unfolded spread already did. The single-page reader was passing no `backPage`, so the
  curl fell back to a translucent mirror of the front.

**Release.** One pre-release ships from Phase 4: **0.3.0-alpha.6 (300006)**.

## Exit criteria

| Criterion (phase plan) | Result | Evidence |
|---|---|---|
| Pixel coverage is in the metrics and the CI gate | **Partial.** `geometricCoverage` is in the sidecar and `Metrics`; it is **not gated** in CI. The geometric signal false-positives on text-matrix-scaled PDFs, so gating on it would block good books. | `GeometricVerify`; `Converter.kt:130`; `CorpusTest` prints the number |
| A deliberately broken conversion is caught by CI rather than by a reader | **Deferred.** This needs the real pixel diff (mechanism B: render the original and the converted page through a headless HTML renderer), a new dependency that wants its own ADR and owner approval. | `phase4-m1m2.md` mechanism B |
| The three known conversion defects are fixed or consciously accepted | **Met.** Spurious spaces fixed (`WORD_GAP_EMS` 0.32, untested against the DevOps book); `size=1.0` headings and the taps report consciously accepted. | `Extraction.kt`; owner |
| Fallback images look deliberate | **Met.** Full-page covers, sharper crops, captions with equations, full-width tables, a tap hint. | reader on the Fold |

The phase's "done when" said the point of pixel verification was to *catch a broken conversion automatically*. That
specific outcome was not reached: the geometric check is recorded but not acted on, and the honest pixel diff is
deferred. This is recorded, not hidden, because it is the one place Phase 4 fell short of its own plan — the reasoning
(the false-positive rate) is sound, but the outcome is "diagnosed, not yet enforced".

## Ratify ADR-0050

One ADR describing shipped scope is `Proposed`:

| ADR | What it decided | Why accept |
|---|---|---|
| [0050](../adr/0050-release-channel-renumbering.md) | Renumber the release channels: 3–4 alpha, 5–6 canary, 7 beta (superseding ADR-0042's channel section) | The 2026-11-05 renumbering already happened in `PLAN.local.md`; this records it so ADR-0042 (accepted, never edited) is superseded rather than silently wrong. |

**Done when:** this sign-off is accepted and no ADR describing shipped code is still `Proposed`.

## Known limits carried forward

- **Geometric coverage is diagnostic-only.** It does not gate CI and does not trigger a fallback. The Packt class of
  bug is still caught by a reader, not by the machine, until the real pixel diff (mechanism B) is built.
- **Spurious-spaces fix is untested** against the DevOps book it was reported from (pages 49–50 and 101–102). It was
  loosened in the safe direction but needs that book to hand to confirm.
- **The `size=1.0` heading question** (known issue 46) was accepted without verification; the DevOps book was the only
  such PDF to hand and it is "fine for now".
- **Carry-overs from earlier phases:** island zoom on the cover screen, and equation spreads taking a little over 300 ms
  after a size change, were never checked on a device. The converter's JVM tests still do not run on macOS arm64.

## What comes next

Phase 5 — the product flow around conversion (`PLAN.local.md`): progressive delivery (chapters readable as they finish),
the health badge from the metrics already in `flowfolio.json`, and pacing for slower phones. The deferred real pixel
diff is the natural place to finally enforce "a broken conversion is caught before a reader sees it", and belongs
either as a Phase 5 milestone or its own ADR.
