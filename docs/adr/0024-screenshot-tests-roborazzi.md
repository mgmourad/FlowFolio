# ADR-0024: Use Roborazzi for screenshot tests, verified on the development machine

- **Status:** Accepted (product owner, 2026-10-20: "add Roborazzi 1.76.0 as a test-only dependency with ADR-0024")
- **Date:** 2026-10-20
- **Deciders:** product owner, lead engineer
- **Phase:** 2 (milestone M2b)

## Context

The riffle scrubber (ADR-0022) is drawn by hand in a canvas, and its spec asks for screenshot tests of the strip at
rest, mid-riffle and with a flag stack, in dark and light. The project already runs Compose UI tests on the JVM with
Robolectric 4.17 (pinned to API 36, CLAUDE.md known issue 1).

## Options considered

1. **Roborazzi** (takahirom/roborazzi): Apache-2.0; 1.76.0 released 2026-09-29, with releases every few weeks. It
   captures Compose content under Robolectric's native graphics, which we already use.
2. **Paparazzi** (cashapp/paparazzi): Apache-2.0; its recent releases are 2.0 alphas (2.0.0-alpha05.1, 2026-09-28).
   It is built on Android Studio's layoutlib, which has lagged new AGP and Compose releases before.
3. **No screenshot tests**: rely on device screenshots by hand. Nothing would catch a drawing regression.

## Decision

Option 1, as a **test-only** dependency (`roborazzi`, `roborazzi-compose`, `roborazzi-junit-rule` and the Gradle
plugin); nothing ships in the app.

- Reference images live with the tests (`src/test/screenshots/`) and are committed.
- Tests drive the Compose frame clock by hand, so every image is the same on every run.
- **Images differ between operating systems** (fonts and anti-aliasing), so the references are recorded and verified on
  the development machine: `.\gradlew.bat :feature:reader:riffle:recordRoborazziDebug` after an intended change, and
  `verifyRoborazziDebug` before committing drawing changes. CI runs the same tests without comparing images: it
  catches crashes, not pixel changes. If that proves too weak, CI can record its own references on Linux and verify
  against them (Roborazzi's documented CI setup); that would be a later decision.

## Consequences

- Positive: drawing changes show up as image diffs; the strip's look in both themes is documented in the repo.
- Negative / accepted risks:
  - Verification is a local step, not enforced by CI.
  - Images add about 0.3 MB each to the repository.
- Follow-ups:
  - **M2b R3 to R5 (lead engineer):** screenshots of the loupe and the stack popover.
