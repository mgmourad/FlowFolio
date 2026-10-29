# ADR-0043: Retire the Python converter server

- **Status:** Accepted
- **Date:** 2026-10-27
- **Deciders:** product owner, lead engineer
- **Phase:** 3

## Context

Phase 0 built a Python skeleton in `converter/`: a FastAPI server with `/healthz` and `/version`, a Docker image, and
three CI jobs (lint and types, tests, the image with a smoke test). ADR-0009 then moved conversion onto the phone, in
Kotlin, so the product never calls a server. The Phase 3 plan (M1 item 6) left its fate to this phase and recommended
retiring it. The owner approved the recommendation on 2026-10-27.

What the Python side still offered:

- the corpus fetch script, which the plan wanted kept;
- an EPUBCheck runner. EPUBCheck is Java, so the JVM tests in `android/tools/convert` now run it directly
  (`ConversionTest`, `CorpusTest`, EPUBCheck 5.4.0).

## Options considered

1. **Keep the server and its CI jobs.** Costs CI minutes and weekly Dependabot updates for code nothing uses, and
   suggests a server path the product doesn't have.
2. **Retire it, keeping the developer tooling.** The fetch script moves to `tools/corpus/fetch_corpus.py` (standard
   library only, like the other `tools/` scripts), and EPUBCheck runs in the JVM tests.

## Decision

Option 2. `converter/` (the server, its tests, `pyproject.toml`, `uv.lock`, `Dockerfile`), `infra/docker/compose.dev.yml`,
the `converter` and `docker` CI jobs, their Dependabot entries and their pre-commit hooks are removed. Python stays for
the small standard-library scripts in `tools/`; ruff (pinned at 0.17.1, the version the converter used) checks them
through `uvx`.

## Consequences

- Positive: CI runs only what ships or guards what ships; no unused image or lock file to keep updated. Python in the
  repo is now only the standard-library scripts in `tools/`.
- Negative / accepted risks: a future server (say, a sync service) starts from nothing, though it would start from a
  new design anyway. The Phase 0 code stays in git history (`git log -- converter/`).
- Follow-ups: none. Known issue 4 (Starlette's `httpx` warning) goes with the server.
