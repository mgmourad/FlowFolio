# Phase 3 sign-off: converter v1, on the phone

- **Status:** Signed off by the product owner, 2026-11-06. Every exit criterion is met, including the Fold timing,
  measured on the real device (below).
- **Plan:** [`phase3-plan.md`](phase3-plan.md). **M1+M2 sign-off:** [`phase3-m1m2-signoff.md`](phase3-m1m2-signoff.md)
  (signed off 2026-10-28, released as FlowFolio 0.3.0-alpha.1). **M3:** the owner's Dynamic Flow design (ADR-0045),
  built 2026-10-28; its detail is in the M1+M2 sign-off's "M3" section.
- **Spec:** `docs/conversion-spec.md`, `docs/schemas/flowfolio.schema.json`.
- **Decisions to ratify with this sign-off:** ADR-0044 (a plain JVM engine), ADR-0048 (updating from the releases
  page), ADR-0049 (re-keying the release identity). All three are `Proposed`; the recommendation is to accept all
  three (see below).
- **Measured on:** the reference Fold (SM-F976U, folded cover screen, Android 17) for the timing; the `fold8_api37`
  emulator (inner 2256×2504, API 37) for the tests and corpus work.

## What Phase 3 delivered

The converter runs on the phone, in its own process, and a converted book opens in the Phase 2 reader. The phase grew
beyond its plan as the owner used the result, so it is summarised here by milestone; the M1+M2 sign-off is the detailed
record of the engine's build.

- **M1 — engine and extraction.** `:core:conversion` as a plain JVM module (ADR-0044); PDFium on the JVM through the
  `PdfSource` interface; the `Document` IR; the corpus harness with SHA-256-pinned downloads.
- **M2 — from structure to EPUB.** Design-system inference, reading order, blocks, headings and linking, and EPUB
  assembly to the spec, with the coverage check and the metrics baselines. Nine corpus books convert with no
  EPUBCheck errors.
- **M3 — Dynamic Flow (ADR-0045).** The "Convert" entry became one book read two ways: PDF or reflowed, switched in the
  "Aa" sheet, a foreground service in its own process (`:convert`), and per-view highlights, notes and bookmarks.
- **M3T1–T3, M4, M4b, M5 — the owner's rounds.** Polish and navigation; finished, favourites and library search; the
  Reading Goal and the offline dictionary (ADR-0046); the theme, confetti, shelf orders and reminder tweaks; Reading
  Atmosphere and VoiceMark; the detour mark; intentional tags; the first-launch tour; folders-to-shelves on import;
  in-app updates (ADR-0048); storage housekeeping; audio extraction from video pages (ADR-0047). This is the bulk of
  what the app is to a reader, and it sat outside the phase plan; `ROADMAP.local.md` records how that happened.

**Releases.** Four pre-releases shipped from Phase 3: 0.3.0-alpha.1 (300001), alpha.2 (300002), alpha.3 (300003) and
alpha.4 (300004). alpha.1 and alpha.2 were deleted on 2026-11-03 (ADR-0049); alpha.3 was the first build anyone could
install, signed with the new release key.

## Exit criteria

| Criterion (phase plan) | Result | Evidence |
|---|---|---|
| Every born-digital corpus book converts to an EPUB with no EPUBCheck errors, and a `flowfolio.json` that validates | **Met.** 9 of 9 OpenStax books, 0 errors and 0 warnings (EPUBCheck 5.4.0); the robustness PDFs and the phone's WebP EPUBs too. After the technical books were added, all 14 convert cleanly. | `:tools:convert:test` (CI `corpus` job) |
| Text coverage of at least 0.98 on at least 95% of pages; under-threshold pages go out as fallbacks and are listed | **Met.** 0.9999 of 7,024 text pages covered; one page (University Physics) went out as a page image. After the quality round, every book covers 100% of its text. | `samples/golden/baselines/`; `CorpusTest` |
| Chapters match the PDF outline; heading levels and reading order correct on the hand-labelled pages; printed page numbers work | **Met.** Every outline matched (0.969–1.000 across the corpus); hand-labelled heading scores 1.00 in every book, reading order 0.88–1.00; "Go to page" uses the printed numbers. | `samples/golden/labels/` (30 pages); the reader on the emulator |
| *University Physics* converts on the Fold within 5 minutes, in the background, in its own process, and opens in the reader | **Met.** On the real Fold (SM-F976U, folded), 959 pages converted in **199.8 s** (3 min 20 s), in the `:convert` process, while the app stayed in the library, and the result opens in the reflowed reader. See the measurement below. | `FF_FLOW … ok in 199790 ms` (logcat); `FlowServiceOnDeviceTest` |
| CI runs the corpus metrics on the JVM and fails on regression | **Met.** The `corpus` job fetches the three CC BY books, runs `CorpusTest` against the baselines, and is part of `ci-gate`. | `.github/workflows/ci.yml` |
| Unit, JVM and instrumented tests; CI green | **Met.** Stage tests, the corpus gate, `TechnicalBookTest`, `OwnBooksTest`, and the on-device service tests pass; CI was green on the alpha.3 and alpha.4 pushes. | `ci-gate` |

