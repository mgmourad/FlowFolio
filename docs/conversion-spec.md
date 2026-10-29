# FlowFolio Conversion Output Contract

- **Status:** v1.2.0, normative (Phase 3, 2026-10-27; 1.1.0 2026-10-28: code listings, nested and numbered lists,
  inline code, chapter labels, tables without rules; 1.2.0 2026-10-28: bold and italics, items with their own glyph,
  tables of two columns). Written by `:core:conversion` (`EpubWriter`, `SidecarBuilder`)
  on the phone; read by the Phase 2 reader. The JSON Schema for the sidecar is `docs/schemas/flowfolio.schema.json`.
- **Owner:** converter + app (any change needs both sides reviewed).
- **Versioning:** the package and the sidecar carry `specVersion` (SemVer). Minor = additive, backward-compatible
  fields. Major = breaking change; the app must refuse or migrate unknown majors. The converter's own version
  (`recipe.engine.version`) changes whenever its output could, even within one spec version.

The converter turns one input PDF into one **output bundle**:

```
<book-id>/
  book.epub             EPUB 3, no EPUBCheck errors (checked on every corpus book, ConversionTest/CorpusTest)
  flowfolio.json        Sidecar (section 3)
```

`book-id` is the `sha256` of the source PDF's bytes, hex. It is stable across re-conversions of the same file, which is
what lets annotations migrate. Page images live inside `book.epub` (`OEBPS/images/`), not beside it.

What later phases add is marked *(Phase n)*; until then the element is not produced.

---

## 1. Identifiers and coordinates

| Concept | Rule |
|---|---|
| Page number (`page`) | 1-based **physical** index into the PDF. |
| Page label (`pageLabel`) | Printed page number from the PDF `/PageLabels` tree (e.g. `xiv`, `214`), else the folio found in the margins by Stage C; `null` if unknown. Used for "Go to page 214". |
| Bounding box (`bbox`) | `[x0, y0, x1, y1]` in **PDF points**, origin **top-left** of the page as displayed (y grows downward). Floats, 2 decimals. |
| Block ID (`blockId`) | `p{page:04d}-b{index:03d}`, in reading order per page, e.g. `p0214-b007`. Stable for a given `recipe`; may change when the recipe changes (migration uses text-quote anchors, section 5). |
| XHTML anchors | Every emitted block element carries `id="{blockId}"` and `data-ff-page="{page}"`. Page breaks carry `id="page-{page}"`. |

## 2. EPUB 3 requirements

1. **Package**: EPUB 3.3; `mimetype` stored first; `dc:identifier` = `urn:flowfolio:{book-id}`; `dc:title` from the
   PDF's metadata (else the file name), `dc:creator` when known; `<meta property="flowfolio:specVersion">`.
2. **Spine**: one XHTML file per chapter (`ch001.xhtml`...). Chapters come from the PDF outline's top level when
   present; otherwise from level-1 headings; otherwise fixed 20-page ranges (`health.summary.chaptersFrom` says which).
3. **Navigation** (`nav.xhtml`): `toc` (the outline's hierarchy, each entry linked to its heading, else its page, and
   never before the entry above it, so the list is in reading order); `page-list` (one entry per source page, titled
   with its `pageLabel`); `landmarks` (bodymatter; index and glossary *(Phase 5)*). Nothing links to `nav.xhtml` itself
   (it is not in the spine).
4. **Page breaks**: where source page N starts in reading order:
   `<span epub:type="pagebreak" role="doc-pagebreak" id="page-214" title="214" data-ff-page="214"/>`. Every page of the
   book has one, pages without text included; a paragraph continued over a page turn holds its break inline.
