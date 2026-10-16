# ADR-0008: CI design and supply-chain hygiene

- **Status:** Accepted
- **Date:** 2026-10-16
- **Phase:** 0

## Context

Two toolchains (Gradle and uv/Docker) in one repository. The repository is private for now
and Actions minutes are limited; Android builds are minute-heavy. Dependencies and Actions are
supply-chain risks.

## Options considered

1. **One workflow running everything on every push**: simple, wasteful.
2. **Path-filtered workflows per subproject + aggregate gate**: efficient; needs a gate job so
   branch protection works when a filtered workflow is skipped.
3. **External CI (Azure Pipelines, etc.)**: no advantage for a GitHub-hosted project.

## Decision

Option 2, in a single workflow file `ci.yml`:

- A `changes` job detects which paths changed (plain `git diff`, no third-party action).
- `android` job: Gradle wrapper validation, `spotlessCheck detekt lintDebug testDebugUnitTest assembleDebug`;
  uploads the debug APK and reports.
- `converter` job: `uv sync --locked`, `ruff check`, `ruff format --check`, `mypy`, `pytest`.
- `docker` job: builds the converter image (not pushed in Phase 0), runs it, and checks `GET /healthz`.
- `commits` job: checks commit subjects against Conventional Commits.
- `ci-gate` job: always runs; fails if any needed job failed. This is the single required check.
- All third-party actions pinned to full commit SHAs, with the version in a comment.
- Default `permissions: contents: read`; `concurrency` cancels superseded runs per ref.
- Dependabot updates GitHub Actions, Gradle, uv, and Docker weekly.

Not in Phase 0: emulator-based instrumented tests (Phase 1, likely on a schedule or via Gradle
Managed Devices), the golden-corpus quality regression job (Phase 3), image publishing to GHCR
(when the converter does real work).

## Consequences

- Fast, cheap runs for one-sided changes.
- SHA pinning needs Dependabot to stay current.
- Branch protection on a **private** repository requires a paid GitHub plan; until then the gate is
  advisory. It becomes enforceable when the repository goes public.
