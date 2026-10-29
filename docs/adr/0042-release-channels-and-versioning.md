# ADR-0042: Release channels, version names and build numbers

- **Status:** Accepted (product owner, 2026-10-27: "from this phase on, commit each phase to the GitHub repo and create
  a release. Phase 3-5 releases are alpha, phases 6-8 are canary and phase 9 are beta. Follow the best practices on
  naming the releases with their versions and build numbers.")
- **Date:** 2026-10-27
- **Deciders:** product owner, lead engineer
- **Phase:** 3

## Context

Until Phase 2 the app stayed at version 0.1.0 (code 1). Phases were committed to `main`, but no tags or GitHub
releases marked them. From Phase 3 the owner wants every signed-off phase to be a release, in a channel set by its
phase.

## Decision

1. **One release per signed-off phase.** When the owner signs off a phase, its work is committed and pushed to `main`,
   tagged, and published as a GitHub release, marked as a pre-release until 1.0.
2. **Version names follow Semantic Versioning 2.0.0.** Until the first stable release, the phase is the minor
   version: Phase N is `0.N.x`. A fix release within a phase raises the patch: `0.3.1-alpha.1`.

   | Phases | Channel | Example version name |
   |---|---|---|
   | 3-5 | alpha | `0.3.0-alpha.1`, `0.4.0-alpha.1`, `0.5.0-alpha.1` |
   | 6-8 | canary | `0.6.0-canary.1` ... `0.8.0-canary.1` |
   | 9 | beta | `0.9.0-beta.1` |
   | first stable | none | `1.0.0` |

   The pre-release number (`.1`, `.2`) counts releases of the same version, for example a release rebuilt after a
   fix that doesn't merit a new patch. Across phases the minor version decides order, so "canary" coming after "beta"
   in the alphabet never matters.
3. **Build numbers are Android's `versionCode`.** It must only ever increase, and is derived from the version:
   `MAJOR × 10,000,000 + MINOR × 100,000 + PATCH × 1,000 + N`, where N is the pre-release number (1-999). Examples:
   `0.3.0-alpha.1` is 300001, `0.6.0-canary.2` is 600002, `1.0.0` is 10000000. Every number stays under Google Play's
   2,100,000,000 limit.
4. **Names in each place:**
   - git tag `v0.3.0-alpha.1`;
   - `versionName` `0.3.0-alpha.1`, and `0.3.0-alpha.1-dev` for the debug build (the existing `-dev` suffix);
   - GitHub release title `FlowFolio 0.3.0-alpha.1 (build 300001)`;
   - APK `flowfolio-0.3.0-alpha.1-300001.apk`, attached with its SHA-256.
5. **Where the version lives:** `android/gradle.properties` holds `flowfolio.versionName` and
   `flowfolio.versionCode`, and the release build reads them, so a release changes one file. The release notes are
   the phase's section of `CHANGELOG.md`, which gets a `## [0.3.0-alpha.1] - date` heading in Keep a Changelog style,
   plus a link to the phase sign-off report.
6. **What a release ships:** the R8 release build, signed with the release key (kept out of the repo, like the dev
   key of ADR-0036). The phase's CI run on `main` must be green before the release is published.

## Consequences

- Each phase's state can be named, checked out and installed: `v0.3.0-alpha.1` and so on.
- Release APKs (`com.flowfolio`) install beside the debug app (`com.flowfolio.dev`) and keep their own library.
- A version picked at release time has to be put in `gradle.properties` and the CHANGELOG before the tag; the release
  checklist in CLAUDE.md lists the steps.
