# ADR-0057: A smaller app: one APK per processor type, and optional downloads

- **Status:** Accepted (owner, 2026-11-12, tightening plan TM2).
- **Date:** 2026-11-12
- **Deciders:** product owner, lead engineer
- **Phase:** tightening milestone TM2

## Context

The 0.7.0-beta.3 APK is 359 MB. 297 MB of that is native code for four processor types, of which a phone uses one:

| Native code | Size |
|---|---|
| x86_64 | 96 MB |
| arm64-v8a | 91 MB |
| armeabi-v7a | 57 MB |
| 32-bit x86, which some prebuilt libraries bring | 53 MB |

The owner asked for a smaller first install, with the rest downloaded on request.

## Decision

1. **One APK per processor type** in each release, beside the universal APK:
   - The types are arm64-v8a, armeabi-v7a and x86_64. 32-bit x86 is dropped everywhere (`ndk.abiFilters`).
   - Measured: **arm64 143 MB**, 32-bit ARM 107 MB, x86_64 148 MB, universal 303 MB.
   - Only release builds split (`splits.abi`), so the debug APK handed over for testing stays one file.
   - The in-app updater installs the APK for the phone's processor (`Updates.pickApk`, shipped one release earlier
     in 0.7.0-beta.3). The universal APK keeps the old file name and is listed first, so older updaters still find it.
2. **Optional downloads instead of bundled data:**
   - **WordNet** (8 MB) leaves the APK. It is downloaded from the `resources-v1` release and checked against its
     pinned SHA-256 (`WordNetPack`).
   - The first-launch offer (`ResourcesOfferHost`) lists Scanned books, Narrator voices and the Dictionary, each with
     a screenshot, its size and a switch. It comes once after the tour and again from Settings → Storage.
3. **No native code is downloaded after install.** Everything that runs came in the signed APK. Python, ONNX Runtime,
   ML Kit's translator and Tesseract stay in the APK, because downloading code would bypass the signature that
   protects readers.

## Consequences

- A phone downloads about 60% less.
- A release now uploads four APKs and their `.sha256` files (ADR-0042's checklist).
- A reader who never downloads the Dictionary still has Wiktionary and Wikipedia in Define.
