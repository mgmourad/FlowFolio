# ADR-0005: Android build structure (version catalog, convention plugins, modules)

- **Status:** Accepted
- **Date:** 2026-10-16
- **Phase:** 0

## Context

The brief calls for a multi-module app (feature and core modules) once Phase 1 stabilizes.
In Gradle, each module repeats Android/Kotlin/Compose configuration unless that configuration
is centralized. Retrofitting centralization later touches every build file.

Android background: a Gradle *module* is a separately compiled unit with its own
`build.gradle.kts`. Splitting into modules gives faster incremental builds and enforced layer
boundaries (a `domain` module cannot import Android UI code if it does not depend on it).

## Options considered

1. **Single module, refactor later**: simplest now; expensive later.
2. **Full multi-module now**: premature; module boundaries unknown before the PDF and pagination spikes.
3. **Single `:app` module now, with convention plugins and a version catalog ready for splitting**.

## Decision

Option 3:

- `gradle/libs.versions.toml` (version catalog) is the only place versions are declared.
- `build-logic/` is an included build with convention plugins:
  `flowfolio.android.application`, `flowfolio.android.compose`, `flowfolio.hilt`,
  `flowfolio.quality` (Spotless/ktlint + detekt). New modules apply a plugin in one line.
- Gradle Kotlin DSL everywhere. Configuration cache enabled.
- Android Gradle Plugin 9.x uses its built-in Kotlin support, so modules do not apply the separate
  `org.jetbrains.kotlin.android` plugin.
- Expected split in Phase 1: `:core:model`, `:core:data`, `:core:designsystem`,
  `:core:pdf`, `:feature:library`, `:feature:reader`. Recorded in a follow-up ADR.

## Consequences

- Adding modules in Phase 1 is cheap and consistent.
- Slightly more upfront build code (build-logic) than a plain single module.
