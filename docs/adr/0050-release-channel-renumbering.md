# ADR-0050: Renumber the release channels

- **Status:** Proposed
- **Date:** 2026-11-06
- **Deciders:** product owner, lead engineer
- **Phase:** 4

## Context

ADR-0042 mapped release channels to phases: **3–5 alpha, 6–8 canary, 9 beta**. That mapping no longer
matches the plan.

On 2026-11-05 the owner renumbered the phases (`PLAN.local.md`): old Phase 5 (equations and tables as
real markup) was **dropped**, and old Phase 7 (never defined) was **renumbered away**, leaving seven
phases, 0–7. Under the new numbering:

- **Phase 4** is verification, self-healing and good fallbacks (what ADR-0042 called Phase 4).
- **Phase 5** is the product flow around conversion (old Phase 6).
- **Phase 6** is reading features (old Phase 8).
- **Phase 7** is scanned books and OCR (old Phase 9).

ADR-0042's channel section is therefore wrong under the new numbers. ADR-0042 is **Accepted**, and the
project convention is that accepted ADRs are never edited, only superseded — so the correction needs its
own ADR.

## Options considered

1. **Edit ADR-0042's channel section.** Rejected: accepted ADRs are not edited.
2. **Supersede just the channel section with a new ADR.** Keeps ADR-0042's versioning scheme (version
   `0.<phase>.<patch>-<channel>.<n>`, `versionCode` arithmetic) intact while correcting only the phase
   ranges.
3. **Leave ADR-0042 as-is and let the channels drift.** Rejected: the mapping then misleads, and a reader
   trying to reason about which channel a build belongs to has to know which phase numbering is current.

## Decision

Supersede ADR-0042's release-channel section (and only that section) with:

> **3–4 alpha, 5–6 canary, 7 beta.**

The versioning scheme itself — version `0.<phase>.<patch>-<channel>.<n>`, `versionCode` =
MAJOR×10,000,000 + MINOR×100,000 + PATCH×1,000 + n — is unchanged and stays in force from ADR-0042.

## Consequences

- Positive: the release channels agree with the phase plan the project is actually following.
- Negative / accepted: none. Phase 4 (this release) is alpha under both the old and new mapping, so no
  existing release or channel changes meaning. The difference first matters when Phase 5 ships, which is
  canary under the new scheme rather than alpha.
- Supersedes: the channel section of ADR-0042. The rest of ADR-0042 remains accepted and in force.
