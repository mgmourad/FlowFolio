# ADR-0036: Sign the sideloaded dev builds with one fixed key, kept out of the repo

- **Status:** Accepted (owner, 2026-10-26: "create a fixed signing key on this PC (kept out of git) and sign every APK
  with it from now on, so after this one reinstall my phone updates in place")
- **Date:** 2026-10-26
- **Deciders:** product owner, lead engineer
- **Phase:** 2

## Context

The owner tests by sideloading APKs of `com.flowfolio.dev` onto their Fold and wants each one to update the
installed app, keeping the library. Android only accepts an update signed with the same key as the installed app.
Debug builds were signed with the SDK's per-machine debug key (`~/.android/debug.keystore`), which differs between
machines and between CI runs: the copy on the phone on 2026-10-26 (certificate SHA-256 `9440c095...`) matched no
key on the dev PC (`0e43833b...`), so a new build could only be installed after uninstalling, losing the app's data.

## Options considered

1. **Keep the SDK debug key.** Nothing to set up, but the key depends on the machine; any build from elsewhere
   forces an uninstall.
2. **Commit a shared debug keystore.** Every machine and CI signs alike, but a key in the repo goes against "no
   secrets in the repo" and would be easy to reuse by mistake for a release.
3. **One fixed key outside the repo, picked up by Gradle when present.** Stable for every APK built on the dev PC;
   nothing secret in git; other machines and CI fall back to the SDK debug key.

## Decision

Option 3. `~/.flowfolio/flowfolio-dev.jks` (PKCS12, RSA 4096, valid 100 years, alias `flowfolio-dev`, certificate
`CN=FlowFolio Dev, O=FlowFolio`, SHA-256 `b972334c...`) holds the key; its path and passwords are the
`flowfolio.devSigning.*` properties in the user's `~/.gradle/gradle.properties`. `app/build.gradle.kts` signs the
`debug` build type with it when `flowfolio.devSigning.storeFile` is set, and with the SDK debug key otherwise. The
benchmark build types follow `signingConfigs["debug"]` as before.

## Consequences

- Positive: every APK built on the dev PC installs over the last one; the owner's library, positions and settings
  survive updates.
- Negative / accepted risks: the phone needs one uninstall to move from the old key; APKs built by CI or on another
  machine still cannot update the phone. Losing the keystore or its passwords means another uninstall, so the owner
  keeps a backup of `flowfolio-dev.jks` together with the four properties.
- Follow-ups: the release signing key is a separate decision, before the first release build (Phase 9).
