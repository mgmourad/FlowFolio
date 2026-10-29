# Phase 3 plan: converter v1, on the phone

- **Status:** Approved 2026-10-27. Three milestones, at the owner's request ("consolidate the milestones into 3 major
  ones"). M1 and M2 built together, 2026-10-27; their sign-off report is `docs/plans/phase3-m1m2-signoff.md`.
  Changed while building: the engine is a plain JVM module and the desktop reads PDFs through JNA rather than a desktop
  build of our JNI bridge (ADR-0044, proposed for the sign-off; JNA owner-approved 2026-10-27; the fallback named in the
  risks below); the Python server is retired by ADR-0043 (ADR-0042 went to release channels).
- **Brief:** "Converter v1, on the phone (`:core:conversion`). It turns born-digital PDFs into EPUB plus a sidecar
  file. It also needs a corpus harness with metrics." (Phase 1 sign-off, the phase list.)
- **Decided before:** ADR-0009 (an on-device engine in Kotlin Multiplatform, PDFium through our JNI bridge, ONNX
  Runtime for models, a WorkManager job, the quality gate on the JVM), ADR-0010 (PDFium), the output contract
  `docs/conversion-spec.md`, and the Phase 1 spike (raw extraction of a 959-page textbook takes 5-6 s on the Fold).
- **Design reference:** `docs/design/FlowFolio_Mock.html`, for the little UI this phase adds.

## Why this phase matters

FlowFolio mode, reflowed text, is the product's default and its reason to exist. Phase 2 built the reader for it:
any EPUB is paginated, typeset, annotated and turned like a book. Phase 3 builds the other half: turning a
born-digital PDF into that EPUB, on the phone, without the book ever leaving it.

Phase 3 makes the converter correct on ordinary content and measurable. The hard content comes later: verification
by pixel comparison (Phase 4); equations, tables and cross-references (Phase 5); the polished in-app flow with
progressive delivery and a dual view (Phase 6); scanned books (Phase 9). Until each of those lands, a region the
converter can't handle goes out as an image of the original page, never as silently wrong text (spec section 4).

## Scope

In:
- `:core:conversion`, a Kotlin Multiplatform module (Android and JVM) holding the pipeline over a typed `Document` IR.
- Stages A (intake and classify), B (extraction), C (design-system inference), D (layout and reading order), F
  (book-wide linking, basic) and I (EPUB assembly), as in `docs/architecture.md`.
- Figures as cropped images. Tables and equations go out as page-image fallback blocks (Phase 5 converts them).
- A minimal text-coverage check, so pages the converter can't account for become fallbacks (spec section 4).
- The corpus harness: fetching the corpus, metrics, baselines, and a CI quality gate on the JVM.
- Running a conversion on the phone, in the background, and opening the result in the Phase 2 reader.

Out (later phases): pixel-diff verification and self-healing (4); equations, real tables, footnote and
cross-reference linking beyond the basics (5); the product flow with progressive delivery, a dual view and the health
badge (6); scanned pages and OCR (9).

## Milestones

### M1: Engine and extraction

The foundations, and everything the later stages read.

1. **`:core:conversion` as a Kotlin Multiplatform module** (`commonMain`, `androidMain`, `jvmMain`). It gets a
   convention plugin in `android/build-logic`, so the KMP-with-AGP-9 setup lives in one place. A short spike comes
   first: Kotlin 2.4 KMP plus AGP 9's Android library target, building and testing on both targets in CI.
2. **PDFium on the JVM.** The same JNI bridge, built for Windows x64 (the dev machine) and Linux x64 (CI) against
   pdfium-binaries, so the engine and its tests run without a phone.
3. **Extraction APIs on the bridge**, beyond what the reader needs:
   - glyph runs, with font name, size, weight and style, colour, and positions;
   - vector paths (rules, boxes, lines);
   - images with their placement;
   - the outline and page labels.

   They're fuzzed with the Phase 1 robustness PDFs.
4. **The `Document` IR** (pages, glyph runs, lines, blocks, styles, artifacts), serializable to JSON for debugging.
   Stages A and B fill it: born-digital, scanned and mixed pages are told apart (scanned pages are marked for Phase
   9), and glyphs are grouped into words and lines.
5. **The corpus harness.**
   - A fetch script downloads the openly licensed PDFs by URL and checks them by sha256; PDFs are never committed.
   - The corpus: four OpenStax titles (CC BY 4.0, each licence confirmed on its title page before it is added) and
     our generated test PDFs.
   - A JVM command runs any stage on any book and writes the IR, so a stage can be checked on its own.
6. **The fate of the Python `converter/`** (ADR-0043). Recommended: retire the FastAPI server, its Docker image and
   their CI jobs, since the product never calls them (ADR-0009). Keep only what helps developers: the corpus fetch
   script and an EPUBCheck runner. EPUBCheck is Java, so it can also run straight from the JVM tests.

**Done when:** the textbook's 1,000 pages extract on the JVM and on the Fold; the IR of a sample page is checked by
hand against the PDF; CI builds and tests the KMP module on both targets.

### M2: From structure to EPUB

The middle of the pipeline: a converted book you can open.

1. **Stage C, design-system inference.** Fonts, sizes and weights are clustered into the book's styles: body text,
   headings by level, captions, sidebars, code. Running headers, footers and page numbers become recorded artifacts.
   The printed page numbers they carry feed `pageLabel` where the PDF has no `/PageLabels` (the OpenStax PDF has
   none).
