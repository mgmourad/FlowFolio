"""fetch_wikisource.py LANG OUT N: N proofread scan pages from LANG.wikisource.org, each as a PNG and its text.

Pages in the Page: namespace (104) at quality 3 (proofread) or 4 (validated) are scans whose text volunteers have
checked against the image, so they serve as ground truth. Mixes pages across many books (one or two per index).
"""
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

lang, out, want = sys.argv[1], Path(sys.argv[2]), int(sys.argv[3])
out.mkdir(parents=True, exist_ok=True)
API = f"https://{lang}.wikisource.org/w/api.php"
UA = {"User-Agent": "FlowFolio-OCR-spike/0.1 (research; contact via github.com/mgmourad/FlowFolio)"}


def fetch(url):
    """One polite request: 2 s apart, backing off on 429 (Wikimedia's rate limit) for up to ten minutes."""
    for attempt in range(8):
        time.sleep(2)
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise
            wait = int(e.headers.get("Retry-After") or 0) or 15 * (attempt + 1)
            print(f"  429, waiting {wait}s", flush=True)
            time.sleep(wait)
    raise SystemExit(f"failed: {url}")


def get(params):
    url = API + "?" + urllib.parse.urlencode({**params, "format": "json", "formatversion": "2"})
    return json.loads(fetch(url))


def clean(wikitext):
    t = re.sub(r"<noinclude>.*?</noinclude>", "", wikitext, flags=re.S)
    t = re.sub(r"<ref[^>]*>.*?</ref>|<ref[^/]*/>", "", t, flags=re.S)
    for _ in range(3):
        t = re.sub(r"\{\{[^{}]*\}\}", lambda m: template(m.group(0)), t)
    t = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", t)
    t = re.sub(r"<[^>]+>", "", t)
    t = re.sub(r"'''?", "", t)
    t = re.sub(r"^[=:*#;]+|[=]+$", "", t, flags=re.M)
    return re.sub(r"[ \t]+", " ", t).strip()


def template(t):
    """Keep the visible text of simple formatting templates ({{center|x}}, {{sc|x}}); drop the rest."""
    body = t[2:-2]
    parts = body.split("|")
    name = parts[0].strip().lower()
    keep = {"center", "c", "sc", "smaller", "larger", "small", "big", "x-larger", "right", "bold", "italic", "i",
            "fs", "uc", "lc", "hws", "hwe", "nop", "font", "يمين", "وسط"}
    if name in {"hws", "hwe"} and len(parts) > 2:
        return parts[2]
    if name in keep and len(parts) > 1:
        return parts[-1]
    return ""


seen_index = {}
got = 0
for src in sorted(out.glob(f"{lang}-*.src")):  # resume
    book = re.match(r"^[^:]+:(.+?)/(\d+)$", src.read_text(encoding="utf-8")).group(1)
    seen_index[book] = 1
    got += 1
cont = {}
while got < want:
    q = get({"action": "query", "generator": "allpages", "gapnamespace": 104, "gaplimit": 200,
             "prop": "proofread", **cont})
    pages = q.get("query", {}).get("pages", [])
    for p in pages:
        if got >= want:
            break
        quality = p.get("proofread", {}).get("quality", 0)
        title = p["title"]
        m = re.match(r"^[^:]+:(.+?)/(\d+)$", title)
        if quality < 3 or not m:
            continue
        book, n = m.group(1), int(m.group(2))
        if seen_index.get(book, 0) >= 1 or not book.lower().endswith((".djvu", ".pdf")):
            continue
        rev = get({"action": "query", "titles": title, "prop": "revisions", "rvprop": "content", "rvslots": "main"})
        text = clean(rev["query"]["pages"][0]["revisions"][0]["slots"]["main"]["content"])
        if len(text) < 600:
            continue
        info = get({"action": "query", "titles": "File:" + book, "prop": "imageinfo", "iiprop": "url",
                    "iiurlparam": f"page{n}-1600px"})
        ii = info["query"]["pages"][0].get("imageinfo")
        if not ii or "thumburl" not in ii[0]:
            continue
        try:
            img = fetch(ii[0]["thumburl"])
        except urllib.error.HTTPError:
            continue
        name = f"{lang}-{got:02d}"
        (out / f"{name}.jpg").write_bytes(img)
        (out / f"{name}.txt").write_text(text, encoding="utf-8")
        (out / f"{name}.src").write_text(title, encoding="utf-8")
        seen_index[book] = seen_index.get(book, 0) + 1
        got += 1
        print(name, title[:80], len(text), flush=True)
    if "continue" not in q:
        break
    cont = q["continue"]
print("got", got)
