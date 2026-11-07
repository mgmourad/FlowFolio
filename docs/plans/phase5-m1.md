# M1 — Progressive Delivery: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (native, this session) to implement this
> plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chapters of a converting PDF become readable in Dynamic Flow as they finish, instead of only when the whole
book is done.

**Architecture:** The converter already knows every chapter after `analyse`; only image rendering (the slow ~52%) and
the EPUB write remain. Render images chapter-by-chapter, and after each chapter rewrite the EPUB with the chapters
finished so far (`complete: false` + `finishedSpineItems` in the sidecar). The service writes each partial atomically to
`book.epub.part` and broadcasts "N of M chapters readable"; the reader opens the partial once the first chapter lands
and reopens to the full book on completion. Complete output stays byte-identical (no engine bump, no re-conversion).

**Tech Stack:** Kotlin (plain JVM `:core:conversion`), Android foreground service, kotlinx.serialization, EPUB 3 ZIP.

**Spec:** `docs/plans/phase5-design.md` (design), `docs/conversion-spec.md` §6, `docs/schemas/flowfolio.schema.json`.

## Global Constraints

- Complete conversion output is byte-identical to today: `finishedSpineItems` is omitted when `complete: true`, so the
  sidecar has no new bytes and the EPUB is unchanged. **Do not bump `SidecarBuilder.ENGINE_VERSION`.** The CI corpus
  gate and baselines must stay green.
- A partial bundle must be a valid EPUB (EPUBCheck-clean) containing only finished chapters (spec §6).
- On-device only; no new dependencies.
- macOS arm64 cannot run `:tools:convert:test` (Linux-only libpdfium) — use `:core:conversion:test` locally and say so.
- detekt/ktlint: complex functions need `@Suppress("CyclomaticComplexMethod", "NestedBlockDepth")`; spotless before commit.
- Delivery rule: milestone work is verified locally (`spotlessCheck detekt lintDebug testDebugUnitTest`,
  `:app:assembleDebug` on `fold8_api37`), CI gates run only at phase boundaries.

## Review Focus

1. **A chapter that spans a page turn** (a paragraph/listing `continuesFrom`): its images still render and its XHTML is
   self-contained per chapter. Pin in Task 3's emit-order test (chapters partition pages in order; no page in two chapters).
2. **A book with one chapter** (`total == 1`): the single emit is `complete: true`; the reader never sees a partial. Pin in
   Task 3 (`emit` count == chapter count, first emit complete when N==1).
3. **The reader opens `.part` while it is being rewritten**: atomic rename means the reader holds a consistent old inode.
   Pin in Task 6 (open the partial file, then rename a new one over it; the open archive still parses).
4. **`bundle()` vs `readableBundle()`**: library/shelf "ready" checks must stay on the *complete* bundle, or a converting
   book would look finished. Pin in Task 4 (a partial exists but `ready()`/`bundle()` is still false/null).
5. **`finishedSpineItems` omitted on complete**: the schema is `additionalProperties: false`, so a complete sidecar that
   serialized the field would fail the corpus gate. Pin in Task 1 (complete sidecar JSON has no `finishedSpineItems` key).

---

### Task 1: Sidecar gains `finishedSpineItems` and a `complete` parameter

**Files:**
- Modify: `android/core/conversion/src/main/kotlin/com/flowfolio/core/conversion/epub/Sidecar.kt`
- Modify: `android/core/conversion/src/main/kotlin/com/flowfolio/core/conversion/SidecarBuilder.kt`
- Test: `android/core/conversion/src/test/kotlin/com/flowfolio/core/conversion/SidecarBuilderTest.kt` (new)

**Interfaces:**
- Produces: `Sidecar.finishedSpineItems: List<String>?` (null default); `SidecarBuilder.build(doc, source, threshold,
  complete: Boolean = true, finishedSpineItems: List<String>? = null): Sidecar`.

- [ ] **Step 1: Write the failing test** — `SidecarBuilderTest`:

