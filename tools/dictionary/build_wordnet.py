"""Build FlowFolio's compact WordNet database from the Princeton WordNet 3.1 database files.

WordNet 3.1 is distributed under the WordNet License (permissive, BSD-style). This script reads the
`data.*` and `index.*` files and writes a SQLite database with one table:

    entries(lemma, pos, definition, synonyms, hypernyms)

* lemma      — the headword (underscores already turned into spaces).
* pos        — noun / verb / adjective / adverb.
* definition — the synset's gloss.
* synonyms   — the other words in the same synset, `;`-separated.
* hypernyms  — the first word of each `@` (hypernym) synset, `;`-separated.

Run (stdlib only): python -I tools/dictionary/build_wordnet.py <dict-dir> <out.db>
"""

import os
import sqlite3
import sys

POS = {"n": "noun", "v": "verb", "a": "adjective", "s": "adjective", "r": "adverb"}


def parse_data(path):
    """Return offset -> (words, gloss, hypernym_offsets)."""
    out = {}
    with open(path, "r", encoding="latin-1") as f:
        for line in f:
            line = line.rstrip("\n")
            if len(line) < 8 or not line[:8].isdigit():
                continue  # license header and blanks
            fields, _, gloss = line.partition(" | ")
            parts = fields.split(" ")
            try:
                w_cnt = int(parts[3], 16)
            except (ValueError, IndexError):
                continue
            words = []
            i = 4
            for _ in range(w_cnt):
                if i >= len(parts):
                    break
                words.append(parts[i].replace("_", " "))
                i += 2
            hypernyms = []
            if i < len(parts):
                p_cnt = int(parts[i], 10)
                i += 1
                for _ in range(p_cnt):
                    if i + 1 >= len(parts):
                        break
                    if parts[i] == "@":  # @ = hypernym pointer
                        hypernyms.append(parts[i + 1])
                    i += 4
            out[parts[0]] = (words, gloss.strip(), hypernyms)
    return out


def parse_index(path):
    """Return lemma -> [synset offsets]."""
    out = {}
    with open(path, "r", encoding="latin-1") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line[0] == " ":
                continue  # license header
            parts = line.split(" ")
            if len(parts) < 4 or not parts[0].isalpha():
                continue
            try:
                synset_cnt = int(parts[2], 10)
                p_cnt = int(parts[3], 10)
            except (ValueError, IndexError):
                continue
            offsets_start = 6 + p_cnt  # lemma, pos, synset_cnt, p_cnt, ptrs…, sense_cnt, tagsense_cnt
            offsets = parts[offsets_start : offsets_start + synset_cnt]
            out[parts[0].replace("_", " ")] = offsets
    return out


def main():
    dict_dir, out_path = sys.argv[1], sys.argv[2]
    data = {}
    index = {}
    for code, fn in (("n", "data.noun"), ("v", "data.verb"), ("a", "data.adj"), ("r", "data.adv")):
        data[code] = parse_data(os.path.join(dict_dir, fn))
    for code, fn in (("n", "index.noun"), ("v", "index.verb"), ("a", "index.adj"), ("r", "index.adv")):
        index[code] = parse_index(os.path.join(dict_dir, fn))

    rows = []
    for code, lemmas in index.items():
        d = data[code]
        for lemma, offsets in lemmas.items():
            for off in offsets:
                syn = d.get(off)
                if not syn:
                    continue
                words, gloss, hypers = syn
                synonyms = ";".join(w for w in words if w.lower() != lemma.lower())
                hypernyms = ";".join(d[h][0][0] for h in hypers if h in d and d[h][0])
                rows.append((lemma, POS[code], gloss, synonyms, hypernyms))

    conn = sqlite3.connect(out_path)
    conn.execute("DROP TABLE IF EXISTS entries")
    conn.execute("CREATE TABLE entries (lemma TEXT, pos TEXT, definition TEXT, synonyms TEXT, hypernyms TEXT)")
    conn.execute("CREATE INDEX idx_lemma ON entries (lemma)")
    conn.executemany("INSERT INTO entries VALUES (?,?,?,?,?)", rows)
    conn.commit()
    conn.close()
    print(f"wrote {len(rows)} entries to {out_path}")


if __name__ == "__main__":
    main()
