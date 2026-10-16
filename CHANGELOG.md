# Changelog

All notable changes to this project are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning: [SemVer](https://semver.org/).

## [Unreleased]

### Added
- Phase 0 foundation: monorepo layout, MIT license, `CLAUDE.md`, architecture overview,
  ADRs 0001–0008, dependency license matrix, conversion output contract draft (spec v0.1.0).
- Android skeleton: single-activity Compose + Material 3 app with Hilt, empty Library screen,
  convention plugins, Spotless/ktlint, detekt, unit tests, Robolectric Compose tests, and an
  instrumented launch test.
- Converter skeleton: FastAPI service with `/healthz` and `/version`, `flowfolio-convert` CLI,
  env-based settings, tests, multi-stage non-root Docker image, dev compose file.
- CI: path-aware GitHub Actions workflow (Android, converter, Docker smoke test, Conventional
  Commits check, `ci-gate`), Dependabot, pre-commit hooks.
- Golden corpus manifest schema (no books yet).