```kotlin
class SidecarBuilderTest {
    // A two-chapter document, built by hand like StagesTest (Linking.apply to cut chapters).
    @Test fun completeOmitsFinishedSpineItems() {
        val doc = twoChapterDoc()
        val json = JSON.encodeToString(SidecarBuilder.build(doc, source(doc), 0.98f))
        assertFalse("complete sidecar has no finishedSpineItems", json.contains("finishedSpineItems"))
        assertTrue(json.contains("\"complete\":true") || json.contains("\"complete\": true"))
    }
    @Test fun partialListsFinishedSpineItems() {
        val doc = twoChapterDoc()
        val s = SidecarBuilder.build(doc, source(doc), 0.98f, complete = false, finishedSpineItems = listOf("ch001.xhtml"))
        assertEquals(false, s.complete)
        assertEquals(listOf("ch001.xhtml"), s.finishedSpineItems)
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew :core:conversion:test --tests "*SidecarBuilderTest*"`
  Expected: compile failure (`finishedSpineItems` unresolved; `build` has no such parameter).

- [ ] **Step 3: Implement** — add `val finishedSpineItems: List<String>? = null` to `Sidecar` (after `complete`); add the
  two parameters to `SidecarBuilder.build` and pass them through to the `Sidecar(...)` call (use named args so the
  positional construction stays clear).

- [ ] **Step 4: Run to verify it passes** — `./gradlew :core:conversion:test --tests "*SidecarBuilderTest*"` Expected: PASS.

- [ ] **Step 5: Commit** — `git add` the three files; `git commit -m "feat: sidecar records finished spine items"`.

### Task 2: EpubWriter writes a chapter subset

**Files:**
- Modify: `android/core/conversion/src/main/kotlin/com/flowfolio/core/conversion/epub/EpubWriter.kt`
- Test: `android/core/conversion/src/test/kotlin/com/flowfolio/core/conversion/epub/EpubWriterTest.kt` (new)

**Interfaces:**
- Consumes: `Document`, `Chapter` (ir), `Linking.file(n)` for chapter file names.
- Produces: `EpubWriter.write(out: OutputStream, images: Map<String, ByteArray>, chapters: List<Chapter> = doc.chapters)`.
  Thread `chapters` through `opf`, `nav`, `tocList`, `target`, `hrefAt`; the `pages` map stays the full `doc.pages`.

- [ ] **Step 1: Write the failing test** — `EpubWriterTest`: build a `Document` with 3 chapters (hand-built blocks, then
  `Linking.apply` or set `doc.chapters` directly), write with `chapters.subList(0, 1)` to a `ByteArrayOutputStream`,
  open the ZIP, and assert: exactly one `ch0*.xhtml` entry; `content.opf` has one spine `<itemref>` and one chapter
  manifest item; the page-list (`nav.xhtml`) lists only that chapter's pages; the full write (`doc.chapters`) has three.
  Assert the full write is byte-identical when called with the default argument.

- [ ] **Step 2: Run to verify it fails** — `./gradlew :core:conversion:test --tests "*EpubWriterTest*"`
  Expected: compile failure (`write` has no `chapters` parameter).

- [ ] **Step 3: Implement** — add the `chapters` parameter and thread it; `write`'s chapter loop, `opf`'s manifest+spine,
  `nav`'s `chapterOfPage`/landmarks, and `tocList`'s fallback all use the passed `chapters`; `target`/`hrefAt` take
  `chapters` so outline entries pointing into unfinished chapters are dropped.

- [ ] **Step 4: Run to verify it passes** — `./gradlew :core:conversion:test --tests "*EpubWriterTest*"` Expected: PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat: epub writer emits a chapter subset"`.

### Task 3: Converter renders chapter-by-chapter and emits progressively

**Files:**
- Modify: `android/core/conversion/src/main/kotlin/com/flowfolio/core/conversion/Converter.kt`
- Test: `android/core/conversion/src/test/kotlin/com/flowfolio/core/conversion/ConverterProgressiveTest.kt` (new, with a
  small in-memory `PdfSource` fake producing two born-digital pages and `renderImages` returning empty crops)

**Interfaces:**
- Consumes: `PdfSource`, `SourceInfo`, `EpubWriter.write(out, images, chapters)`, `SidecarBuilder.build(..., complete, finishedSpineItems)`.
- Produces: `Converter.convertProgressively(pdf, source, modified, progress = {}, emit: (epub: ByteArray, sidecar: Sidecar,
  finished: Int, total: Int) -> Unit): Result`; private `renderChapter(pdf, doc, chapter, out: MutableMap<String, ByteArray>,
  onPage: (Int) -> Unit)` shared by `convert` and `convertProgressively`.

