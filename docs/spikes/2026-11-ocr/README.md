# Spike: OCR engine for scanned books (Phase 7 M1), 2026-11-11

**Question:** which OCR engine reads scanned English and Arabic book pages well enough to reflow them, runs on the
phone and in CI, and can tell when a page should stay a picture? This feeds ADR-0054.

## Method

- **Pages:** 20 English and 20 Arabic scanned pages from Wikisource's `Page:` namespace, each **proofread** (quality
  3–4) by volunteers against its scan. That makes the transcription a trustworthy reference. One page per book, so
  40 different books. Fetched by `fetch_wikisource.py`; sources are listed in `pages.txt`.
  - 9 of the Arabic pages are **manuscripts** (handwriting) and one is an English abstract. They are excluded from
    the print scores (`excluded-pages.txt`) and kept for the fallback test.
  - That leaves **10 printed Arabic pages**: 3 modern (1950s on), 7 older metal type (1880s–1930s). This is a small
    sample. M3's corpus will be larger.
- **Engines,** run on this Mac (Apple Silicon, CPU only):
  - RapidOCR 3.10.0 (Apache-2.0), which runs PaddleOCR's models on ONNX Runtime:
    - English: PP-OCRv5 `en` mobile, PP-OCRv5 `latin` mobile, and PP-OCRv6 multilingual **small**;
    - Arabic: PP-OCRv5 `arabic` mobile (PP-OCRv6 has no Arabic);
    - detection by PP-OCRv5 mobile, or PP-OCRv6 small for the v6 run.
  - Tesseract 5.5.3 through `tesserocr` 2.12.0, with `tessdata_best` `eng` and `ara`.
- **Scores** (`bench.py`, `rescore.py`):
  - **Word recall** is the headline: the share of the reference's words the OCR got exactly right.
  - Character error rate (CER) is shown too, but Wikisource's references leave out running heads, title blocks and
    dotted leaders, and one is a magazine page with only one article transcribed. CER counts all of that as error,
    so it overstates the real error. Line-end hyphens are joined first, as the converter would.
  - Arabic is compared without vowel marks and with alef, ya and ta-marbuta forms unified, because the references
    and the scans disagree on them.

## Results

| | English, median word recall (worst page) | Arabic printed, median word recall | s/page (Mac) | Pack size |
|---|---|---|---|---|
| **PP-OCRv6 small** (RapidOCR) | **98.7%** (86%) | (no Arabic model) | 0.54 | ~30 MB |
| PP-OCRv5 en/latin mobile | 97.2% (71%) | | 0.40 | ~12 MB |
| **PP-OCRv5 arabic mobile** | | 16% (modern: 74–91%) | 0.24 | ~12 MB |
| Tesseract 5.5.3 `best` | 98.3% (32%) | 53% (modern: 77–94%) | 0.60–1.12 | 15+ MB per language |

Per Arabic page, PP-OCRv5 / Tesseract recall was:
- modern print: ar-02 91% / 94%, ar-15 81% / 77%, ar-16 74% / 94%;
- older print: ar-08 57% / 75%; ar-01, 03, 04, 05, 07 and 14 all 1–19% / 13–55%.

**Confidence as the "stay a picture" gate:** this matters most for the done-when.
- With RapidOCR, keeping a page only when its mean line confidence is **≥ 0.85**:
  - kept all 20 pages that read at ≥ 90% recall;
  - dropped every weak printed page and **every manuscript** (their confidence is 0.69 at most);
  - kept one 86% English page and lost no good page.
- Tesseract's confidence is not a usable gate. It scored a manuscript 0.95, and raising its bar dropped good
  pages.

## Findings

1. **English is solved by PP-OCRv6 small**. Its median and worst-case recall are the best of the four (Tesseract's
   worst page was 32%), and it is twice as fast as Tesseract.
2. **Arabic works on modern print and fails on old type**, for every engine tried. PP-OCRv5's Arabic model is a
   small mobile model trained on modern fonts. On books printed before about 1950 it reads fragments. Tesseract
   does better there but still far from usable, and its confidence cannot be trusted to hide its mistakes.
3. **RapidOCR's confidence reliably separates good pages from bad.** With a 0.85 gate, old Arabic pages and
   manuscripts stay pictures rather than showing invented text, which is the phase's done-when.
4. **The app's bundled ONNX Runtime can run the models.** sherpa-onnx ships ORT **1.28.2**, and the models use opsets
   11–14 (IR 10). A second copy of ORT is not needed (and two cannot coexist).
5. **Arabic needs bidi reordering.** The Arabic model returns each line in *visual* order. RapidOCR fixes that with
   `python-bidi`, which is LGPL-3.0 and so not allowed. Android's `android.icu.text.Bidi` and the JDK's
   `java.text.Bidi` (for CI) do the same job under permissive licences.
6. **Where the models are hosted:** RapidOCR downloads them from modelscope.cn. FlowFolio should mirror the
   files it uses as GitHub release assets with pinned SHA-256s, as the voice packs are.

## The owner's decision (2026-11-11)

- **English:** PaddleOCR.
- **Arabic:** both engines. Choosing whichever reports the higher confidence was simulated on these outputs and
  rejected: it chose the weaker text on 3 of the 4 usable pages, and showed a handwritten page as text (Tesseract was
  95% sure of it).
- Instead, the two engines' **word agreement** is the gate. It was 49–86% on every usable page and at most 11% on
  every bad or handwritten one. When the page passes, Tesseract's text is used. See ADR-0054.

## Not measured yet

- **Speed on the Fold.** The Mac numbers are about 2–4× faster than a phone. That would put a page at about 1–2 s on
  the Fold, within the plan's 2 s bar, but M2 measures it in `:convert`.
- **A 3% CER bar can't be checked against Wikisource references** (finding 1's caveat). M3's own golden pages, typed
  to match exactly what the page shows, can.
- **Arabic on the books the owner actually reads.** If they are modern print, PP-OCRv5 Arabic is promising. If
  classical or older editions matter, that needs a further spike on a stronger Arabic recogniser.
