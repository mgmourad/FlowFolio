# ADR-0007: Hilt for dependency injection

- **Status:** Accepted
- **Date:** 2026-10-16
- **Phase:** 0

## Context

The app follows MVVM with `ui`, `domain`, and `data` layers. ViewModels, repositories,
Room DAOs, and WorkManager workers need their dependencies wired consistently and replaceable in tests.

Android background: Android creates Activities, ViewModels, and Workers itself, so constructor
injection needs framework integration. Hilt is Google's Dagger-based layer that provides it.

## Options considered

1. **Hilt** (Apache-2.0): compile-time graph validation, first-party integration with
   ViewModel, WorkManager, and Compose navigation; uses annotation processing (KSP), which costs build time.
2. **Koin** (Apache-2.0): pure Kotlin, no codegen, simpler; errors surface at runtime.
3. **Manual DI**: no library; grows unwieldy across many modules.

## Decision

Hilt with KSP, as specified in the technology baseline.

## Consequences

- Missing bindings fail the build instead of crashing at runtime.
- Extra build time from code generation; mitigated by the Gradle build cache.
- Tests use `@HiltAndroidTest` or construct classes directly; domain classes stay DI-agnostic (constructor injection only).
