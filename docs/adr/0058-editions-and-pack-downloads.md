# ADR-0058: Two editions, and pack downloads that resume

- **Status:** Accepted (owner, 2026-11-12). Amends ADR-0057 (the release assets) and ADR-0042 (the release
  checklist: releases are now built by `.github/workflows/release.yml`).
- **Date:** 2026-11-12
- **Deciders:** product owner, lead engineer

## Context

0.7.0-beta.4 made the optional packs (scanned books, Narrator voices, WordNet) downloads offered after the tour. On the
owner's phone the scanned-books pack failed outright, and a second try took a long time. Measured: GitHub serves both
FlowFolio's and sherpa-onnx's releases at 8-15 MB/s with HTTP Range, so the cause was the app:
- each pack fetched its files one at a time with **no retry**: one dropped read failed the whole pack;
- the voices were sherpa-onnx's 132 MB `.tar.bz2`, which the phone then unpacked in Java for minutes (4.8 s of full
  CPU natively on a Mac) while the card read "100%";
- the reader and the offer each made a voice downloader for the same folder;
- the three packs waited for each other.

The owner also asked for an edition with everything inside, and releases for current processors and one tier back.

## Decision

1. **`PackFetch` (`:core:voice`)** is the one downloader: a pack's files in parallel, each resuming from the bytes on
   disk (Range) for up to 5 attempts, progress at most once per percent, failures logged under `FlowFolioFetch`, and
   served bytes checked against pinned SHA-256s before anything is renamed into place. The packs download side by side.
2. **The five Kokoro files** the narrator uses are mirrored on `resources-v1`, unchanged and gzipped one by one
   (117 MB): unpacked by native zlib in seconds. Piper's opt-in voices keep sherpa-onnx's archives.
3. **Two editions** (`edition` flavour, same package and key, so each updates the other in place):
   - **Essential** downloads the packs on request.
   - **Full** carries them in `assets/packs/<group>/` (fetched at build time from `app/bundled-packs.tsv`, pinned) and
     copies them into place on first start (`BundledPacks`); the offer is skipped. Release builds only.
4. **Assets per release:** for each edition, `arm64-v8a` (current phones), `armeabi-v7a` (one tier back) and the
   universal APK (which also carries `x86_64` for emulators and Chromebooks). Full names end in `.full.apk`, which
   updaters from before the editions never match, so they stay on Essential; `Updates.pickApk(full = …)` keeps each
   phone on its edition.
5. **`release.yml`** builds and publishes a `v*` tag's release, signing with the release key from four repository
   secrets (ADR-0049's second copy), and refuses any APK not signed by `59fbae55…` (`tools/release/collect-apks.sh`).

## Consequences

- Measured on `fold8_api37`: all three packs (177 MB) in 40 s, where beta.4 failed or took minutes. Full's packs are in
  place 8 s after first start.
- Full is about 190 MB larger (arm64 326 MB, universal 479 MB).
- `assembleDebug`, tests, lint and benchmarks are the Essential edition's (`essentialDebug`); the debug APK is at
  `app/build/outputs/apk/essential/debug/`.
