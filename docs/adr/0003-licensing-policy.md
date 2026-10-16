# ADR-0003: Licensing policy for FlowFolio and its dependencies

- **Status:** Accepted, amended by ADR-0009 (2026-10-16): no copyleft anywhere, including the converter
- **Date:** 2026-10-16
- **Phase:** 0

## Context

FlowFolio is open source under the **MIT license** (product owner decision, 2026-10-16).
It may later be commercialized (for example a hosted converter or a paid app listing).
The best tools in PDF processing are often copyleft: MuPDF/PyMuPDF (AGPL-3.0),
Marker (GPL-3.0 code, separately licensed model weights), Ultralytics YOLO (AGPL-3.0).

Key facts:

- MIT code may be combined with GPL/AGPL code, but the **combined work** is then distributed
  under the copyleft terms. That would make the app or converter effectively GPL/AGPL for
  anyone who distributes it, contradicting the intent of an MIT project and blocking
  commercial or proprietary downstream use.
- **AGPL-3.0 section 13**: if a modified AGPL program is offered to users over a network, its
  complete corresponding source must be offered to those users. Running an AGPL-based
  converter as a hosted service triggers this, even without distributing binaries.
- GPL code in an Android APK makes the APK a GPL work; app stores' terms can also conflict
  with GPL obligations.
- ML **model weights** carry licenses separate from the code that loads them (some are
  non-commercial, for example CC BY-NC). Weights must be checked independently.

## Options considered

1. **Permissive only, everywhere**: simplest legally; may cost conversion quality.
2. **Permissive in the app; copyleft allowed server-side case by case**: keeps the app clean;
   converter quality can use stronger tools where justified.
3. **Accept copyleft anywhere**: maximal quality; turns the project GPL/AGPL in practice.

## Decision

Option 2, with these rules:

1. **Android app**: only permissive licenses compatible with MIT distribution
   (MIT, BSD-2/3, Apache-2.0, ISC, Zlib, Unicode, OFL for fonts, EPL for test-only
   dependencies). No GPL, LGPL (static linking issues on Android), AGPL, or SSPL.
2. **Converter**: permissive by default. A copyleft dependency requires its own ADR that
   documents the obligation, the quality gain measured on the golden corpus, and the
   impact on hosting. It must be isolated behind an engine interface so it can be swapped
   out, and optional (an "extras" install), so the default converter build stays MIT-clean.
3. **Model weights**: recorded in `docs/licenses.md` with their own license; non-commercial
   weights are not allowed in default builds.
4. **Fonts**: only OFL/Apache fonts are bundled. Fonts extracted from user PDFs are embedded
   in the user's own EPUB only when the font's embedding permissions (OS/2 `fsType`) allow it.
5. **Tooling** (build, lint, CI) is not distributed and may use any OSI license.
6. Every dependency is listed in `docs/licenses.md` before it is added.

## Consequences

- The default build of every component can be redistributed under MIT terms.
- Some best-in-class tools (PyMuPDF, Marker) are off the default path; Phase 3 must show
  that permissive engines (pypdfium2, pdfminer.six/pdfplumber, Docling) meet the quality bar,
  or justify an optional copyleft engine through a new ADR.
- CI adds a license check for the converter's Python dependencies starting in Phase 3.
