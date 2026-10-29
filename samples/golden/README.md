# Golden metrics and hand labels

For each corpus PDF (`samples/corpus/manifest.yaml` id), checked by `CorpusTest` in `android/tools/convert`:

- `baselines/<id>.json`: the committed metric baseline (`Metrics.kt`): text coverage (mean, and the share of pages at
  or above 0.98), fallback pages, chapters and where they came from, outline entries matched by a heading, block
  counts by type, EPUBCheck errors and warnings, hand-labelled heading and reading-order scores, seconds per page and
  EPUB size (the last two recorded, never compared: they depend on the machine and the image encoder).
- `labels/<id>.json`: 30 pages across the corpus, labelled by eye from page renders (2026-10-27): each page's headings
  with their levels, and the opening words of its blocks in reading order (index pages give the two- and three-column
  cases). A few words per page, not the books' content.
- `<id>/output/`: the local conversion (`book.epub`, `flowfolio.json`), gitignored.

The run fails when a metric gets worse than its baseline beyond the tolerance in `CorpusTest` (coverage 0.005, outline
matching 0.01, labelled scores none, fallback pages 2 or 0.5%, chapters and EPUBCheck warnings none, EPUBCheck errors
always zero). After a deliberate change, record new baselines and commit them with it:

    .\gradlew.bat :tools:convert:test --tests "*CorpusTest*" "-Pflowfolio.recordBaselines"   (from android/)
