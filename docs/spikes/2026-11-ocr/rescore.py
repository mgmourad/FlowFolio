"""rescore.py DATA LANG CONFIG...: word recall and CER again from the saved OCR, fairly: line-end hyphens joined,
dotted leaders dropped; word recall = share of the proofread text's words found (as a multiset) in the OCR."""
import collections, json, re, statistics, sys, unicodedata
from pathlib import Path
from rapidfuzz.distance import Levenshtein
data, lang, configs = Path(sys.argv[1]), sys.argv[2], sys.argv[3:]
MARKS = re.compile("[ً-ٰٟۖ-ۭـ]")
HAND = set((data / "handwritten.txt").read_text().split()) if (data / "handwritten.txt").exists() else set()
def norm(t):
    t = unicodedata.normalize("NFKC", t)
    t = re.sub(r"(\w)-\s*\n\s*(\w)", r"\1\2", t)          # line-end hyphens
    t = re.sub(r"(\w)- (\w)", r"\1\2", t)
    t = re.sub(r"\.{3,}|(?:\. ){3,}|_{3,}", " ", t)         # dotted leaders, rules
    if lang == "ar":
        t = MARKS.sub("", t)
        t = re.sub("[إأآٱ]", "ا", t).replace("ى", "ي").replace("ة", "ه")
    return t
def words(t):
    return re.findall(r"\w+", norm(t).lower())
for c in configs:
    rows = []
    for img in sorted(data.glob(f"{lang}-*.jpg")):
        if img.stem in HAND: continue
        truth, ocr = words(img.with_suffix(".txt").read_text()), words(img.with_suffix(f".{c}.ocr.txt").read_text())
        have = collections.Counter(ocr)
        hit = sum(min(n, have[w]) for w, n in collections.Counter(truth).items())
        cer = Levenshtein.normalized_distance("".join(ocr), "".join(truth))
        rows.append((img.stem, hit / max(1, len(truth)), cer))
    rec = [r[1] for r in rows]
    print(f"{c:10s} {len(rows):2d} pages  word recall median {statistics.median(rec):.1%} mean {statistics.mean(rec):.1%}  "
          f"(worst {min(rec):.0%})  CER median {statistics.median([r[2] for r in rows]):.1%}")
    json.dump(rows, open(data / f"rescore-{lang}-{c}.json", "w"))
