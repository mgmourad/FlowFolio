# FlowFolio — Phase 6 handoff (starting clean)

You are continuing **FlowFolio**, an Android app (Kotlin, Jetpack Compose) that reflows PDF textbooks into EPUB
("Dynamic Flow") and reads them with a page-curl / two-page-spread reader, on-device.

**Read first, in order:**
1. `CLAUDE.md` — project memory (architecture, conventions, commands, known issues, release checklist).
2. `PLAN.local.md` and `ROADMAP.local.md` — private planning docs (git-ignored). They define the phases.
3. `docs/status.md` — a one-page status report of where the project is.
4. `docs/plans/phase5-signoff.md` — what just closed, and what was consciously deferred.
5. `docs/adr/` — decisions; especially 0042 (versioning/channels), 0045 (Dynamic Flow), 0050 (channel renumbering).

## Current state (2026-11-07)

- **Phase 5 ("the product flow around conversion") is signed off** and released as **FlowFolio 0.5.0-canary.1
  (build 500001)**, tag `v0.5.0-canary.1`. CI is **green** (including the converter corpus gate); `main` is pushed
  and clean.
- **Phases 0–5 of 7 are done.** What remains is **Phase 6 (reading features)** and **Phase 7 (scanned books and OCR)**.
- Release channels (ADR-0050, accepted): **3–4 alpha, 5–6 canary, 7 beta.** So Phase 6 ships canary
  (`0.6.0-canary.1`), Phase 7 ships beta (`0.7.0-beta.1`).

## What Phase 5 just shipped (so you're not re-deriving it)

- **Progressive delivery:** chapters become readable in Dynamic Flow as they finish (the converter rewrites a valid
  partial EPUB after each chapter; the sidecar carries `complete: false` + `finishedSpineItems`; the reader opens the
  first chapter and reopens on completion). A complete conversion is byte-for-byte unchanged — no engine bump, no
  re-conversion, CI baselines stay valid.
- **The health badge:** the reader's "Aa" sheet grades a conversion good/fair/poor from the metrics already in
  `flowfolio.json`.