- [ ] **Step 1: Write the failing test** — `ConverterProgressiveTest`:
  - `emitsOneBundlePerChapterInOrder`: collect `emit` calls; assert `finished` goes `1..total`, `total == doc.chapters.size`.
  - `partialIsAValidEpubOfFinishedChapters`: for the first emit, open the ZIP and assert only `ch001.xhtml` is present and
    the spine has one item; the sidecar has `complete == false` and `finishedSpineItems == ["ch001.xhtml"]`.
  - `finalEmitMatchesConvert`: the last emit's `epub` bytes equal `Converter().convert(...)` written to a buffer, and its
    sidecar equals the complete sidecar (no `finishedSpineItems`).

- [ ] **Step 2: Run to verify it fails** — `./gradlew :core:conversion:test --tests "*ConverterProgressiveTest*"`
  Expected: compile failure (`convertProgressively` unresolved).

- [ ] **Step 3: Implement** — extract the per-page crop/render body of `images()` into `renderChapter`; rewrite `images()`
  to loop `doc.chapters` + `renderChapter`; add `convertProgressively` per the design (emit after each chapter, then
  `progress(Progress("writing", 1, 1))` at the end); leave `convert` streaming and unchanged.

- [ ] **Step 4: Run to verify it passes** — `./gradlew :core:conversion:test` Expected: PASS (all converter tests).

- [ ] **Step 5: Commit** — `git commit -m "feat: converter emits chapters progressively"`.

### Task 4: Protocol, status and the readable bundle

**Files:**
- Modify: `android/core/model/src/main/kotlin/com/flowfolio/core/model/DynamicFlow.kt` (`FlowStatus.Converting` gains
  `readableChapters: Int = 0, totalChapters: Int = 0`)
- Modify: `android/core/data/src/main/kotlin/com/flowfolio/core/data/flow/FlowProtocol.kt` (extras + overload)
- Modify: `android/core/data/src/main/kotlin/com/flowfolio/core/data/flow/DynamicFlow.kt` (receiver + `readableBundle()`)
- Modify: `android/core/data/src/main/kotlin/com/flowfolio/core/data/ReaderRepository.kt` (`openFlow`, `epubChapterTitles`)

**Interfaces:**
- Produces: `FlowProtocol.progress(pkg, bookId, fraction, pause, readableChapters, totalChapters): Intent`;
  `EXTRA_CHAPTERS = "chapters"`, `EXTRA_TOTAL_CHAPTERS = "totalChapters"`;
  `DynamicFlow.readableBundle(bookId: String): File?` (complete epub when ready, else the `.part` if it exists).

- [ ] **Step 1: Write the failing test** — extend `DefaultDynamicFlow` tests if present (else a small `FlowProtocolTest`
  asserting the new extras round-trip on an `Intent`, and `FlowStatusTest` for equality/copy). Assert `readableBundle`
  returns the partial when `book.epub` is absent but `book.epub.part` exists, and null when neither exists.

