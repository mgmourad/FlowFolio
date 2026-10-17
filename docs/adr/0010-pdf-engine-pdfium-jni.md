# ADR-0010: PDFium through our own JNI bridge for rendering and extraction

- **Status:** Proposed (awaiting product-owner approval)
- **Date:** 2026-10-16
- **Phase:** 1
- **Evidence:** `docs/spikes/2026-10-pdf-renderer.md`

## Context

The reader needs fast, sharp page rendering on foldables, the table of contents (outline), and printed page numbers
(page labels). The on-device converter (ADR-0009) needs glyph positions, fonts, colors, vector paths, and images.

## Options considered

1. **Framework `PdfRenderer`:** no dependency; same image quality (it is PDFium internally); no outline, no page labels,
   no glyph/path data; one page open at a time; p95 render ~35 ms.
2. **Jetpack `androidx.pdf` (beta):** sandboxed process (a security plus), text/links/search; no outline or page labels;
   slower on the large inner screen (p95 56–69 ms) because of cross-process transfer; beta API.
3. **PdfiumAndroidKt wrapper:** PDFium with outline and text; no page labels or glyph/path data; ships its own
   `libpdfium.so`; single maintainer.
4. **PDFium via our own JNI bridge over `pdfium-binaries`:** everything we need; best p95 (17 ms cover, 25 ms inner);
   6.3 MB per ABI; we own ~300 lines of C++ and the native build.

## Decision

Option 4. `:core:pdf` wraps a `PdfEngine` interface (as in the spike) with a single PDFium implementation used by
both the reader and the converter. PDFium binaries come from `bblanchon/pdfium-binaries`, pinned by release tag and
SHA-256 and downloaded at build time (never committed). All PDFium calls are serialized (PDFium is not thread-safe).

## Consequences

- One engine for reading and converting; outline, labels, and Stage B data available from day one.
- NDK/CMake become part of the build and CI (Linux runners download the Android binaries the same way).
- **Security:** untrusted PDFs are parsed in our process. Mitigations: track pdfium-binaries releases (Dependabot-style
  check), fuzz the bridge with malformed PDFs in Phase 1, run conversion in an isolated process.
- The Kotlin Multiplatform JVM target (ADR-0009) will load the same bridge built against `pdfium-linux-x64` /
  `pdfium-win-x64` for CI corpus runs.
- If maintenance of pdfium-binaries stalls, we can build PDFium ourselves from Chromium sources (documented fallback).
