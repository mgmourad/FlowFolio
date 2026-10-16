# ADR-0001: Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-10-16
- **Phase:** 0

## Context

FlowFolio spans an Android app and a Python conversion service and will be built in
phases over a long period. Decisions about libraries, licensing, and architecture
need to be traceable so later changes are made knowingly.

## Options considered

1. **No formal records**: decisions live in commit messages and chat. Cheap, but not discoverable.
2. **Lightweight ADRs in the repo (MADR-style Markdown)**: versioned with the code, reviewed in PRs.
3. **External wiki**: separated from code, drifts out of date.

## Decision

Use numbered Markdown ADRs in `docs/adr/`, based on `0000-template.md`. Every significant
technical choice (dependency, architecture, licensing, platform constraint) gets one.
ADRs are immutable once accepted; changes are made by a new ADR that supersedes the old one.

## Consequences

- Decisions are reviewable alongside code.
- Small overhead per decision; keep ADRs short (one page).
- `CLAUDE.md` links to the ADR index for quick orientation.
