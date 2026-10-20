# ADR-0022: Build the riffle scrubber as its own module, drawn in one Canvas over a page that never resizes

- **Status:** Accepted (product owner, 2026-10-20: plan approved with the recommended answers)
- **Date:** 2026-10-20
- **Deciders:** product owner, lead engineer
- **Phase:** 2 (milestone M2b)

## Context

The owner's spec (`docs/design/riffle-scrubber-spec.md`, mock `docs/design/FlowFolio_Riffle_Scrubber.html`) replaces
the reader's scrubber bar with a "riffle scrubber": a front view of the book's fore edge with one leaf per sheet,
spring physics, a draggable ribbon, bookmark flags, a loupe of page previews, and a multi-sheet landing turn. It must
work for PDFs and EPUBs, folded and unfolded, at 120 Hz, inside the memory budget (ADR-0013).

Facts that shape the design:
- The mock assumes spread `s` shows pages `2s` and `2s + 1`. The reader also has single pages (cover screen), spreads
  with the cover on its own or not, odd page counts and right-to-left books.
- The page cache (ADR-0013) holds full-size images of the pages near the reader only. Riffling passes hundreds.
- PDF rendering is one PDFium thread, shared by pages, zoom tiles and anything new.
- Hundreds of leaves move every frame; per-leaf animation objects would allocate and stutter.
- The reader already draws edge to edge, with its bars as overlays, so the page size does not depend on the bars.

## Options considered

1. **Extend the existing scrubber in `:feature:reader`.** Least code, but the scrubber's physics, drawing and gestures
   would mix with the reader's, and could not be tested or demonstrated on their own.
2. **A separate module behind small interfaces, with a demo screen on fake data, then wired into the reader.** Testable
   in isolation; the reader supplies its geometry, bookmarks and thumbnails through adapters.
3. **A View-based custom widget.** No benefit over a Compose `Canvas` here, and the rest of the reader is Compose.

## Decision

Option 2.

- **Module `:feature:reader:riffle`** (Android library with Compose). The scrubber depends on three interfaces only:
  - `BookGeometry`: page count, the reader's units (pages or spreads) and the pages each shows, sections from the
    contents, printed page labels, reading direction;
  - `RiffleFlags`: the bookmarks as flags (page, colour, optional note);
  - `PageThumbnailProvider`: low-resolution page images, cached and prefetched.
- **Positions map to the reader's units, not the mock's spreads.** Each unit gets a position on the fore edge measured
  in sheets: `(firstPage + lastPage + 1) / 4`. A spread opens exactly between its two sheets, a single page sits within
  its sheet, and the order always matches the book. The strip finds the unit nearest the finger by binary search. For
  right-to-left books the strip is mirrored: the front cover is on the right.
- **Leaves are a picture of the sheets** (`ceil(pages / 2)`), thinned to one per N sheets when there are more than
  `stripWidthPx / 1.6`; thinning never changes the mapping.
- **Drawing:** one `Canvas`; leaves batched into 18 shade buckets of reused `Path`s; positions and velocities in
  preallocated `FloatArray`s stepped in a `withFrameNanos` loop with the real frame time (two substeps). The loop runs
  only while something moves, and the ribbon's idle sway settles after a few seconds, so a still strip costs nothing.
- **Logic as small pure-Kotlin state machines**, tested on the JVM: the position mapping, `ScrubSpeed` and the fine-scrub
  controller, flag stacking and tap tracking, `ChromeTimer` (auto-hide), `RiffleState`, the landing plan and the
  haptic decisions.
- **The page never resizes:** the strip, top bar and loupe are overlays. EPUB pages get top and bottom margins that
  clear them; PDF pages keep their full size and the overlays cover them while shown (owner decision 1). A test checks
  the reader's content size with the chrome shown and hidden.
- **Thumbnails** have their own cache of 16-bit images at loupe size (about 16 MB at most), rendered at low priority
  so page renders always come first. The page cache is not used for them.
- **Landing:** with Curl, up to six fast flat flips from thumbnails, then the final page curl from the last
  intermediate page to the target, which gives the target's full-size render time to finish. Slide and Scroll land
  with a slide or a scroll (owner decision 4). Reduced motion lands with a crossfade.
- **The loupe** is a translucent surface whose blur is not recomputed every frame: an animated blur leaves textures in
  the GPU cache (CLAUDE.md issue 21).

## Consequences

- Positive:
  - The scrubber can be built, tested and shown before it touches the reader.
  - The mapping is exact for every layout, including cover-alone spreads and right to left.
  - No allocation and no work while still.
- Negative / accepted risks:
  - Thumbnails add up to about 16 MB while riffling, inside a budget EPUBs already exceed (measured in M3).
  - On the cover screen, a long book in single pages gives less than 1 dp per page; fine scrubbing (10 times slower),
    the nudge arrows and Go to page cover single-page precision.
  - EPUB pages lose some text height to the new margins.
- Follow-ups:
  - **M2b R2 (lead engineer):** screenshot tests (ADR-0024 chooses the tool).
  - **M2b R5:** macrobenchmark and memory numbers against the spec's targets.
