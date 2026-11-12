"""Fetch the scanned OCR corpus (stdlib only), as the PDF corpus is fetched.

Each line of `samples/golden/ocr/manifest.txt` is `id lang book page sha256`: the page's proofread scan from
`{lang}.wikisource.org`, fetched as its 1600px render into `samples/corpus/cache/ocr/{id}.jpg` and checked against
its sha256. The scans are NEVER committed (only the manifest, hashes and the proofread text); the ground-truth text
lives next to the manifest as `{id}.txt`.

    python -I tools/corpus/fetch_ocr_corpus.py [--only id,id] [--dry-run]
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "samples" / "golden" / "ocr" / "manifest.txt"
CACHE = ROOT / "samples" / "corpus" / "cache" / "ocr"
USER_AGENT = "FlowFolio-ocr-corpus/1.0 (+https://github.com/; local testing)"
CHUNK = 1 << 20


def get(lang: str, params: dict) -> dict:
    api = f"https://{lang}.wikisource.org/w/api.php"
    url = api + "?" + urllib.parse.urlencode({**params, "format": "json", "formatversion": "2"})
    return json.loads(fetch(url))


def fetch(url: str) -> bytes:
    """One polite request, backing off on 429 (Wikimedia's rate limit)."""
    for attempt in range(8):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=120) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise
            wait = int(e.headers.get("Retry-After") or 0) or 15 * (attempt + 1)
            print(f"  429, waiting {wait}s", flush=True)
            time.sleep(wait)
    raise SystemExit(f"failed: {url}")


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        while chunk := f.read(CHUNK):
            h.update(chunk)
    return h.hexdigest()


def entries(text: str) -> list[dict[str, str]]:
    out = []
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        if len(parts) != 5:
            raise SystemExit(f"bad manifest line: {line!r}")
        out.append(dict(zip(("id", "lang", "book", "page", "sha256"), parts)))
    return out


def thumb_url(entry: dict[str, str]) -> str:
    info = get(
        entry["lang"],
        {
            "action": "query",
            "titles": "File:" + entry["book"],
            "prop": "imageinfo",
            "iiprop": "url",
            "iiurlparam": f"page{entry['page']}-1600px",
        },
    )
    ii = info["query"]["pages"][0].get("imageinfo")
    if not ii or "thumburl" not in ii[0]:
        raise SystemExit(f"no image for {entry['id']}")
    return ii[0]["thumburl"]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--only", help="comma-separated ids to fetch")
    parser.add_argument("--dry-run", action="store_true", help="list what would be fetched")
    args = parser.parse_args()
    only = set(args.only.split(",")) if args.only else None

    failed = 0
    for entry in entries(MANIFEST.read_text(encoding="utf-8")):
        if only and entry["id"] not in only:
            continue
        dest = CACHE / f"{entry['id']}.jpg"
        if dest.exists() and sha256(dest) == entry["sha256"]:
            print(f"{entry['id']}: ok")
            continue
        if args.dry_run:
            print(f"{entry['id']}: would fetch")
            continue
        print(f"{entry['id']}: fetching", flush=True)
        try:
            url = thumb_url(entry)
            data = fetch(url)
        except OSError as e:
            print(f"{entry['id']}: FAILED {e}")
            failed += 1
            continue
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(data)
        got = sha256(dest)
        if got != entry["sha256"]:
            print(f"{entry['id']}: HASH MISMATCH, expected {entry['sha256']}, got {got}")
            failed += 1
        else:
            print(f"{entry['id']}: ok ({len(data) / 1e6:.1f} MB)")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
