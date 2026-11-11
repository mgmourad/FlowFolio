"""bench.py DATA LANG CONFIG: OCR every LANG-*.jpg in DATA with RapidOCR CONFIG, scored against the proofread text.

CONFIG is one of: en-v5, latin-v5, multi-v6s (English), ar-v5 (Arabic).
Prints per page: character error rate (CER), seconds, lines, mean confidence; then the totals, and writes
DATA/<page>.<config>.ocr.txt and a JSON summary.
"""
import json
import re
import statistics
import sys
import time
import unicodedata
from pathlib import Path

from rapidfuzz.distance import Levenshtein

data, lang, config = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
TESS = config.startswith("tess")
if TESS:
    import tesserocr
    tess = tesserocr.PyTessBaseAPI(path=str(Path(__file__).parent / "tessdata") + "/", lang="ara" if lang == "ar" else "eng")
else:
    from rapidocr import EngineType, LangDet, LangRec, ModelType, OCRVersion, RapidOCR

REC = {} if TESS else {
    "en-v5": dict(lang=LangRec.EN, ver=OCRVersion.PPOCRV5, kind=ModelType.MOBILE),
    "latin-v5": dict(lang=LangRec.LATIN, ver=OCRVersion.PPOCRV5, kind=ModelType.MOBILE),
    "multi-v6s": dict(lang=LangRec.EN, ver=OCRVersion.PPOCRV6, kind=ModelType.SMALL),
    "ar-v5": dict(lang=LangRec.ARABIC, ver=OCRVersion.PPOCRV5, kind=ModelType.MOBILE),
}[config]
DET = {} if TESS else dict(ver=OCRVersion.PPOCRV6, kind=ModelType.SMALL) if config == "multi-v6s" else dict(
    ver=OCRVersion.PPOCRV5, kind=ModelType.MOBILE)

engine = None if TESS else RapidOCR(params={
    "Global.use_cls": True, "Global.text_score": 0.0, "Global.min_height": 10,
    "Det.engine_type": EngineType.ONNXRUNTIME, "Det.lang_type": LangDet.MULTI if config != "multi-v6s" else LangDet.EN,
    "Det.ocr_version": DET["ver"], "Det.model_type": DET["kind"],
    "Rec.engine_type": EngineType.ONNXRUNTIME, "Rec.lang_type": REC["lang"],
    "Rec.ocr_version": REC["ver"], "Rec.model_type": REC["kind"],
})

ARABIC_MARKS = re.compile("[ً-ٰٟۖ-ۭـ]")  # harakat, superscript alef, tatweel


def norm(text, strict):
    t = unicodedata.normalize("NFKC", text)
    if lang == "ar":
        t = ARABIC_MARKS.sub("", t)  # the scans' vowel marks are often absent or faint; judge the letters
        if not strict:
            t = re.sub("[إأآٱ]", "ا", t).replace("ى", "ي").replace("ة", "ه")
    t = re.sub(r"[‘’“”\"'`«»]", "", t)
    if not strict:
        t = re.sub(r"[^\w]", "", t)  # letters and digits only: layout, punctuation and spacing aside
    return re.sub(r"\s+", " ", t).strip()


def reading_order(boxes, texts):
    """Lines top to bottom; boxes on the same line left to right, or right to left for Arabic."""
    items = []
    for box, text in zip(boxes, texts):
        ys = [p[1] for p in box]
        xs = [p[0] for p in box]
        items.append((min(ys), max(ys), min(xs), text))
    items.sort(key=lambda i: i[0])
    lines, current = [], []
    for item in items:
        if current and item[0] > statistics.mean([(c[0] + c[1]) / 2 for c in current]):
            lines.append(current)
            current = []
        current.append(item)
    if current:
        lines.append(current)
    rtl = lang == "ar"
    return "\n".join(" ".join(i[3] for i in sorted(line, key=lambda i: i[2], reverse=rtl)) for line in lines)


rows = []
for image in sorted(data.glob(f"{lang}-*.jpg")):
    truth = image.with_suffix(".txt").read_text(encoding="utf-8")
    start = time.perf_counter()
    if TESS:
        tess.SetImageFile(str(image))
        text = tess.GetUTF8Text()
        seconds = time.perf_counter() - start
        scores = [c / 100 for c in tess.AllWordConfidences()]
        texts = [line for line in text.splitlines() if line.strip()]
    else:
        result = engine(str(image))
        seconds = time.perf_counter() - start
        boxes = result.boxes if result.boxes is not None else []
        texts = list(result.txts or [])
        scores = list(result.scores or [])
        text = reading_order(boxes, texts) if texts else ""
    image.with_suffix(f".{config}.ocr.txt").write_text(text, encoding="utf-8")
    cer = Levenshtein.normalized_distance(norm(text, False), norm(truth, False))
    strict = Levenshtein.normalized_distance(norm(text, True), norm(truth, True))
    rows.append(dict(page=image.stem, cer=cer, strict=strict, seconds=seconds, lines=len(texts),
                     confidence=statistics.mean(scores) if scores else 0.0, low=sum(s < 0.8 for s in scores)))
    print(f"{image.stem}  CER {cer:6.2%}  strict {strict:6.2%}  {seconds:5.2f}s  {len(texts):3d} lines  "
          f"conf {rows[-1]['confidence']:.3f}", flush=True)

cers = [r["cer"] for r in rows]
summary = dict(config=config, pages=len(rows), median_cer=statistics.median(cers), mean_cer=statistics.mean(cers),
               median_strict=statistics.median([r["strict"] for r in rows]),
               median_seconds=statistics.median([r["seconds"] for r in rows]),
               rows=rows)
(data / f"summary-{lang}-{config}.json").write_text(json.dumps(summary, indent=1))
print(f"== {config}: {len(rows)} pages, median CER {summary['median_cer']:.2%}, mean {summary['mean_cer']:.2%}, "
      f"median strict {summary['median_strict']:.2%}, median {summary['median_seconds']:.2f}s/page")
