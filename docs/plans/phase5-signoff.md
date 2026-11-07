# Phase 5 sign-off: the product flow around conversion

- **Status:** Signed off by the product owner, 2026-11-07.
- **Plan:** `PLAN.local.md`, Phase 5 — "the product flow around conversion". Milestone plan:
  [`phase5-m1.md`](phase5-m1.md) (progressive delivery). M2 (the health badge) and M3 (Settings as a menu of pages)
  ran straight from the design ([`phase5-design.md`](phase5-design.md)); M4 is the standing end-of-phase polish slot.
- **Spec:** `docs/conversion-spec.md` §3–4 (sidecar health) and §6 (progressive output), `docs/schemas/flowfolio.schema.json`.
- **Decisions ratified with this sign-off:** ADR-0050 (release-channel renumbering: 3–4 alpha, 5–6 canary, 7 beta).
- **Measured on:** the reference Fold (folded and unfolded) for the reader and library work; the JVM converter tests
  for the conversion engine. The Linux-only corpus gate (`:tools:convert:test`) runs in CI (`ci-gate`), not on macOS
  arm64 (known issue 37).

## What Phase 5 delivered

The phase's goal was that conversion stop feeling like a wait and start feeling like a feature: a book is readable
*before* it finishes converting, and a reader can see *at a glance* how good the conversion is.

- **M1 — progressive delivery.** A converting book's first chapter is readable in Dynamic Flow long before the whole
  book is done. The converter renders each chapter's images and rewrites the EPUB after every chapter — a valid EPUB
  of only the finished chapters — and the sidecar carries `complete: false` and a `finishedSpineItems` list while
  partial. The service writes each partial atomically to `book.epub.part` and reports "chapter N of M readable"; the
  reader opens the first chapter as soon as it lands, picks up further chapters as they land (throttled so reading
  isn't interrupted every chapter), and reopens to the whole book on completion. A complete conversion is
  byte-for-byte unchanged, so the engine version is not bumped, no book re-converts, and the CI baselines stay valid.
- **M2 — the health badge.** The reader's "Aa" sheet grades how well a book converted, from the metrics already in
  `flowfolio.json`: "Converted well · 98% text", or "A few/Many pages are pictures" when pages fell back, graded
  good/fair/poor from the fallback count.
- **M3 — Settings as a menu of pages.** The owner redefined M3 from "conversion on slower phones" (its pacing folded
  into M1) to this: Settings is now a menu — Appearance, Text, Reading, Atmosphere, Library, Dynamic Flow, Updates and
  Storage each open a page of their own behind a shared piece of art (the page's icon on a glowing plate), with a
  slide-and-fade transition; About and "Show me around" stay on the menu.
- **M4 (the polish slot) — the owner's batches.** The first-book demo (a tap-zone overlay with "Got it" and
  "Never show tips again", and "Introduce features again" in Settings); the reader's chapter-and-section running heads
  (kept to their own page's column in a spread, and cut at half the cover screen when folded so the front camera never
  clips them); the shelf-page header (the name on its own line); the update banner deep-linking to Settings' update
  section and the "what's new" window after an update; annotation counts and VoiceMark tabs on the Annotations page,
  with the author under each book's title; and the Favourites page header (left title, slogan, Search and Select, and a
  select-mode Delete that asks whether to remove the books from Favourites or from the library).

**Release.** One pre-release ships from Phase 5: **0.5.0-canary.1 (500001)**, tag `v0.5.0-canary.1`.

## Exit criteria

| Criterion (phase plan) | Result | Evidence |
|---|---|---|
| A long book is readable before it finishes converting | **Met.** The reader opens the first chapter as soon as it lands and picks up further chapters as they finish, reporting "chapter N of M readable". | `Converter.convertProgressively`; `FlowService`; `ReaderFlow.watch` |
| A reader can see at a glance how good the conversion is | **Met.** The "Aa" sheet's Dynamic Flow card grades the conversion good/fair/poor with one line. | the health badge in `feature/reader` |
| A complete conversion is unchanged (no re-conversion, CI baselines stay valid) | **Met.** `finishedSpineItems` is omitted when `complete: true`, so a complete sidecar and EPUB are byte-identical; the engine version is not bumped. | `SidecarBuilder`; the corpus baselines |
| Settings is a menu of pages | **Met.** Eight pages behind a menu, with About and "Show me around" on the menu. | `SettingsScreen.kt` |

## Ratify ADR-0050

One ADR describing shipped scope was `Proposed`:

| ADR | What it decided | Why accept |
|---|---|---|
| [0050](../adr/0050-release-channel-renumbering.md) | Renumber the release channels: 3–4 alpha, 5–6 canary, 7 beta (superseding ADR-0042's channel section) | Phase 5 is the first release under the new mapping — canary, not alpha. Accepting it before `0.5.0-canary.1` keeps the channels honest. |

**Done when:** this sign-off is accepted and no ADR describing shipped code is still `Proposed`.

## Known limits carried forward

- **The real pixel diff (mechanism B) is still deferred.** `geometricCoverage` remains diagnostic-only; the Packt class
  of bug is still caught by a reader, not the machine. It wants a headless HTML renderer — a new dependency — so it
  keeps its own ADR and owner approval.
- **F-Droid self-hosted repo** remains deferred to the end of Phase 9.
- **macOS arm64 cannot run `:tools:convert:test`** (Linux-only `libpdfium`), so the corpus gate runs in CI only —
  known issue 37.
- **ADR-0049's follow-up** (a second off-machine copy of the release signing key as GitHub Actions secrets) is still
  open.
- **Carry-overs from earlier phases** (island zoom on the cover screen, equation spreads taking a little over 300 ms
  after a size change) were not re-checked on a device this phase.

## What comes next

Phase 6 — reading features (`PLAN.local.md`).
