# FlowFolio Conversion Output Contract

- **Status:** DRAFT v0.1 (Phase 0). Becomes normative at the end of Phase 3.
- **Owner:** converter + app (any change needs both sides reviewed; see ADR-0002).
- **Versioning:** the sidecar carries `specVersion` (SemVer). Minor = additive, backward-compatible
  fields. Major = breaking change; the app must refuse or migrate unknown majors.

The converter turns one input PDF into one **output bundle**:

```
<book-id>/
  book.epub             EPUB 3, validates with EPUBCheck (no errors; warnings listed in health report)
  flowfolio.json        Sidecar (this document, section 3)
  pages/                Optional: original page renders for fallback blocks and block crops (WebP)
```

`book-id` is a content hash of the source PDF: `sha256` of the file bytes, hex, first 32 chars.
It is stable across re-conversions of the same file, which is what lets annotations migrate.

---

## 1. Identifiers and coordinates

| Concept | Rule |
|---|---|
| Page number (`page`) | 1-based **physical** index into the PDF. |
| Page label (`pageLabel`) | Printed page number from the PDF `/PageLabels` tree (e.g. `xiv`, `214`), or inferred from the detected page-number artifact; `null` if unknown. Used for "go to page 214". |
| Bounding box (`bbox`) | `[x0, y0, x1, y1]` in **PDF user-space points**, origin **top-left** of the page's CropBox (y grows downward), after applying `/Rotate`. Floats, 2 decimals. |
| Block ID (`blockId`) | `p{page:04d}-b{index:03d}` assigned in reading order per page, e.g. `p0214-b007`. Stable for a given `recipe`; may change if the recipe changes (migration uses text-quote anchors, section 5). |
| XHTML anchors | Every emitted block element carries `id="{blockId}"`. Page breaks carry `id="page-{pageLabel or page}"`. |

## 2. EPUB 3 requirements

1. **Package**: EPUB 3.3, `dc:identifier` = `urn:flowfolio:{book-id}`, `dc:source` = original PDF title/ISBN when known.
   `<meta property="flowfolio:specVersion">`.
2. **Spine**: one XHTML file per chapter. Chapters come from the PDF outline (bookmarks) when present;
   otherwise from inferred top-level headings; otherwise fixed page ranges (fallback, flagged in health report).
3. **Navigation** (`nav.xhtml`): `toc` (full outline hierarchy), `page-list` (one entry per source page, using
   `pageLabel`), `landmarks` (cover, toc, bodymatter, index, glossary when found).
4. **Page breaks**: at the reading-order position where source page N starts:
   `<span epub:type="pagebreak" role="doc-pagebreak" id="page-214" title="214" data-ff-page="214"/>`.
5. **Block elements** (each with `id`, and `data-ff-page` for the source page):

   | Source element | XHTML |
   |---|---|
   | Heading level n | `<h{n}>` (levels from the book design system, Stage C) |
   | Paragraph | `<p>` |
   | List | `<ul>/<ol>/<li>` |
   | Figure | `<figure><img or svg/><figcaption/></figure>`; vector diagrams as inline or referenced SVG with labels as `<text>` |
   | Table | `<table>` with `<caption>`, `<thead>`, `rowspan`/`colspan` for merged cells; stitched tables emitted once |
   | Equation (display) | `<math display="block">` MathML; LaTeX in `<annotation encoding="application/x-tex">` |
   | Equation (inline) | `<math>` MathML with the same annotation |
   | Sidebar / callout | `<aside epub:type="sidebar" role="doc-…">` (`doc-tip`, `doc-example`, etc. when classified) |
   | Footnote | `<aside epub:type="footnote" role="doc-footnote">` + `<a epub:type="noteref" role="doc-noteref">` |
   | Margin note | `<aside epub:type="marginalia" class="ff-margin">` anchored after the nearest body block |
   | Low-coverage fallback | `<figure class="ff-fallback" data-ff-coverage="0.71"><img src="pages/p0214.webp" alt="{extracted text}"/></figure>` |

6. **Styling**: one `book.css` with the inferred palette as CSS custom properties and font-faces for
   embeddable fonts (ADR-0003 rule 4). No absolute positioning; content must reflow.
7. **Accessibility**: `schema:accessMode`, `accessibilityFeature` (MathML, tableOfContents, printPageNumbers), alt text from figure captions or extracted labels.

