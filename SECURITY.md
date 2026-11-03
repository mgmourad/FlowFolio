# Security

## Reporting a vulnerability

Please report privately, through
[GitHub's private advisory form](https://github.com/mgmourad/FlowFolio/security/advisories/new), rather than in a
public issue. Tell us what you found, how to reproduce it, and what it lets an attacker do.

FlowFolio is a side project, not a company — expect a reply in days rather than hours. You will get credit in the
advisory and the changelog unless you would rather not.

## What FlowFolio touches

Most of the app is offline. The parts worth a second look:

| Area | What it does |
|---|---|
| **Book parsing** | PDFs go through PDFium over our own JNI bridge; EPUBs through our parser. Both read files the reader chose, and a malformed book is the obvious attack surface. |
| **EPUB rendering** | A `WebView` lays out equations, SVG and complex tables. It serves only files from inside the book being read and runs only scripts we ship; no network, no remote content. |
| **Embedded Python** | `:core:extract` runs yt-dlp on a bundled CPython to find an audio address. It is given only an address the reader typed, and returns only an address. |
| **Updates** | The app reads this repository's releases list and can download an APK into its cache. Installation is always Android's own installer, with its own prompt. |
| **Network** | Three destinations, each off or on demand: Open Library (book details), Google ML Kit (translation models), and the audio address a reader supplies. Nothing identifies the reader. |

## What it does not do

- No accounts, no analytics, no telemetry, no crash reporting, no advertising identifiers.
- Your library never leaves the device. Reading positions, highlights, notes and VoiceMarks are stored only in the
  app's private storage.
- A book read "from its current location" is never copied, moved or deleted by FlowFolio.

## Builds and signing

Releases are built from this repository and published on the
[Releases page](https://github.com/mgmourad/FlowFolio/releases) with a `.sha256` beside each APK. Check it before
installing:

```bash
shasum -a 256 flowfolio-*.apk
```

Release APKs are signed with the project's release key (`CN=FlowFolio Release`). **One exception:**
`0.3.0-alpha.2` was signed with the development key because the release key was not available on the build
machine; its release notes say so, and later builds return to the release key. An APK that does not install over
a previous one is signed differently — treat that as a reason to check where it came from.

FlowFolio is not on the Play Store and never asks for a password, an account or a payment. Anything claiming
otherwise is not FlowFolio.
