# ADR-0009: On-device conversion engine in Kotlin Multiplatform

- **Status:** Accepted (feasibility to be confirmed by the Phase 1 spike probe)
- **Date:** 2026-10-16
- **Phase:** 1
- **Supersedes:** ADR-0002 (in part), ADR-0004; amends ADR-0003

## Context

The original brief placed conversion in a Python server (FastAPI, Docker). The product owner
decided on 2026-10-16 that **conversion runs natively inside the app**, on the device. Reasons:
it is the product's differentiator, books never leave the device, it works offline, and there is
no service to operate. FlowFolio mode can then be on by default for every user.

Consequences for engineering:
- Python cannot realistically ship in the app. Embedding CPython (Chaquopy) is possible, but the
  ML and PDF libraries the pipeline would need (PyTorch, Docling, most PDF bindings) do not run on Android.
- The pipeline needs low-level PDF data (glyph runs with fonts and positions, vector paths,
  images, outline, page labels), not just rendered pages and plain text.
- Quality must still be measured on the golden corpus in CI, without requiring a phone.

## Options considered

1. **Kotlin Multiplatform engine (Android + JVM targets)**: one codebase; runs in the app and on
   the JVM in CI. Native PDFium and ONNX Runtime work on both targets.
2. **Android-only Kotlin**: simpler build; corpus regression in CI would need emulators (slow, costly).
3. **C++ core with JNI**: fastest at runtime; much slower to develop and test; harder to hire for.
4. **Python prototype first, port to Kotlin later**: fast algorithm iteration, but every stage is
   built twice and the two drift.

## Decision

Option 1.

- `:core:conversion` is a Kotlin Multiplatform module with `commonMain` (pipeline stages A–I over the
  typed `Document` IR), `androidMain`, and `jvmMain` (platform bindings).
- **PDF access:** PDFium (BSD-3/Apache-2.0) through **our own thin JNI bridge**, built against prebuilt
  PDFium binaries for Android ABIs and Linux/Windows x64. The same PDFium build serves rendering
  (reader) and extraction (converter), so the two never disagree about a page. Final library choice
  per ADR-0010 after the spike.
- **ML:** ONNX Runtime (MIT) on both targets, with the same model files. Models (layout, embeddings,
  small language model, later OCR) must have permissive weight licenses and are downloaded on first
  use rather than bundled. Downloading a model sends no user data.
- **Execution in the app:** a resumable WorkManager job running as a foreground service with a
  progress notification, chapter-first output, pausing on low battery or thermal pressure.
- **CI:** the golden-corpus quality gate runs on the JVM target.
- The output contract (`docs/conversion-spec.md`) is unchanged: EPUB 3 + `flowfolio.json`, now produced on device.
- The Python `converter/` service is no longer the product path. Its future (developer tooling for
  corpus fetching, EPUBCheck validation, and metric reports, or removal) is decided in the Phase 3 plan.
  The Docker image and server CI jobs remain until then.

## Consequences

- Positive: privacy and offline by construction; no hosting cost; one engine for rendering and extraction;
  CI quality gates without devices.
- Negative / accepted risks:
  - Conversion of a large textbook takes minutes to tens of minutes on a phone; slower on budget devices.
  - Model size is limited, so quality relies more on deterministic geometry, design-system inference, and
    verification. Low-confidence regions fall back to the original page image (spec section 4).
  - We own a JNI bridge and native build (NDK, CMake) and must ship native libraries per ABI.
- **ADR-0003 amended:** everything ships in the app, so there is **no copyleft code or weights anywhere**.
  The "copyleft server-side via a dedicated ADR" path no longer exists.
- **ADR-0002 superseded in part:** the shared artifact between app and converter is no longer a network
  contract but an in-process module; the spec still governs the output format.
- **ADR-0004 superseded:** uv remains only for Python developer tooling, if kept.
- Follow-up: the Phase 1 spike measures on the reference device (Galaxy Z Fold 8 Ultra) the cost of
  extracting glyph runs, fonts, and paths for 50 textbook pages and of running one small ONNX model.
