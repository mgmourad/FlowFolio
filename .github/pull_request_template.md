## What and why

<!-- One or two sentences. Link the ADR or issue if relevant. -->

## Verification

<!-- State exactly what you ran. Mark items as: tested / implemented but untested / stubbed. -->

- [ ] Android: `./gradlew spotlessCheck detekt lintDebug testDebugUnitTest assembleDebug`
- [ ] Converter: `uv run ruff check . && uv run mypy && uv run pytest`

## Checklist

- [ ] Conventional Commit title (`feat:`, `fix:`, `docs:` ...)
- [ ] New dependency? Added to `docs/licenses.md` with license and release date, and approved
- [ ] Significant decision? ADR added in `docs/adr/`
- [ ] `CHANGELOG.md` updated under *Unreleased*
- [ ] No secrets, no copyrighted sample files
