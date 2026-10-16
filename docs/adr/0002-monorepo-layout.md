# ADR-0002: Monorepo with strict app/converter boundary

- **Status:** Superseded in part by ADR-0009 (2026-10-16)
- **Date:** 2026-10-16
- **Phase:** 0

## Context

The Android reader and the Python converter evolve together: the converter's output
(EPUB 3 + `flowfolio.json` sidecar) is the app's input. Changes to the output contract
must land atomically with both producer and consumer changes.

## Options considered

1. **Two repositories**: independent release cycles, but contract changes need coordinated PRs.
2. **Monorepo, shared code**: tempting to share models, but Kotlin and Python cannot share code directly.
3. **Monorepo, shared contract only**: one repo; the only shared artifact is the specification and its JSON Schema.

## Decision

Option 3. Layout: `android/`, `converter/`, `docs/`, `samples/`, `infra/`, `.github/`.
The single source of truth for the interface is `docs/conversion-spec.md`, plus a JSON Schema
for the sidecar (generated from the converter's Pydantic models starting in Phase 3, committed,
and validated in tests on both sides).

CI workflows are path-filtered so Android changes do not run Python jobs and vice versa;
a `ci-gate` job aggregates results into one required check.

## Consequences

- Atomic contract changes; one issue tracker; one CHANGELOG.
- Each subproject keeps its own toolchain (Gradle, uv) and can be built in isolation.
- Repository size must be watched: no PDFs or large binaries are committed (see ADR-0003 and the corpus manifest).