## 3. Sidecar `flowfolio.json`

Top-level shape (sketch; the normative JSON Schema is generated from Pydantic models in Phase 3 and
committed at `docs/schemas/flowfolio.schema.json`):

```jsonc
{
  "specVersion": "0.1.0",
  "bookId": "3f2a…",
  "source": { "fileName": "…", "sha256": "…", "pageCount": 912, "title": "…", "isbn": null },
  "pages": [
    { "page": 214, "pageLabel": "214", "width": 612.0, "height": 792.0,
      "kind": "born-digital" /* | "scanned" | "mixed" */, "xhtml": "ch07.xhtml#page-214" }
  ],
  "blocks": [
    { "blockId": "p0214-b007", "type": "paragraph", "page": 214, "bbox": [72.0, 310.5, 540.0, 388.2],
      "xhtml": "ch07.xhtml#p0214-b007", "confidence": 0.97,
      "method": "geometry+template", "textHash": "sha1:…", "continuesFrom": "p0213-b011" }
  ],
  "graph": {
    "nodes": [ { "id": "fig-3.2", "kind": "figure", "label": "Figure 3.2", "blockId": "p0081-b004" } ],
    "edges": [ { "from": "p0090-b002", "to": "fig-3.2", "kind": "references" } ]
  },
  "health": {
    "coverageThreshold": 0.98,
    "summary": { "pages": 912, "pagesBelowThreshold": 3, "equationsVerified": 0.93 },
    "pages": [ { "page": 214, "textCoverage": 0.995, "pixelCoverage": 0.982, "status": "ok",
                 "selfHealed": [ { "blockId": "p0214-b009", "reason": "alignment-gap", "chars": 41 } ],
                 "unresolved": [] } ]
  },
  "recipe": {
    "engine": { "name": "flowfolio-converter", "version": "0.1.0" },
    "components": { "pypdfium2": "x.y", "docling": "x.y" },
    "settings": { "coverageThreshold": 0.98 },
    "corrections": [ { "rule": "classify", "match": { "styleId": "s12" }, "as": "sidebar" } ]
  }
}
```

### Block `type` enum (initial)
`heading`, `paragraph`, `list`, `listItem`, `figure`, `caption`, `table`, `equation`, `sidebar`,
`footnote`, `marginNote`, `code`, `quote`, `glossaryEntry`, `indexEntry`, `fallback`.
Artifacts that are removed (running headers/footers, page numbers) are **recorded** with
`type: "artifact"` and no `xhtml`, so coverage accounting stays honest.

### `method` values
Free-form but namespaced, e.g. `tagged-pdf`, `geometry+template`, `ml:docling`, `ocr:<engine>`,
`healed:alignment`, `healed:pixel-diff`, `user-correction`.

## 4. Coverage and the "never ship silently" rule

- **Text coverage** (per page) = aligned characters of source text found in output ÷ source characters,
  excluding blocks recorded as `artifact`. Computed by sequence alignment (Stage G).
- **Pixel coverage** (per page) = 1 − (area of unexplained diff blobs ÷ inked area) from render-back diff.
- A page is `ok` if both are ≥ `coverageThreshold` (default 0.98, configurable); otherwise `degraded`.
- A `degraded` page is **never** emitted as reflow-only: its unexplained regions (or the whole page) are
  emitted as `fallback` blocks with the original render, and the health report lists it. The app shows a
  per-book health badge and marks such pages.

## 5. Annotation anchoring (app side, for reference)

Annotations store a W3C-style `TextQuoteSelector` (`exact`, `prefix`, `suffix`, ~32 chars each side)
plus `page`, `blockId`, and `bookId`. Re-conversion resolution order: `blockId` + quote match →
quote match within `page` ± 1 → fuzzy quote match book-wide → orphaned (shown to the user, never dropped).

## 6. Progressive output

During conversion the bundle may be partial. The job API (Phase 6) exposes chapters as they are
finished; the sidecar then carries `"complete": false` and lists the finished spine items. A partial
bundle must still be a valid EPUB containing only finished chapters.

## Open questions

1. Single EPUB with progressive delivery vs. chapter fragments merged at the end (decide in Phase 6).
2. Coverage threshold default (0.98) is a placeholder until Phase 4 baselines exist.
3. SVG for vector figures: inline vs. separate files (EPUBCheck and reader performance to decide).
