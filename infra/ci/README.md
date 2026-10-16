# CI helpers

Workflows live in `.github/workflows/` (GitHub requires that location). This folder holds scripts
those workflows call once they outgrow inline steps (for example the golden-corpus regression
runner in Phase 3). Design: [ADR-0008](../../docs/adr/0008-ci-design.md).
