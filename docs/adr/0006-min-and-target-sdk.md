# ADR-0006: minSdk 29, target and compile the latest stable API

- **Status:** Accepted (minSdk to be re-checked by the Phase 1 PDF rendering spike)
- **Date:** 2026-10-16
- **Phase:** 0

## Context

Android background: `minSdk` is the oldest Android version the app installs on;
`targetSdk` declares which platform behaviors the app has opted into; `compileSdk` is the API
level the code compiles against. Google Play requires a recent `targetSdk`; `minSdk` is our choice.

Forces:
- Students often use older or budget phones, which argues for a low `minSdk`.
- Android 10 (API 29) introduced scoped storage. Below 29, file import needs a second,
  legacy code path with broad storage permissions.
- Jetpack `androidx.pdf` may require API 31+ with SDK extensions; this is unverified until the Phase 1 spike.
- Compose, Hilt, Room, WorkManager, and Readium all support API 29 and below.

## Options considered

1. **minSdk 26 (Android 8)**: widest reach; legacy storage path; more device bugs to test.
2. **minSdk 29 (Android 10)**: one storage model (Storage Access Framework); still covers the
   large majority of active devices.
3. **minSdk 31 (Android 12)**: simplest; excludes a meaningful share of student devices.

## Decision

`minSdk = 29`. `compileSdk` and `targetSdk` = latest stable platform (API 37 at time of writing).

## Consequences

- Single, permission-light import path via the Storage Access Framework.
- If the Phase 1 spike selects a renderer that needs API 31+, a new ADR will either raise `minSdk`
  or keep a fallback renderer (`android.graphics.pdf.PdfRenderer`, available since API 21) for 29–30.
