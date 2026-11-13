# FlowFolio: project sign-off

- **Status:** **Signed off by the product owner, 2026-11-12.** The planned project is complete.
- **Release at sign-off:** FlowFolio **0.7.0-beta.5 (build 700005)**, tag `v0.7.0-beta.5`, a GitHub pre-release
  built by `.github/workflows/release.yml`: Essential and Full editions, each for 64-bit ARM, 32-bit ARM and as a
  universal APK, all signed with the release key (`CN=FlowFolio Release`, SHA-256 `59fbae55…`).
- **CI at sign-off:** green on `main`.
- **Owner's words:** "this is the first time I feel 100% satisfied with the app." Tweaks and fixes will follow; they
  are maintenance, not open phases.

## What was built

FlowFolio is a reader for PDFs and EPUBs, made foldable-first (Galaxy Z Fold), that turns any PDF into a book that
reflows to the screen, on the phone, offline.

| Phase | What it delivered | Signed off |
|---|---|---|
| 0 | Monorepo, Android build, CI, licensing policy | 2026-10-16 |
| 1 | The PDF reader: PDFium via JNI, page curl, spreads, annotations, translation | 2026-10-20 |
| 2 | EPUB reading and the pagination engine, the riffle scrubber, typography | 2026-10-27 |
| 3 | The converter on the phone (Dynamic Flow), Reading Atmosphere, VoiceMark, Dictionary, updates | 2026-11-06 |
| 4 | Conversion quality: covers, crops, tables, code highlighting | 2026-11-06 |
| 5 | Progressive delivery, the health badge, Settings as pages | 2026-11-07 |
| 6 | In-book search, three-source Define, fixed-layout EPUB, Narrator Mode | 2026-11-08 |
| 6.5 | The Quiet Library UX overhaul (ADR-0052) and Narrator in the background (ADR-0053) | 2026-11-10 |
| 7 | Scanned books in English and Arabic (OCR, ADR-0054/0055) | 2026-11-12 |
| TM1, TM2 | Tightening: dark reader chrome, priority conversion, folder sync, smaller app, demo book, widgets, two editions | 2026-11-12 |

The detail is in each phase's sign-off under `docs/plans/`, the `CHANGELOG.md`, and ADRs 0001–0058.

## How it is released from here

Push a `v*` tag. The release workflow builds both editions, refuses any APK not signed with the release key, and
publishes the pre-release with the CHANGELOG's reader notes (ADR-0042, ADR-0050, ADR-0058). The key's second copy is
in the repository's Actions secrets (ADR-0049, set 2026-11-12).

## Known limitations, carried forward

None of these block the sign-off; each is a candidate for a future tweak.

1. **OCR speed on the Fold:** 4.4 s a page in English, 7.0 s in Arabic, against a 2 s target (known issue 55).
2. **Arabic OCR accuracy:** about 16% CER on kept corpus pages against the plan's 6%; pre-1950 print and manuscripts
   stay pictures by design (ADR-0054/0055).
3. **Two-column scanned pages** read straight across; figures on scanned pages are dropped when the page becomes text.
4. **Tour pictures:** the unfolded set predates the last visual changes, and the folded VoiceMark slide reuses the
   unfolded picture (recording cannot be shown on the emulator, known issue 40).
5. **Not checked on a real phone:** the Full edition's first start, and the widgets on the Fold's cover launcher.
6. **Release file names:** Essential APKs carry no edition word, to keep beta.4's updater working; a
   `flowfolio-essential-…` prefix would be safe from the next release.
7. **Owner items from Phase 7:** the launch posts, F-Droid yes or no, and proofread Arabic ground truth.
8. **Hygiene:** detekt 1.23.8 is old (known issue 3); Gradle 10 deprecation warnings come from plugins (known
   issue 5); the release key's password has appeared in a chat transcript and can be changed with
   `keytool -storepasswd` (then the gradle property and the Actions secret).
