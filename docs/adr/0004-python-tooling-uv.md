# ADR-0004: uv for converter project and dependency management

- **Status:** Accepted
- **Date:** 2026-10-16
- **Phase:** 0

## Context

The converter needs reproducible environments across Windows (dev), Linux (CI), and Docker,
with a lockfile and a pinned Python version. ML dependencies later (torch, Docling) make
resolution slow and fragile with older tools.

## Options considered

1. **Poetry**: mature, lockfile; slower resolver; does not manage Python versions itself.
2. **pip + pip-tools**: minimal; more glue scripts; no Python version management.
3. **uv** (Astral, MIT/Apache-2.0): very fast resolver and installer, standard `pyproject.toml`
   (PEP 621), cross-platform lockfile (`uv.lock`), manages Python interpreters, actively released.

## Decision

Use **uv**. Python is pinned with `.python-version` (3.13). Dependencies live in
`converter/pyproject.toml`; `uv.lock` is committed; CI and Docker use `uv sync --locked`.
Dev tools (ruff, mypy, pytest) are in the `dev` dependency group.

## Consequences

- Fast CI and Docker builds; identical lock across platforms.
- uv is younger than Poetry; if it stalls, migration is cheap because `pyproject.toml` is standard.
- Python 3.13 is chosen over 3.12; if a required ML wheel lacks 3.13 support, we pin down via a new ADR.
