# Contributing to FlowFolio

Thanks for looking. FlowFolio is a reading app, and the thing it is most trying to be is *quiet* — so the bar for
a change is less "does it work" than "does it make reading better, and does it stay out of the way".

## Before you build

```bash
git clone https://github.com/mgmourad/FlowFolio.git && cd FlowFolio
uv tool install pre-commit && pre-commit install     # commit hooks; or use pipx/pip
cd android && ./gradlew assembleDebug
```

You need **JDK 21**, the **Android SDK** with platform 37, and a **Python 3.12** on `PATH` — the build embeds
yt-dlp through Chaquopy and pip-installs it (see [ADR-0047](docs/adr/0047-audio-extraction-with-yt-dlp.md)).

## Before you open a pull request

```bash
cd android
./gradlew spotlessApply                                  # fix formatting
./gradlew spotlessCheck detekt lintDebug testDebugUnitTest
./gradlew :feature:reader:riffle:verifyRoborazziDebug    # screenshot tests
```

CI runs the same things. If a screenshot test fails because you *meant* to change the drawing, re-record it with
`recordRoborazziDebug` and say so in the pull request.

## How the project works

- **Decisions get an ADR.** Anything structural — a dependency, a storage format, a new module — gets a short
  record in [`docs/adr/`](docs/adr), from [the template](docs/adr/0000-template.md). Accepted ADRs are never
  edited, only superseded. Read a few; they explain most of the "why" you will want.
- **[`CLAUDE.md`](CLAUDE.md) is the project's working memory** — the current phase, the architecture in brief, and
  a long list of hard-won known issues. Read it before anything non-trivial. It is written for whoever picks the
  work up next, including AI assistants.
- **[`CHANGELOG.md`](CHANGELOG.md)** follows Keep a Changelog. Add to `[Unreleased]`.
- **Commits follow [Conventional Commits](https://www.conventionalcommits.org/)** and a hook enforces it. Scopes in
  use: `android`, `ci`, `docs`, `tools`.

## Dependencies

Adding one is a decision, not a convenience. Open an issue first with its **licence**, its **last release date**
and the **alternatives** you considered.

**No GPL, LGPL or AGPL code, ever.** Everything ships inside the app, so a copyleft licence would take the whole
app with it. The matrix in [`docs/licenses.md`](docs/licenses.md) lists every dependency and why it is allowed;
add your row there.

## Writing code here

- Kotlin, ktlint `android_studio` style through Spotless, detekt on top. No trailing commas.
- Compose with Material 3, single activity, MVVM.
- Use `:core:designsystem` rather than raw Material colours. A new token needs a value in **every** theme.
- Comments say *why*, not *what*. The existing ones set the tone: they explain the constraint that forced the
  shape of the code, and they are worth matching.
- Never claim something works without running it. "Tested", "implemented but untested" and "stubbed" are all fine
  words; a wrong claim is not.

## Testing

- JVM and Robolectric tests run in CI. On-device tests and benchmarks are run by hand — `CLAUDE.md` has the
  commands and the traps.
- Behaviour that only a device can show (recording, gestures, GPU memory) still deserves a test of the logic
  underneath it. There are examples in the reader's tests.

## Reporting a bug

Please say which **device and Android version**, whether the book is a **PDF or EPUB**, and what you expected
instead. If it is about a specific book, saying which one (or attaching a page) helps enormously — most hard bugs
here are really a book doing something unusual.

For anything security-related, see [SECURITY.md](SECURITY.md) instead.
