# ADR-0012: Phase 1 module structure

- **Status:** Accepted (approved with the Phase 1 build plan, 2026-10-16)
- **Date:** 2026-10-16
- **Phase:** 1
- **Builds on:** ADR-0005 (convention plugins), ADR-0009 (on-device converter), ADR-0010 (PDFium), ADR-0011 (pagecurl)

## Context

ADR-0005 deferred the module split until the PDF and page-curl spikes settled the architecture. They have.
The converter (Phase 3) must reuse the PDF engine and the data model, so those cannot live inside UI code.

## Decision

| Module | Type | Depends on | Purpose |
|---|---|---|---|
| `:core:model` | Kotlin/JVM | — | Plain data types (book, page reference, outline entry, reading position). No Android, so the Kotlin Multiplatform converter can share it later. |
| `:core:pdf` | Android library + NDK | `:core:model` | `PdfEngine` / `PdfDocument` interfaces and the PDFium JNI implementation (ADR-0010). |
| `:core:pagecurl` | Android library | — | Vendored pagecurl 1.5.1 with spread support (ADR-0011). |
| `:core:database` | Android library | `:core:model` | Room: books, reading progress. |
| `:core:datastore` | Android library | `:core:model` | DataStore: settings. |
| `:core:data` | Android library | database, datastore, pdf | Repositories; the only layer features talk to for data. |
| `:core:designsystem` | Android library | — | Theme and shared Compose components. |
| `:feature:library` | Android library | data, designsystem | Library screen, import. |
| `:feature:reader` | Android library | data, pdf, pagecurl, designsystem | Reader screen, page cache. |
| `:app` | Application | features | Navigation, DI graph, `Application`. |
| `:benchmark` | Test (macrobenchmark) | `:app` | Frame timing, startup, memory, baseline profile. |

Rules: features never depend on each other; `core` modules never depend on features; only `:core:data`
exposes repositories. New convention plugins: `flowfolio.android.library`, `flowfolio.jvm.library`,
`flowfolio.pdfium` (downloads and verifies prebuilt PDFium, wires CMake).

## Consequences

- Clear seams for the Phase 3 converter: it depends on `:core:model` and `:core:pdf`, not on UI.
- More build files; each is a few lines thanks to the convention plugins.
- `:core:pdf` contains native code, so it is the only module that needs the NDK.
