# Scanned-books corpus ground truth

The proofread text of the scanned corpus used by `OcrCorpusTest` (Phase 7 M3), one `{id}.txt` per page. Each page is a
public-domain scan from Wikisource's `Page:` namespace at quality 3–4 (proofread or validated), so the text is a
human-checked reference against the scan. `manifest.txt` lists each page's id, language, source book and page, and the
scan's sha256; the scans themselves are fetched by `tools/corpus/fetch_ocr_corpus.py` into `samples/corpus/cache/ocr/`
(never committed), as the PDF corpus is.

- 21 English pages (en-00..en-20): clean scans, read by PaddleOCR.
- 20 Arabic pages (ar-00..ar-19): 9 manuscripts (ar-00, ar-06, ar-09..ar-13, ar-17, ar-18) that must stay pictures,
  one English abstract (ar-19), and 10 printed pages — 3 modern (ar-02, ar-15, ar-16) that read well and 7 older
  metal type that mostly stay pictures (ADR-0054).

The gate (`OcrCorpusTest`) measures, per page, word recall and CER against this text, the share of pages kept as
text, and that the manuscripts and a blurred page stay pictures. The baselines are the constants in `OcrCorpusTest`;
English 98.9% recall / 3.0% CER / 20 of 21 kept, Arabic printed 93.0% recall / 16.0% CER / 4 of 10 kept.