- [ ] **Step 2: Run to verify it fails** — `./gradlew :core:data:testDebugUnitTest` (or the module's test task) Expected: compile failure.

- [ ] **Step 3: Implement** — add the fields/extras; map `ACTION_PROGRESS` → `Converting(fraction, pause, readable, total)`;
  add `readableBundle()` to the interface + `None` + `DefaultDynamicFlow`; switch `ReaderRepository.openFlow` and
  `epubChapterTitles` to `readableBundle`.

- [ ] **Step 4: Run to verify it passes** — `./gradlew :core:data:testDebugUnitTest` Expected: PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat: expose readable chapter count and partial bundle"`.

### Task 5: FlowService writes partials atomically and reports chapters

**Files:**
- Modify: `android/core/data/src/main/kotlin/com/flowfolio/core/data/flow/FlowService.kt`

**Interfaces:**
- Consumes: `Converter.convertProgressively`, `FlowProtocol.progress(..., readableChapters, totalChapters)`, `FlowFiles.partial/sidecar/epub`.
- Produces: an atomic write helper (`writeAtomically(target: File, bytes: ByteArray)` via a sibling `.tmp` + `renameTo`);
  `Progress.chapter(finished, total)` broadcasting the chapter counts with the current fraction.

- [ ] **Step 1: Write the failing test** — extend `FlowServiceOnDeviceTest` (androidTest, emulator) to assert the progress
  broadcasts include `EXTRA_CHAPTERS`/`EXTRA_TOTAL_CHAPTERS` and that the partial `.part` exists mid-run and is renamed to
  `book.epub` on completion. (This is an on-device test; the JVM part is covered by Tasks 1–3.)

- [ ] **Step 2: Run to verify it fails** — on `fold8_api37`: `./gradlew :core:data:connectedDebugAndroidTest` (or the
  `am instrument` form in CLAUDE.md) Expected: FAIL (extras not sent / partial never present).

- [ ] **Step 3: Implement** — switch `convert` to `convertProgressively`; in `emit` write the partial and sidecar
  atomically and call `progress.chapter(finished, total)`; keep the final `.part → book.epub` rename and the complete
  sidecar write.

- [ ] **Step 4: Run to verify it passes** — on `fold8_api37`, the same test Expected: PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat: flow service delivers chapters progressively"`.

### Task 6: Reader opens the partial and follows completion

**Files:**
- Modify: `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/ReaderFlow.kt`
- Modify: `android/feature/reader/src/main/kotlin/com/flowfolio/feature/reader/DynamicFlowSwitch.kt` (`statusLine` shows
  "chapter N of M" when `readableChapters > 0`)

**Interfaces:**
- Consumes: `DynamicFlow.readableBundle`, `FlowStatus.Converting(readableChapters, totalChapters)`.
- Produces: `ReaderFlow.opensFlow`/`switch`/`watch` treat a readable partial as "can open flow": `opensFlow` returns
  `wants && readableBundle != null`; `watch` reopens on `Converting(readableChapters > 0)` **or** `Ready`; `switch`'s
  "later" uses `readableBundle == null`.

- [ ] **Step 1: Write the failing test** — `ReaderFlowTest` (new, with a fake `DynamicFlow`/`Host`): `watch` reopens when
  status becomes `Converting(readableChapters = 1)`; `opensFlow` returns true when `readableBundle` is non-null even while
  `bundle` is null.

- [ ] **Step 2: Run to verify it fails** — `./gradlew :feature:reader:testDebugUnitTest --tests "*ReaderFlowTest*"` Expected: FAIL.

- [ ] **Step 3: Implement** — the three `ReaderFlow` changes and the `statusLine`/`ringOf` copy for "chapter N of M".

- [ ] **Step 4: Run to verify it passes** — `./gradlew :feature:reader:testDebugUnitTest` Expected: PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat: reader opens chapters as they finish"`.

### Task 7: Schema, strings, docs

**Files:**
- Modify: `docs/schemas/flowfolio.schema.json` (add optional `finishedSpineItems` to the top-level `properties`)
- Modify: `android/feature/reader/src/main/res/values/strings.xml` (a `flow_status_chapters` string, e.g.
  "Chapter %1$d of %2$d ready")
- Modify: `docs/plans/phase5-design.md` (mark M1 status as built)

- [ ] **Step 1: Implement** — schema (optional `"finishedSpineItems": { "type": "array", "items": { "type": "string" } }`
  with `"nullable"`-free JSON Schema — omit the key entirely when complete, so no null handling is needed); string; doc note.

- [ ] **Step 2: Verify** — `./gradlew :core:conversion:test` and a manual `SchemaCheck` smoke (the corpus gate's
  `SchemaCheck` accepts the optional property).

- [ ] **Step 3: Commit** — `git commit -m "docs: schema and strings for progressive delivery"`.

### Task 8: Local verification gate

- [ ] **Step 1:** `./gradlew spotlessApply`
- [ ] **Step 2:** `./gradlew :core:conversion:test` (converter JVM tests; `:tools:convert:test` cannot run on macOS arm64 — known issue 37)
- [ ] **Step 3:** `./gradlew spotlessCheck detekt lintDebug testDebugUnitTest -x :tools:convert:test`
- [ ] **Step 4:** `./gradlew :app:assembleDebug`; install on `fold8_api37`, convert a multi-chapter PDF, and confirm: the
  first chapter is readable in Dynamic Flow before conversion finishes; "chapter N of M" shows; the full book opens on completion.
- [ ] **Step 5:** Commit any fixes; note in the hand-back that CI (corpus gate) has **not** run for this milestone work.