### The Fold timing (measured 2026-11-06)

The one exit criterion that emulator numbers could not close, now measured on the real device.

- **Result: 199.8 s (3 min 20 s)** for *University Physics* (959 pages), well inside the 5-minute (300 s) target.
- **How:** the reference Fold (SM-F976U, folded cover screen, Android 17), the library's "Turn on Dynamic Flow", with
  the conversion in the `:convert` process while the app stayed in the library. The service logged
  `FF_FLOW <id> ok in 199790 ms`.
- **Own process:** `com.flowfolio.dev:convert`, about 204 MB RSS during the run and idle at about 148 MB after; the
  app's own process was unaffected.
- **Output:** a 37 MB `book.epub` plus its `flowfolio.json`, opened in the reflowed reader (its page indicator reads
  "1", not the PDF's "1 of 959").

For reference, the emulator measured the same book at 58 s (M1+M2); other corpus books on the emulator: *Principles of
Economics 3e* (995 pages) 108 s, *Physics* (875 pages) 110 s, *U.S. History* (987 pages) 185 s, *Psychology 2e* (755
pages) about a minute. The real Fold is slower than the emulator, as expected, and still clears the target by a wide
margin.

## Ratify the three open ADRs

Three ADRs describing shipped code are still `Proposed`. The recommendation is to **accept all three**.

| ADR | What it decided | Why accept | Anything to note |
|---|---|---|---|
| [0044](../adr/0044-conversion-engine-as-a-jvm-module.md) | The engine is a plain JVM module; JNA only in desktop tooling | It is the structure the whole phase was built on, and it held: one engine, tested on the JVM against the real PDFium and run unchanged on the phone. Supersedes the KMP part of ADR-0009. | None. It has been in force since M1+M2; accepting it just records what is already true. |
| [0048](../adr/0048-updating-in-place.md) | Check, fetch and hand updates to Android's installer, never install silently | Shipped and working: once the repository went public, Settings → Updates found and downloaded a build and offered Install. | `REQUEST_INSTALL_PACKAGES` is sensitive and F-Droid flags it; the ADR already says to explain it in the listing. |
| [0049](../adr/0049-re-key-the-release-identity.md) | Generate a new release key now, before the launch, with written custody | The old key was lost, both earlier releases had zero downloads, and the new key has signed every build from alpha.3. Supersedes the key-custody part of ADR-0042. | One follow-up is still open: the second off-machine copy as GitHub Actions secrets (the password-manager copy exists). |

If any is rejected rather than accepted, it should be superseded rather than edited (the accepted-ADRs convention), and
the "Done when" line below changes.

## For the owner

1. **Ratify ADR-0044, ADR-0048 and ADR-0049** (recommended: accept all three).
2. **The licences screen** (known issue 8) is now in Settings → About → Open-source licences, and is on the Fold in the
   build you have. Give it a look.
3. **Sign off Phase 3**, or list what to change.

**Done when:** this sign-off is accepted and no ADR describing shipped code is still `Proposed`. (The Fold number is
recorded and inside target — 199.8 s of the 300 s budget.)

## Known limits carried forward

- **Tables and display equations are images** this phase (by plan). Phase 4's "Fallbacks worth keeping" makes the image
  path deliberate; the reader-side islands (ADR-0019/0021) already render real MathML, SVG and complex tables for EPUBs
  that contain them.
- **The front matter** of a converted book reflows as one justified paragraph.
- **The converter's JVM tests do not run on macOS arm64** (`libpdfium.so` is a Linux ELF): `:tools:convert:test` needs
  `-x` there; CI's Linux runner owns the corpus gate (CLAUDE.md, known issue 37).
- **Carry-overs from Phase 2:** island zoom on the cover screen, and equation spreads taking a little over 300 ms after
  a size change, were never checked on a device.

## What comes next

Phase 4 — "trust the conversion" (`PLAN.local.md`): pixel verification against the original PDF (the metric that would
have caught the Packt bug automatically), self-healing fallbacks, and the fallbacks-worth-keeping work that replaces
the dropped Phase 5.
