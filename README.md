# FlowFolio

An open-source Android reader for college textbooks and ebooks, with a physics-based page curl
and a converter that turns fixed-layout textbook PDFs into reflowable EPUB 3, keeping figures,
tables, equations, sidebars, footnotes, annotations, and a link back to every original page.
Conversion is measured, not eyeballed: every block traces to its source page, and every page gets a
coverage score.

Designed for phones and tablets, with first-class support for foldables: a single page when
folded and a two-page spread when unfolded.

**Status:** Phase 3 (the converter, on the phone). PDF and EPUB reading are done (Phases 1 and 2). See
[CLAUDE.md](CLAUDE.md) for the current phase and [CHANGELOG.md](CHANGELOG.md) for progress.

## Repository

| Path | What |
|---|---|
| `android/` | Kotlin + Jetpack Compose app, including the on-device converter (`:core:conversion`) |
| `docs/` | Architecture, ADRs, conversion spec, license matrix |
| `samples/` | Corpus manifest, metric baselines, hand labels |
| `tools/` | Corpus fetch, test-file generators and other standard-library scripts |
| `infra/` | CI helpers |

## Quick start

Prerequisites: JDK 21, Android SDK (API 37); Python 3.13 and [uv](https://docs.astral.sh/uv/) for the tools and hooks.

```bash
# Android: build, test, install the debug app on a running emulator/device
cd android && ./gradlew assembleDebug testDebugUnitTest installDebug

# Converter: download the corpus, then run its tests on the JVM (EPUBCheck, metrics against baselines)
python -I tools/corpus/fetch_corpus.py
cd android && ./gradlew :core:conversion:test :tools:convert:test
```

Full command reference: [CLAUDE.md](CLAUDE.md#commands).

## Privacy

Books stay on your device: conversion runs on the phone. The only network use is the opt-in book details lookup
(Open Library) and the on-device translator (ML Kit downloads its language models and sends Google anonymous
usage data).

## License

[MIT](LICENSE). Third-party licenses: [docs/licenses.md](docs/licenses.md).