5. **Block elements**:

   | Source element | XHTML |
   |---|---|
   | Heading level n | `<h{n}>`; levels from the outline where a heading matches an entry, else one below the last outline heading. A chapter's label set above its title ("Chapter 5") is the heading's first line: `<h1>Chapter 5<br/>Conditionals</h1>` (1.1) |
   | Paragraph | `<p>`; a paragraph continued from the previous page stays one `<p>` (`continuesFrom` in the sidecar) |
   | List | `<ul>`, or `<ol>` for numbered and lettered items (`type="a\|A\|i\|I"`, `start` when it isn't 1); a deeper item's list sits inside the item before it. An item's lines that hang under its text stay in it; a change of marker kind, or numbering that starts again, starts a new list (1.1). An item marked with a symbol or emoji (✅, 🔹, ❶) keeps it as its own marker:
   `<li style="list-style-type: '✅ '">` (1.2) |
   | Superscript / subscript | `<sup>` / `<sub>` (text set smaller and raised or lowered from its line's baseline) |
   | Code within running text | `<code>`: text in a monospaced font inside a line of prose (1.1) |
   | Bold and italics within running text | `<strong>` / `<em>`: words set in a bold or italic face where the body text is not (1.2) |
   | Inline drawing (text or maths set as vector shapes) | `<img class="ff-inline" style="height:Hem;width:Wem;vertical-align:-Dem">`, sized in ems of the surrounding text so it scales with the reader's text |
   | Figure | `<figure class="ff-figure"><img/><figcaption/></figure>` (caption joined when it touches the figure) |
   | Table | `<figure class="ff-table ff-fallback"><img/></figure>`, an image of the table, ruled or not (rows of short cells whose starts line up, 1.1; two columns under a bold header
   row, a header row shaded instead of ruled, and a last row drawn without borders, 1.2). A table of plain ink has
   `<img class="ff-equation">` (drawn in the reader's text colour); one with colour of its own (coloured icons or
   cells, a shaded header row) has a plain `<img>` and keeps it (1.2) *(Phase 4: `<table>`)* |
   | Equation (display) | `<figure class="ff-equation ff-fallback"><img class="ff-equation"/></figure>`, an image with its number *(Phase 5: MathML)* |
   | Sidebar / callout | `<aside class="ff-sidebar">` |
   | Code | `<pre class="ff-code"><code>`: lines in a monospaced font (by the PDF's flags, the font's name, or glyphs that all advance alike). Each character at its column (its distance from the listing's left edge in character widths), so indentation and text drawn with characters keep their shape; blank lines from the gaps between rows. A listing over a page turn stays one `<pre>` with the page break inside it; a shaded box ends one (1.1) |
   | Low-coverage or scanned page | `<figure class="ff-fallback" data-ff-coverage="0.71"><img alt="{extracted text}"/></figure>` |
   | Footnotes, margin notes, cross-reference links | *(Phase 5)* |

6. **Images**: one render per page, cropped. On the phone: WebP, lossy (quality 85) for figures and page fallbacks,
   lossless for line art (equations, inline drawings, tables). The desktop development tool writes PNG. Both are core
   media types in EPUB 3.3.
7. **Line art in ink**: images with `class="ff-inline"` or `class="ff-equation"` are black ink on white paper. A reader
   may draw them in its own text colour with the paper left clear (the FlowFolio reader does), so they suit any paper.
8. **Styling**: one `book.css`; no absolute positioning; content must reflow.
9. **Accessibility**: `schema:accessMode` (textual, visual), `accessibilityFeature` (tableOfContents, printPageNumbers,
   readingOrder), `accessibilityHazard` none, and a summary; alt text for fallbacks from the page's extracted text.

## 3. Sidecar `flowfolio.json`

Normative schema: `docs/schemas/flowfolio.schema.json` (`CorpusTest` checks every corpus book's sidecar against it).
Example:

```jsonc
{
  "specVersion": "1.2.0",
  "bookId": "93652b69…",
  "complete": true,
  "source": { "fileName": "university-physics-volume-1.pdf", "sha256": "93652b69…", "pageCount": 959,
              "title": "University Physics Volume 1", "isbn": null },
  "pages": [
    { "page": 59, "pageLabel": null, "width": 612.0, "height": 792.0,
      "kind": "born-digital" /* | "scanned" | "empty" */, "xhtml": "ch005.xhtml#page-59" }
  ],
  "blocks": [
    { "blockId": "p0059-b001", "type": "equation", "page": 59, "bbox": [270.0, 269.0, 538.0, 292.0],
      "xhtml": "ch005.xhtml#p0059-b001", "confidence": 1.0, "method": "fallback:phase5", "continuesFrom": null }
  ],
  "graph": { "nodes": [], "edges": [] },
  "health": {
    "coverageThreshold": 0.98,
    "summary": { "pages": 959, "pagesBelowThreshold": 1, "fallbackPages": 1, "scannedPages": 0, "chapters": 29,
                 "chaptersFrom": "outline", "fallbackBlocks": 2690 },
    "pages": [ { "page": 59, "textCoverage": 1.0, "status": "ok", "drawingsAsImages": 23 } ]
  },
  "recipe": {
    "engine": { "name": "flowfolio-converter", "version": "1.2.0" },
    "components": { "pdfium": "chromium/8100" },
    "settings": { "coverageThreshold": "0.98" }
  }
}
```

### Block `type` values
`heading`, `paragraph`, `list`, `figure`, `caption`, `table`, `equation`, `sidebar`, `code`, `fallback`.
Running headers, footers and page numbers are removed from the text and not counted as the page's text
(section 4). *(Phase 5 adds `footnote`, `marginNote`, `glossaryEntry`, `indexEntry`.)*

### `method` values
Namespaced: `geometry+template` (laid out from the page's geometry and the book's styles), `geometry+columns` (code,
by column), `geometry+marker` (lists, by their markers), `fallback:phase5`
(equations and tables shown as images until their phase), `fallback:coverage` (a page under the threshold),
`fallback:scanned`. Later: `ml:<model>`, `ocr:<engine>`, `user-correction`.

### `graph`
Cross-references between blocks; empty until *(Phase 5)*.

## 4. Coverage and the "never ship silently" rule

- **Text coverage** (per page) = characters of the page's text found in its blocks ÷ the page's characters, with
  running headers, footers and page numbers left out. Text inside figures, tables and equations counts as found (they
  are images of the page this phase, with the text as alt text where it applies).
- A page is `ok` when its coverage is at least `coverageThreshold` (0.98); otherwise `degraded`, and the **whole page**
  goes out as one `fallback` image with its text as alt text. Scanned pages go out the same way (`scanned`) until
  *(Phase 9)* OCR. Nothing below the threshold is ever emitted as reflowed text alone.
- *(Phase 6)* the app shows a per-book health badge and marks degraded pages; *(Phase 4)* adds pixel coverage.

## 5. Annotation anchoring (app side, for reference)

Annotations store a W3C-style `TextQuoteSelector` (`exact`, `prefix`, `suffix`, ~32 chars each side)
plus `page`, `blockId`, and `bookId`. Re-conversion resolution order: `blockId` + quote match →
quote match within `page` ± 1 → fuzzy quote match book-wide → orphaned (shown to the user, never dropped).

## 6. Progressive output

During conversion the bundle may be partial. *(Phase 6)* exposes chapters as they are finished; the sidecar then
carries `"complete": false` and lists the finished spine items. A partial bundle must still be a valid EPUB containing
only finished chapters. Phase 3 writes complete bundles only (`complete: true`).

## Open questions

1. Single EPUB with progressive delivery vs. chapter fragments merged at the end (decide in Phase 6).
2. The coverage threshold (0.98) is reviewed once Phase 4 adds pixel coverage.
3. Vector figures as SVG instead of images (EPUBCheck and reader performance to decide, Phase 4).
