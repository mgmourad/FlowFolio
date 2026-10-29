"""Download the golden corpus listed in samples/corpus/manifest.yaml (stdlib only).

Each `books:` entry is fetched into samples/corpus/cache/ (PDFs at the top, EPUBs under epub-books/) and checked
against its sha256. An entry with an empty sha256 is downloaded and its hash written back into the manifest, so the
first run pins every file. Files already present with the right hash are left alone.

    python -I tools/corpus/fetch_corpus.py [--only id,id] [--dry-run]
"""

from __future__ import annotations

import argparse
import hashlib
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "samples" / "corpus" / "manifest.yaml"
CACHE = ROOT / "samples" / "corpus" / "cache"
# Entries are one flow mapping per line: `  - { id: x, kind: pdf, ..., url: "...", sha256: "..." }`.
ENTRY = re.compile(r"^\s*-\s*\{(?P<body>.*)\}\s*$")
FIELD = re.compile(r'(\w+):\s*("(?:[^"]*)"|\[[^\]]*\]|[^,]+)')
USER_AGENT = "FlowFolio-corpus-fetch/1.0 (+https://github.com/; local testing)"
CHUNK = 1 << 20


def entries(text: str) -> list[dict[str, str]]:
    """The `books:` entries of the manifest, in order."""
    out: list[dict[str, str]] = []
    in_books = False
    for line in text.splitlines():
        if re.match(r"^\w", line):
            in_books = line.startswith("books:")
            continue
        m = ENTRY.match(line) if in_books else None
        if m:
            fields = {k: v.strip().strip('"') for k, v in FIELD.findall(m.group("body"))}
            out.append(fields)
    return out


def target(entry: dict[str, str]) -> Path:
    if entry["kind"] == "epub":
        return CACHE / "epub-books" / f"{entry['id']}.epub"
    return CACHE / f"{entry['id']}.pdf"


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        while chunk := f.read(CHUNK):
            h.update(chunk)
    return h.hexdigest()


def download(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    part = dest.with_suffix(dest.suffix + ".part")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response, part.open("wb") as f:
        while chunk := response.read(CHUNK):
            f.write(chunk)
    part.replace(dest)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--only", help="comma-separated ids to fetch")
    parser.add_argument("--dry-run", action="store_true", help="list what would be fetched")
    args = parser.parse_args()
    only = set(args.only.split(",")) if args.only else None

    text = MANIFEST.read_text(encoding="utf-8")
    failed = 0
    for entry in entries(text):
        if only and entry["id"] not in only:
            continue
        url, want, dest = entry["url"], entry.get("sha256", ""), target(entry)
        if not url.startswith("https://"):
            print(f"{entry['id']}: skipped, not an https URL")
            continue
        if dest.exists() and want and sha256(dest) == want:
            print(f"{entry['id']}: ok")
            continue
        if args.dry_run:
            print(f"{entry['id']}: would fetch {url}")
            continue
        print(f"{entry['id']}: fetching {url}", flush=True)
        try:
            download(url, dest)
        except OSError as e:
            print(f"{entry['id']}: FAILED {e}")
            failed += 1
            continue
        got = sha256(dest)
        if want and got != want:
            print(f"{entry['id']}: HASH MISMATCH, expected {want}, got {got} (the publisher may have updated it)")
            failed += 1
        elif not want:
            # Pin the hash in the manifest line of this entry.
            text = re.sub(
                rf'(\{{\s*id:\s*{re.escape(entry["id"])},.*?sha256:\s*)""',
                rf'\g<1>"{got}"',
                text,
                count=1,
            )
            MANIFEST.write_text(text, encoding="utf-8", newline="\n")
            print(f"{entry['id']}: pinned {got} ({dest.stat().st_size / 1e6:.1f} MB)")
        else:
            print(f"{entry['id']}: ok ({dest.stat().st_size / 1e6:.1f} MB)")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
