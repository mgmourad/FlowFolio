# FlowFolio

An open-source Android reader for college textbooks and ebooks, with a physics-based page curl
and a converter that turns fixed-layout textbook PDFs into reflowable EPUB 3, keeping figures,
tables, equations, sidebars, footnotes, annotations, and a link back to every original page.
Conversion is measured, not eyeballed: every block traces to its source page, and every page gets a
coverage score.

Designed for phones and tablets, with first-class support for foldables: a single page when
folded and a two-page spread when unfolded.

**Status:** Phase 0 (foundation). Nothing user-facing yet. See [CLAUDE.md](CLAUDE.md) for the
current phase and [CHANGELOG.md](CHANGELOG.md) for progress.

## Repository

| Path | What |
|---|---|
| `android/` | Kotlin + Jetpack Compose app |
| `converter/` | Python FastAPI conversion service |
| `docs/` | Architecture, ADRs, conversion spec, license matrix |
| `samples/` | Golden corpus manifest and metric baselines |
| `infra/` | Docker compose, CI helpers |

## Quick start

Prerequisites: JDK 21, Android SDK (API 37), [uv](https://docs.astral.sh/uv/), Docker.

```bash
# Android: build, test, install the debug app on a running emulator/device
cd android && ./gradlew assembleDebug testDebugUnitTest installDebug

# Converter
cd converter && uv sync && uv run pytest && uv run flowfolio-convert serve
```

Full command reference: [CLAUDE.md](CLAUDE.md#commands).

## Privacy

Books stay on your device. Files are uploaded only when you convert a book, and only to the
converter endpoint you configure, which can be self-hosted.

## License

[MIT](LICENSE). Third-party licenses: [docs/licenses.md](docs/licenses.md).