- **Settings as a menu of pages** (the owner's redefinition of M3 from "conversion on slower phones").
- **The polish slot:** first-book demo with "Never show tips again", the reader's chapter-and-section running heads,
  the shelf-page header, annotation counts + VoiceMark tabs, and the Favourites page header.
- ADR-0050 (release-channel renumbering) is **accepted**. No ADR describing shipped code is still `Proposed`.

## Architecture map (the parts Phase 6 and 7 touch)

- **Reader** `android/feature/reader/`: `ReaderViewModel.kt`, `EpubSession.kt`, `ReadyReader.kt`, `ReaderScreen.kt`,
  `TurnStyles.kt`, `PageTone.kt`. In-book search and TTS belong here; the reflowed text is already laid out with
  chapter + offset, and the locator machinery (Phase 2, ADR-0040) already turns a place into a highlight/jump.
- **EPUB package** `android/core/epub/` (`EpubPackage.kt`, `PackageReader.kt`): it reads `rendition:layout` and sets
  `fixedLayout`, but the reader ignores it. Fixed-layout EPUBs (M2) start here; ADR-0019 says each page wants a WebView
  at page size.
- **Converter** `android/core/conversion/`: `Converter.kt` already emits `PageKind.SCANNED` and passes scanned pages
  through as images. Phase 7's OCR slots in here.
- **Library search** (already built) and the reader's lookup search are *not* in-book search — M1 is a new thing.
- **Dictionary** `android/feature/library/` (ADR-0046) is done; it is not a template for TTS, but the two are neighbours.

## Your task: Phase 6, then Phase 7

### Phase 6 — reading features (`PLAN.local.md` has the full text)

**Goal:** the three things a serious reader expects that the app still cannot do. **Done when:** you can search a
book, a fixed-layout EPUB looks like itself, and the app can read aloud.

- **M1 — Search inside a book.** Find a phrase in the book you're reading, in both views, with results you can jump to.
  Reflowed text is already indexed by chapter and offset.
- **M2 — Fixed-layout EPUBs.** `rendition:layout: pre-paginated` is detected but ignored; such books are reflowed
  today. ADR-0019: each page needs a WebView drawing at page size.
- **M3 — Text to speech.** Nothing exists. Android's own `TextToSpeech` keeps it offline and dependency-free.
  **Note the collision:** equations and tables are images, so TTS will skip them silently — decide then whether that is
  acceptable or reopens the maths question.
- **M4 — Accessibility pass.** Screen-reader labels, focus order, contrast, touch targets.
- **M5 — Polish slot** (the owner's tweak batch).

### Phase 7 — scanned books (`PLAN.local.md`)

**Goal:** books that are pictures of pages become readable. **Done when:** a scanned book reflows, and a bad OCR page
falls back rather than inventing text.

- **M1 — OCR decision and ADR.** ONNX Runtime (32.6 MB/ABI) or ML Kit (proprietary, already blocks F-Droid). ADR-0003's
  no-GPL rule covers model weights too. Settle the licence before the engineering.
- **M2 — OCR in the pipeline.** `PageKind.SCANNED` is already detected; give it a text layer.
- **M3 — Quality gate.** A scanned-book corpus and baselines, with a confidence threshold below which a page stays an
  image.
- **M4 — Polish slot.**

## Open items to keep on the radar (not phase work)

- **The real pixel diff (mechanism B)** — deferred from Phase 4. It is the honest version of "a broken conversion is
  caught before a reader sees it" and wants a headless HTML renderer (a new dependency → its own ADR + owner approval).
  Raise it with the owner if it should land before Phase 7.
- **ADR-0049's follow-up** — a second off-machine copy of the release signing key as GitHub Actions secrets.
- **F-Droid self-hosted repo** — deferred; a self-hosted repo avoids the ML Kit/PDFium/Chaquopy blocks.
- **Launch posts** — written, not sent.

## Working agreements & gotchas (they will bite you otherwise)

- **Never claim something works without running it.** Say "tested", "implemented but untested", or "stubbed"
  (`CLAUDE.md`).
- **Ask before adding any dependency** (state license, last release date, alternatives). This applies to the OCR model
  and the headless HTML renderer in particular.
- **macOS arm64 cannot run `:tools:convert:test`** (Linux-only `libpdfium.so`). Use `-x :tools:convert:test` locally;
  CI's Linux runner owns the corpus gate.
- **CI corpus gate is required (`ci-gate`).** A converter change that alters output **must** update the baselines on a
  Linux box (`-Pflowfolio.recordBaselines`) or CI goes red.
- **detekt/ktlint strictness:** complex functions need `@Suppress("CyclomaticComplexMethod", "NestedBlockDepth")`; a
  top-level KDoc must not dangle. Run `spotlessApply` + `detekt` before committing.
- **Delivery rule (owner):** CI gates at *phase* boundaries, not per-milestone-tweak. Milestone work is verified
  locally (`spotlessCheck detekt lintDebug testDebugUnitTest`, `:app:assembleDebug` on `fold8_api37`) and delivered in
  large batches.
- **Reference device:** Galaxy Z Fold 8 Ultra (adb `RFGL7318RKK`); emulator `fold8_api37`. Folded cover screen
  1080×2520; unfolded inner screen is the spread.
- **Release checklist (ADR-0042):** set `flowfolio.versionName`/`versionCode` in `android/gradle.properties`, add the
  CHANGELOG heading, commit, push, wait for green CI, build `:app:assembleRelease`, tag, `gh release create
  --prerelease`. Release key is in `~/.flowfolio` (signing via `~/.gradle/gradle.properties`).
- **Conventional Commits**; keep `CHANGELOG.md`; no secrets in the repo.

## Suggested first steps for the next session

1. Read `CLAUDE.md`, `PLAN.local.md` (Phase 6 section), and `docs/plans/phase5-signoff.md` to confirm the above
   against the code as it stands.
2. Start Phase 6 with a **plan document** (`docs/plans/phase6-m1.md` at least) and get the owner's sign-off on M1
   (in-book search) before building — that's the milestone the other Phase 6 work hangs off.
3. Decide the M3 collision early: does text-to-speech silently skipping images (tables/equations) reopen the maths
   question, or is it acceptable? That is a product call for the owner, not a code call.
4. Build M1, verifying on the Fold as you go, and close Phase 6 with its own `phase6-signoff.md`.
