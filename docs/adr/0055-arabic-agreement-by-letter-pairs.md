# ADR-0055: Measure the Arabic engines' agreement by letter pairs, not whole words

- **Status:** Accepted (owner, 2026-11-12, with the Phase 7 sign-off). Supersedes the agreement *measure* in ADR-0054. ADR-0054's design stands:
  Tesseract reads, PaddleOCR checks, and Tesseract's text is kept.
- **Date:** 2026-11-12
- **Deciders:** product owner, lead engineer
- **Phase:** 7 (M4)

## Context

ADR-0054 keeps a scanned Arabic page as text only when Tesseract and PaddleOCR's Arabic model agree on at least 40% of
their words. The owner reported that none of his Arabic books worked in 0.7.0-beta.1.

We measured his 1960s Egyptian book page by page (`OcrGate`, `tessdata_best`):
- **Tesseract reads its pages well:** page 12 comes out close to the printed text.
- **PaddleOCR garbles a letter in most words:** "سافرت إ اكويت ة اية…" where the page says "وسافرت إلى الكويت للمرة
  الثانية".
- **So word agreement measured the checker, not the page.** It came out at 0.17 to 0.57, and 5 of 11 body pages that
  Tesseract had read well stayed pictures. On a page-by-page basis a book became a patchwork of text and pictures.

The M3 corpus never showed this. Its printed Arabic was mostly pre-1950 metal type, rejected either way, and it had no
mid-century book print, which is the kind of Arabic scan readers will most often own.

## Options considered

1. **Lower the word bar.** No bar between 0.21 and 0.34 separates the corpus's misread pages from the book's good ones
   with any margin. Rejected.
2. **Trust Tesseract's own confidence.** ADR-0054 already rejected this: Tesseract was 95% sure of a manuscript it
   misread. On the corpus a misread page (ar-03, recall 0.27) still scored 0.84. Rejected.
3. **Compare letter pairs (chosen).** Take the Dice overlap of the two readings' letter pairs (bigrams within words,
   counted as multisets) after the same Arabic normalisation as before. A word that one engine got nearly right still
   shares most of its pairs, while two engines reading unrelated nonsense share few.

## Decision

Option 3, with the bar at **0.66** (`OcrGate.MIN_AGREEMENT`). Measured on 2026-11-12:

| Pages | Tesseract word recall | Word agreement | Letter-pair agreement |
|---|---|---|---|
| Corpus, read well (ar-02, 08, 15, 16) | 0.74–0.94 | 0.63–0.92 | 0.87–0.97 |
| Corpus, misread (all others, manuscripts included) | 0.00–0.49 | 0.00–0.21 | 0.00–0.62 |
| The owner's book, 11 body pages | not proofread | 0.17–0.57 | 0.55–0.86 |

- **On the corpus,** 0.66 keeps exactly the 4 pages ADR-0054 kept and rejects every misread page.
- **On the owner's book,** it keeps 10 of 11 body pages, where word agreement kept 6.

Also in this change:
- PaddleOCR now checks Tesseract on the lines already detected, rather than detecting the page a second time.
- The M3 gate's CI wiring is fixed so it actually runs (see Consequences).

## Consequences

- **Arabic books like the owner's mostly become text.** Pages still flip to pictures where PaddleOCR is lost:
  headings in display type, and page 10 of his book (0.55).
- **The margin is thin at the top of the rejected group.** The highest misread page (0.62) and the lowest kept page of
  the book (0.67) are close, so the bar should be re-measured whenever either engine or model changes, and the corpus
  should gain mid-century book print with proofread text (M3's corpus list asked for it).
- **CI's Arabic gate had never run.** CI passed the OCR paths relative to the wrong folder, so `OcrCorpusTest`
  skipped all three tests while the job stayed green, and the gate was measuring Ubuntu's smaller Arabic model rather
  than `tessdata_best`. Both are fixed: absolute paths, a test that fails on a missing folder, and the phone's own
  models from FlowFolio's mirror, checked against their pinned hashes.
