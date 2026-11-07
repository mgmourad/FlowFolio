# Phase 5 design: the product flow around conversion

- **Status:** Draft, for owner sign-off.
- **Date:** 2026-11-07.
- **Scope:** M1 (progressive delivery) + M2 (health badge). F-Droid self-hosted repo is deferred to the end of
  Phase 9. Mechanism B (the real pixel diff) is redefined as a failover and deferred out of this phase. Pacing on
  slower phones folds into M1. The polish slot remains the standing end-of-phase owner slot.
- **Spec:** `docs/conversion-spec.md` §6 (progressive output) and §3–4 (sidecar health), `docs/schemas/flowfolio.schema.json`.
- **Plan:** `PLAN.local.md`, Phase 5.

## Intent and why

Conversion today is a 20–130 s wait that blocks nothing (the PDF stays readable) but delivers nothing until the very
end: a reader who turns on Dynamic Flow watches a progress ring, then the whole book appears at once. Phase 5 makes the
reflowed book appear progressively — chapters become readable as they finish — and gives a reader a way to judge how
well a book converted, from metrics the converter already computes but nothing shows.

Who it is for: a reader with a long textbook who turns on Dynamic Flow and currently waits; and a reader deciding
whether to trust a reflowed textbook (is the text complete? did pages fall back to images?).

## Goals, non-goals, anti-goals

**Goals**
- M1: the first chapter of a converting book is readable in the reflowed view before the last chapter converts; the
  reader can read while it finishes and sees chapters appear.
- M2: a reader sees at a glance how good a conversion is — text coverage, pages below threshold, fallback pages,
  geometric coverage — in the reader's "Aa" sheet and the library's book details.

**Non-goals**
- No change to the PDF view.
- No change to what a *complete* conversion produces (the final EPUB's contents are the same as today).
- No new dependency (the pixel-diff renderer is explicitly out of this phase).
- No OCR, table/equation markup, search or TTS.

**Anti-goals**
- The partial EPUB must never be a broken EPUB: EPUBCheck-clean, containing only finished chapters.
- A reader must never be shown a "ready" book that is not whole; partial is always visibly partial.
- No regression to the CI corpus gate: a complete conversion's output must stay valid against the schema and the
  baselines.

## Constraints

- On-device only (ADR-0009); conversion runs in the `:convert` process (ADR-0045).
- The reader opens a single valid EPUB file today (`ReaderRepository.openFlow`); progressive delivery must not force a
  second reader path for fragments.
- `docs/conversion-spec.md` §6 already fixes the shape: a partial bundle is a valid EPUB with `complete: false` and a
  list of finished spine items.
- Phase 5 ships canary (ADR-0050). No dependency additions (owner's rule).

## Approaches (the fork)

M1 had one real fork, settled here:

- **Single EPUB, progressively rewritten** (chosen). After the structure is known, render images chapter-by-chapter;
  after each chapter, rewrite the ZIP with the chapters finished so far. The reader opens the growing partial and picks
  up chapters. Chosen because it is exactly what the spec mandates ("a partial bundle must still be a valid EPUB
  containing only finished chapters") and the reader already opens one valid EPUB file. Cost: re-zipping per chapter;
  images are stored uncompressed (`STORED`), so this is CRC + I/O, seconds over a full book.
- **Chapter fragments merged at the end** (rejected). Emit per-chapter XHTML + images as they finish, assemble the
  final EPUB at completion. Rejected: it needs a new reader path for non-EPUB fragments, and a late merge that can still
  fail after a reader has begun reading — the wait M1 exists to remove; and it contradicts the spec's "partial bundle
  is a valid EPUB".

## The how

### M1: progressive delivery

1. **Chapter-granular image rendering.** `Converter` keeps `read` + `analyse` unchanged (all chapters are known before
   the slow render begins — reading+layout is ~46% of the time, images ~52%). `images` becomes per-chapter: render a
   chapter's crops, hand them to a writer, then the next chapter. Rendering order and parameters are unchanged, so a
   chapter's images are byte-for-byte what they are today.
2. **Partial writers.** `EpubWriter` gains a "chapters so far" mode: the spine, manifest, nav page-list and images
   cover only chapters 0..N. `SidecarBuilder` writes `complete: false` and a new `finishedSpineItems: [<file>, …]`
   listing the finished chapter files (spec §6). On the final chapter both write the full package with `complete: true`,
   and `finishedSpineItems` is omitted — so a complete sidecar stays byte-identical to today's, no engine-version bump,
   no mass re-conversion, and the CI baselines stay valid.
3. **Atomic partial file.** Each chapter's rewrite goes to a temp name and is renamed over `book.epub.part`, so the
   reader never opens a half-written ZIP. On completion the `.part` is renamed to `book.epub` (as today); `FlowFiles.ready`
   keeps meaning "whole and current."
4. **Protocol: "N of M readable".** `FlowProtocol.progress` gains a finished-chapter count;
   `FlowStatus.Converting` gains `readableChapters` (0 while nothing is readable). The service reports it; the app turns
   it into "chapter 12 of 29 readable" on the progress ring and the "Aa"-sheet line.
5. **The reader opens the partial.** `DynamicFlow.bundle()` returns the partial EPUB once ≥1 chapter is readable (not
   only when `ready()`). `ReaderFlow.watch()` reopens the flow view when the first chapter lands (if the reader wants
   flow), and refreshes the open EPUB as further chapters land, reusing the existing reopen + reanchor machinery so the
   reading place survives.

Open sub-decisions for the plan (builder's call, not owner-facing): whether "refresh" is a full reopen or an in-place
spine reload; exactly how the reading place is preserved across a chapter arrival; the re-zip cadence (every chapter vs.
batched), which is also where pacing on slower phones lives.

### M2: the health badge

1. **Read the sidecar.** Parse `flowfolio.json`'s `health` (`:core:conversion:epub.Sidecar` already deserializes it).
   Add a `BookHealth` type in `:core:model` and a cached reader in `:core:data`, invalidated alongside
   `FlowFiles.outdated`.
2. **One glance.** A single grade plus one line: text coverage, pages below threshold, fallback pages, geometric
   coverage. Grade rule (my call, adjustable): **Good** = no degraded pages and coverage ≥ threshold; **Fair** = a few
   fallbacks/degraded pages; **Poor** = many. Shown as a compact badge ("Converted well · 98%").
3. **Surfaces.** The reader's "Aa" sheet (on the Dynamic Flow card) and the library's book details. Degraded pages get a
   mark in the reader (spec §4: "marks degraded pages").

## Release

Phase 5 ships **canary**. Proposed first release **0.5.0-canary.1** (build 500001), to be confirmed with the owner
before `gradle.properties` is touched — Phase 4 shipped as `0.3.0-alpha.6`, so "phase = minor" has already drifted once.

## Deferred (recorded, not lost)

- **Mechanism B as a failover.** When the real pixel diff is built (headless HTML render, its own ADR + dependency
  approval), its job is self-healing: a page whose render disagrees with the original falls back to an image, rather
  than gating CI. This is Phase 4 M2's deferred "self-healing" in its final shape.
- **F-Droid self-hosted repo.** End of Phase 9.

## What's left to the builder

- Exact signatures, file paths, test names — the plan's job (`writing-plans`).
- Refresh-vs-reopen mechanics and position preservation for a growing EPUB.
- Re-zip cadence / throttling, and the pacing measure-and-tune that Phase 5's M3 used to own.