2. **Stage D, layout and reading order.**
   - Columns, blocks, paragraphs, including paragraphs that continue across pages and columns.
   - Lists, block quotes, simple sidebars.
   - Figures with their captions.
3. **Stage F, basic book-wide linking.** Chapters from the PDF outline, else from top-level headings, else fixed page
   ranges (flagged). Internal links and outline targets point at blocks.
4. **Stage I, EPUB assembly** to the spec:
   - one XHTML file per chapter;
   - `nav.xhtml` with toc, page-list and landmarks;
   - page-break markers;
   - `book.css` with the inferred palette;
   - `flowfolio.json` with blocks, bounding boxes, confidence and method.

   The JSON Schema is generated and committed (`docs/schemas/flowfolio.schema.json`), and the spec becomes normative
   as v1.0.
5. **The coverage check.** Each page's source text is aligned with the text emitted, and pages under the threshold
   (0.98 for now) go out as fallback images. Tables and equations are fallbacks by design in this phase.
6. **Metrics:**
   - text coverage per page;
   - EPUBCheck errors and warnings;
   - chapter and heading accuracy, and reading order, on a small hand-labelled set (about 30 pages across the
     corpus, two-column pages included);
   - pages left as fallback.

   Baselines go in `samples/golden/`.

**Done when:** every corpus book converts on the JVM to an EPUB with no EPUBCheck errors, that opens in the Phase 2
reader with its chapters and printed page numbers; the metrics have baselines.

### M3: On the phone, and the quality gate

The engine running where it will live, and quality that can't slip unnoticed.

*Changed 2026-10-27 (owner): items 1 and 2 become **Dynamic Flow**, a reading mode of the PDF itself (ADR-0045).*

1. **Conversion on the phone.**
   - A foreground service with a progress notification runs it, in a separate process (the Phase 1 security note:
     PDFium parsing untrusted files); no WorkManager (ADR-0045).
   - The job can be resumed, and it pauses on low battery or thermal pressure.
   - The bundle is stored with the book.
2. **Dynamic Flow** (ADR-0045): the "Aa" sheet's PDF / Dynamic Flow switch, the "Turn on Dynamic Flow upon opening"
   setting, Turn on Dynamic Flow in the library's menus and list swipe; progress ring and page-number bar, phone and
   in-app notifications; highlights, notes and bookmarks per view, with "convert the changes" when switching.
3. **Performance and memory on the Fold.**
   - A target to agree: *University Physics*, about 1,000 pages, converted in the background in 5 minutes or less.
   - Memory bounded in the conversion process.
   - The reader stays smooth while a conversion runs.
4. **Models only where needed.** v1 is deterministic: geometry and the inferred design system. If a stage clearly
   needs a model (for example classifying sidebars), its ADR settles ONNX Runtime's 32.6 MB per ABI (a reduced
   build, or a download on first use). Otherwise ONNX Runtime waits for Phase 4 or 5.
5. **The CI quality gate.** The JVM converts the corpus books (or a fixed subset, if the full set is too slow for
   every push) and fails on regressions beyond tolerance against the baselines. Merging a deliberate change updates
   them.
6. **Phase sign-off report**, with measurements, as for Phases 1 and 2.

**Done when:** the exit criteria below hold.

## Exit criteria

- **Valid output:** every born-digital corpus book converts to an EPUB with no EPUBCheck errors, plus a
  `flowfolio.json` that validates against the committed schema.
- **Nothing lost silently:** text coverage of at least 0.98 on at least 95% of pages across the corpus, tables and
  equations aside (they are fallbacks this phase). Every page under it goes out as a fallback image and is listed.
- **Structure:**
  - chapters match the PDF outline where there is one;
  - heading levels and reading order are correct on the hand-labelled pages (targets set from the first baselines in
    M2);
  - printed page numbers work in "Go to page".
- **On the phone:** *University Physics* converts on the Fold within the agreed time, in the background, in its own
  process, and opens in the reader.
- **Quality gate:** CI runs the corpus metrics on the JVM and fails on regression.
- **Tests:** unit tests per stage, JVM tests over the corpus, and instrumented tests for the job on the device. CI
  stays green.

## Risks

- **KMP with AGP 9** is young. The M1 spike settles it first; the fallback is an Android library with a separate JVM
  test module sharing the sources.
- **PDFium on desktop JVMs**: native builds for two more platforms. pdfium-binaries publishes them, and our bridge is
  small.
- **Reading order** on two-column pages with sidebars is the hardest part of M2. Born-digital PDFs often carry
  structure (tagged PDF); we use it when present and fall back to geometry.
- **Conversion time on slower phones**: measured on the Fold, and noted for Phase 6's progressive delivery.

## For the owner to approve

1. **This plan and its three milestones.**
2. **Retiring the Python server** (ADR-0043, M1 item 6): keep only the corpus fetch script and an EPUBCheck runner.
3. **Dependencies.** Each is asked for when it is added, with its licence, last release and alternatives:
   - the Kotlin Multiplatform plugin and kotlinx.serialization (both Apache-2.0);
   - pdfium-binaries for Windows and Linux (BSD-3 / Apache-2.0, as on Android);
   - EPUBCheck (BSD-3-Clause), for tests only.
4. **Downloading the corpus:** four OpenStax titles, CC BY 4.0, each licence confirmed before it is added.
5. **The conversion-time target**: 5 minutes or less for a 1,000-page textbook on the Fold.
