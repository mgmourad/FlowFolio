# ADR-0044: The conversion engine is a plain JVM module, with JNA only in desktop tooling

- **Status:** Accepted (product owner, 2026-11-06, with the Phase 3 sign-off)
- **Date:** 2026-10-27
- **Deciders:** product owner, lead engineer
- **Phase:** 3
- **Supersedes:** ADR-0009 in part (its choice of Kotlin Multiplatform for `:core:conversion`)

## Context

ADR-0009 put conversion on the phone and planned `:core:conversion` as a Kotlin Multiplatform module (Android and JVM
targets), so that the same engine runs in the app and in CI's corpus quality gate. Building Phase 3 showed that the
engine needs no platform code of its own:

- the pipeline (extraction, design system, regions, reading order, blocks, headings, linking, EPUB writing) is pure
  Kotlin over a `PdfSource` interface and the `Document` IR;
- the only platform-specific parts are reading a PDF and encoding images. Those live behind `PdfSource`, implemented
  on Android by `PdfiumConversionSource` in `:core:pdf` (our JNI bridge, WebP images) and on the desktop by
  `JnaPdfSource` in `android/tools/convert` (PNG images).

The owner's rule (2026-10-27): conversion from PDF to EPUB happens on the phone, never on a PC; desktop tools such as
JNA are fine for developing the feature.

## Options considered

1. **Kotlin Multiplatform, as ADR-0009 planned.** `commonMain` plus `androidMain` and `jvmMain`. Adds a KMP plugin
   with AGP 9's Android library target and a convention plugin, for no code that differs per platform.
2. **A plain Kotlin/JVM module** (`flowfolio.jvm.library`, like `:core:model`), which Android modules can depend on
   directly. The platform parts implement its interface in the modules that already own PDFium.
3. **The JNI bridge built for the desktop too** (as the plan's M1 item 2 said): a CMake build of `pdfium_jni.cpp` for
   Windows and Linux. That's a second native toolchain to maintain for development only.

## Decision

Option 2 for the engine, and for the desktop: JNA 5.19.1 (Apache-2.0 / LGPL-2.1 dual licence; we use it under
Apache-2.0) calls PDFium's own C API from the pdfium-binaries Windows and Linux builds of the release the app uses
(`FetchHostPdfiumTask`, SHA-256 checked). JNA and the desktop PDFium are in `android/tools/convert` only. That module is
never a dependency of the app, so neither ships.

## Consequences

- Positive: one engine, tested on the JVM with the real PDFium (the corpus harness and EPUBCheck run there) and run
  unchanged on the phone; no KMP build setup; no desktop native build.
- Negative / accepted risks: the desktop and the phone read PDFs through different bindings (JNA vs JNI). Both call
  the same PDFium functions, and the phone's extraction is checked on the device (M1's "Done when"). The desktop writes
  PNG where the phone writes WebP, so EPUB sizes are measured on the phone.
- Follow-ups: if the engine ever needs another platform (say, a desktop app), revisit KMP then.
