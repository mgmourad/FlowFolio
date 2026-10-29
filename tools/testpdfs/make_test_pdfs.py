"""Generates small, self-made PDFs that exercise the PDF engine's edge cases.

Standard library only, so there is no tool to install. Output is our own content (MIT) and is committed as
androidTest assets of :core:pdf. Damaged variants (truncated, bit-flipped) are derived at test time.

Usage:  python -I tools/testpdfs/make_test_pdfs.py android/core/pdf/src/androidTest/assets/pdfs
"""

from __future__ import annotations

import hashlib
import struct
import sys
from pathlib import Path

PAD = bytes.fromhex("28BF4E5E4E758A4164004E56FFFA01082E2E00B6D0683E802F0CA9FE6453697A")


def rc4(key: bytes, data: bytes) -> bytes:
    s = list(range(256))
    j = 0
    for i in range(256):
        j = (j + s[i] + key[i % len(key)]) % 256
        s[i], s[j] = s[j], s[i]
    out = bytearray()
    i = j = 0
    for byte in data:
        i = (i + 1) % 256
        j = (j + s[i]) % 256
        s[i], s[j] = s[j], s[i]
        out.append(byte ^ s[(s[i] + s[j]) % 256])
    return bytes(out)


class Rc4R2:
    """PDF Standard Security Handler, revision 2 (40-bit RC4), per ISO 32000-1 algorithms 2, 3 and 4."""

    def __init__(self, user_pw: str, owner_pw: str, file_id: bytes, permissions: int = -44) -> None:
        user = (user_pw.encode("latin-1") + PAD)[:32]
        owner = (owner_pw.encode("latin-1") + PAD)[:32]
        self.o = rc4(hashlib.md5(owner).digest()[:5], user)  # algorithm 3
        self.p = permissions
        digest = hashlib.md5(user + self.o + struct.pack("<i", permissions) + file_id).digest()  # algorithm 2
        self.key = digest[:5]
        self.u = rc4(self.key, PAD)  # algorithm 4

    def encrypt(self, num: int, gen: int, data: bytes) -> bytes:
        object_key = hashlib.md5(self.key + struct.pack("<I", num)[:3] + struct.pack("<I", gen)[:2]).digest()[:10]
        return rc4(object_key, data)

    def dictionary(self) -> str:
        return f"<< /Filter /Standard /V 1 /R 2 /O <{self.o.hex()}> /U <{self.u.hex()}> /P {self.p} >>"


class Pdf:
    """Builds a classic xref-table PDF from numbered objects."""

    def __init__(self) -> None:
        self.objects: dict[int, bytes] = {}
        self.streams: dict[int, tuple[str, bytes]] = {}

    def obj(self, num: int, body: str) -> None:
        self.objects[num] = body.encode("latin-1")

    def stream(self, num: int, data: bytes, extra: str = "") -> None:
        self.streams[num] = (extra, data)

    def build(
        self,
        root: int = 1,
        security: Rc4R2 | None = None,
        file_id: bytes | None = None,
        break_xref: bool = False,
        info: int | None = None,
    ) -> bytes:
        out = bytearray(b"%PDF-1.7\n%\xe2\xe3\xcf\xd3\n")
        offsets: dict[int, int] = {}
        nums = sorted(set(self.objects) | set(self.streams))
        encrypt_num = max(nums) + 1 if security else None
        for num in nums:
            offsets[num] = len(out)
            if num in self.objects:
                out += f"{num} 0 obj\n".encode() + self.objects[num] + b"\nendobj\n"
            else:
                extra, data = self.streams[num]
                if security:
                    data = security.encrypt(num, 0, data)
                out += (
                    f"{num} 0 obj\n<< /Length {len(data)} {extra}>>\nstream\n".encode()
                    + data
                    + b"\nendstream\nendobj\n"
                )
        if security and encrypt_num:
            offsets[encrypt_num] = len(out)
            out += f"{encrypt_num} 0 obj\n{security.dictionary()}\nendobj\n".encode()
        size = max(offsets) + 1
        xref = len(out)
        out += f"xref\n0 {size}\n0000000000 65535 f \n".encode()
        for num in range(1, size):
            offset = offsets.get(num)
            if offset is not None and break_xref:
                offset += 7  # every entry points into the middle of the previous object
            out += (f"{offset:010d} 00000 n \n" if offset is not None else "0000000000 65535 f \n").encode()
        trailer = f"/Size {size} /Root {root} 0 R"
        if info:
            trailer += f" /Info {info} 0 R"
        if file_id:
            trailer += f" /ID [<{file_id.hex()}> <{file_id.hex()}>]"
        if security:
            trailer += f" /Encrypt {encrypt_num} 0 R"
        out += f"trailer\n<< {trailer} >>\nstartxref\n{xref}\n%%EOF\n".encode()
        return bytes(out)


def text_page(pdf: Pdf, page_num: int, content_num: int, label: str, box: str = "0 0 612 792", rotate: int = 0) -> None:
    """A page with a big black label and a black bar, so tests can find ink at known places."""
    content = (f"0 0 0 rg 36 36 120 24 re f BT /F1 36 Tf 72 400 Td ({label}) Tj ET").encode()
    pdf.stream(content_num, content)
    rot = f" /Rotate {rotate}" if rotate else ""
    pdf.obj(
        page_num,
        f"<< /Type /Page /Parent 2 0 R /MediaBox [{box}]{rot} "
        f"/Resources << /Font << /F1 3 0 R >> >> /Contents {content_num} 0 R >>",
    )


def document(pages: list[tuple[str, str, int]], extra_catalog: str = "") -> Pdf:
    pdf = Pdf()
    page_nums = [10 + 2 * i for i in range(len(pages))]
    pdf.obj(1, f"<< /Type /Catalog /Pages 2 0 R {extra_catalog}>>")
    kids = " ".join(f"{n} 0 R" for n in page_nums)
    pdf.obj(2, f"<< /Type /Pages /Kids [{kids}] /Count {len(pages)} >>")
    pdf.obj(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    for i, (label, box, rotate) in enumerate(pages):
        text_page(pdf, page_nums[i], page_nums[i] + 1, label, box, rotate)
    return pdf


def pdf_text(s: str) -> str:
    """A PDF literal string's contents: backslashes and brackets escaped."""
    return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


class Sheet:
    """One page's content stream, written line by line: text at a place in a font, and filled boxes."""

    def __init__(self) -> None:
        self.ops: list[str] = []

    def text(self, x: float, y: float, font: str, size: float, s: str) -> None:
        self.ops.append(f"BT /{font} {size} Tf {x} {y} Td ({pdf_text(s)}) Tj ET")

    def runs(self, x: float, y: float, size: float, parts: list[tuple[str, str]]) -> None:
        """Pieces of one line in different fonts, one after another (Courier for inline code)."""
        body = " ".join(f"/{font} {size} Tf ({pdf_text(s)}) Tj" for font, s in parts)
        self.ops.append(f"BT {x} {y} Td {body} ET")

    def box(self, x: float, y: float, w: float, h: float, grey: float) -> None:
        self.ops.append(f"{grey} g {x} {y} {w} {h} re f 0 g")

    def data(self) -> bytes:
        return "\n".join(self.ops).encode("latin-1")


def technical_pdf() -> bytes:
    """
    A small technical book for the converter: a chapter label over its title, a running header beside the folio,
    nested bullet lists with lines hanging under their text, a numbered list, bullets set in a symbol font, inline
    code, an indented code listing placed by position, a shaded box of commands, a diagram drawn with characters, a
    listing that runs over a page turn, and a table's header row (which is not a heading).
    """
    fonts = {
        "F1": "/Times-Roman",
        "F2": "/Times-Bold",
        "F3": "/Courier",
        "F4": "/ZapfDingbats",
        "F5": "/Helvetica-Bold",
    }
    pages: list[Sheet] = [Sheet(), Sheet(), Sheet()]
    body, code, step, code_step = 11, 10, 14, 12
    for n, page in enumerate(pages):
        page.text(72, 750, "F1", 9, "Learning Python by Example")
        page.text(530, 750, "F1", 9, str(n + 1))

    p = pages[0]
    p.text(72, 690, "F5", 14, "Chapter 1")
    p.text(72, 664, "F5", 22, "Getting Started")
    y = 630
    for line in [
        "This short book shows how a converted page keeps its structure: lists keep their",
        "items and their depth, code keeps its lines and its indentation, and a diagram drawn",
        "with characters keeps its shape when the text is laid out again on a phone.",
    ]:
        p.text(72, y, "F1", body, line)
        y -= step
    y -= 12
    p.text(72, y, "F5", 15, "1.1 Lists and bullets")
    y -= 24
    items = [
        (0, ["Lists keep each item whole, even when an item runs over more than", "one line of the page."]),
        (1, ["A nested item sits further in."]),
        (1, ["So does this one, whose text also runs on to a second line that", "hangs under its first word."]),
        (0, ["Back at the first depth again."]),
    ]
    for depth, lines in items:
        x = 80 + depth * 24
        p.text(x, y, "F1", body, "\x95" if depth == 0 else "\x96")
        for line in lines:
            p.text(x + 12, y, "F1", body, line)
            y -= step
        y -= 3
    y -= 10
    for k, lines in enumerate(
        [
            ["Install Python from its website."],
            ["Open a terminal, then type the command", "shown below and press Enter."],
            ["Check the version it prints."],
        ],
        start=1,
    ):
        p.text(80, y, "F1", body, f"{k}.")
        for line in lines:
            p.text(94, y, "F1", body, line)
            y -= step
        y -= 3
    y -= 10
    for text in ["Symbols set in a dingbat font are bullets too.", "This one is a second such item."]:
        p.text(80, y, "F4", 8, "l")
        p.text(92, y, "F1", body, text)
        y -= step + 3

    p = pages[1]
    p.text(72, 700, "F5", 15, "1.2 Code")
    p.runs(
        72,
        676,
        body,
        [("F1", "Call "), ("F3", "print()"), ("F1", " to show text, and "), ("F3", "len(s)"), ("F1", " to count it.")],
    )
    y = 650
    listing = [(0, "def greet(name):"), (4, "if name:"), (8, 'print("Hello, " + name)'), (None, ""), (4, "return name")]
    for indent, line in listing:
        if indent is not None:
            p.text(80 + indent * 6, y, "F3", code, line)
        y -= code_step
    y -= 16
    p.box(72, y - 30, 300, 44, 0.92)
    p.text(80, y, "F3", code, "$ git add *.c")
    p.text(80, y - code_step, "F3", code, "$ git commit -m 'Initial version'")
    y -= 60
    p.text(72, y, "F1", body, "The program talks to a server through a cache:")
    y -= 22
    for line in [
        "+--------+      +--------+",
        "| client | ---> | server |",
        "+--------+      +--------+",
        "     |               |",
        "     v               v",
        "  [ cache ]      [ store ]",
    ]:
        p.text(80, y, "F3", code, line)
        y -= code_step
    y -= 16
    p.text(72, y, "F1", body, "A longer listing follows; it carries on over the page:")
    y = 120
    for line in ["for i in range(3):", "    total = total + i"]:
        p.text(80, y, "F3", code, line)
        y -= code_step

    p = pages[2]
    y = 720
    for line in ["    print(total)", "print('done')"]:
        p.text(80, y, "F3", code, line)
        y -= code_step
    y -= 20
    p.text(72, y, "F1", body, "Escape sequences in strings:")
    y -= 22
    for k, row in enumerate([("Escape", "Meaning", "Example"), ("\\n", "new line", "a\\nb"), ("\\t", "tab", "a\\tb")]):
        font = "F2" if k == 0 else "F1"
        for x, cell in zip((80, 200, 320), row):
            p.text(x, y, font, body, cell)
        y -= step
    y -= 30
    p.text(72, y, "F5", 14, "Chapter 2")
    p.text(72, y - 26, "F5", 22, "Going Further")
    p.text(72, y - 60, "F1", body, "The second chapter starts here, with its label above its title.")

    pdf = Pdf()
    page_nums = [20 + 2 * i for i in range(len(pages))]
    pdf.obj(1, "<< /Type /Catalog /Pages 2 0 R /Outlines 4 0 R >>")
    pdf.obj(2, f"<< /Type /Pages /Kids [{' '.join(f'{n} 0 R' for n in page_nums)}] /Count {len(pages)} >>")
    pdf.obj(4, "<< /Type /Outlines /First 5 0 R /Last 8 0 R /Count 2 >>")
    pdf.obj(
        5,
        f"<< /Title (Getting Started) /Parent 4 0 R /Next 8 0 R /First 6 0 R /Last 7 0 R /Count 2 "
        f"/Dest [{page_nums[0]} 0 R /Fit] >>",
    )
    pdf.obj(6, f"<< /Title (1.1 Lists and bullets) /Parent 5 0 R /Next 7 0 R /Dest [{page_nums[0]} 0 R /Fit] >>")
    pdf.obj(7, f"<< /Title (1.2 Code) /Parent 5 0 R /Prev 6 0 R /Dest [{page_nums[1]} 0 R /Fit] >>")
    pdf.obj(8, f"<< /Title (Going Further) /Parent 4 0 R /Prev 5 0 R /Dest [{page_nums[2]} 0 R /Fit] >>")
    pdf.obj(9, "<< /Title (Learning Python by Example) /Author (The FlowFolio Authors) >>")
    font_refs = []
    for k, (name, base) in enumerate(fonts.items()):
        num = 10 + k
        encoding = "" if base == "/ZapfDingbats" else " /Encoding /WinAnsiEncoding"
        pdf.obj(num, f"<< /Type /Font /Subtype /Type1 /BaseFont {base}{encoding} >>")
        font_refs.append(f"/{name} {num} 0 R")
    for page, num in zip(pages, page_nums):
        pdf.stream(num + 1, page.data())
        pdf.obj(
            num,
            f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
            f"/Resources << /Font << {' '.join(font_refs)} >> >> /Contents {num + 1} 0 R >>",
        )
    return pdf.build(info=9)


def generate(out_dir: Path) -> dict[str, int]:
    out_dir.mkdir(parents=True, exist_ok=True)
    files: dict[str, bytes] = {}
    letter = "0 0 612 792"

    # Labels i, ii, 1, 2, 3 and a two-entry outline (front matter, chapter 1).
    pdf = document(
        [(f"Page {i + 1}", letter, 0) for i in range(5)],
        "/Outlines 4 0 R /PageLabels << /Nums [0 << /S /r >> 2 << /S /D /St 1 >>] >> ",
    )
    pdf.obj(4, "<< /Type /Outlines /First 5 0 R /Last 6 0 R /Count 2 >>")
    pdf.obj(5, "<< /Title (Front matter) /Parent 4 0 R /Next 6 0 R /Dest [10 0 R /Fit] >>")
    pdf.obj(6, "<< /Title (Chapter 1) /Parent 4 0 R /Prev 5 0 R /Dest [14 0 R /Fit] >>")
    pdf.obj(7, "<< /Title (FlowFolio Test Book) /Author (The FlowFolio Authors) >>")
    files["labels-outline.pdf"] = pdf.build(info=7)

    files["rotated.pdf"] = document(
        [("R0", letter, 0), ("R90", letter, 90), ("R180", letter, 180), ("R270", letter, 270)]
    ).build()

    # Letter, A4 landscape, a 1-inch square, and the maximum PDF page size (200 x 200 inches).
    files["mixed-sizes.pdf"] = document(
        [("Letter", letter, 0), ("A4L", "0 0 842 595", 0), ("S", "0 0 72 72", 0), ("Huge", "0 0 14400 14400", 0)]
    ).build()

    empty = Pdf()
    empty.obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
    empty.obj(2, "<< /Type /Pages /Kids [] /Count 0 >>")
    files["zero-pages.pdf"] = empty.build()

    file_id = hashlib.md5(b"flowfolio-encrypted-test").digest()
    files["encrypted-rc4.pdf"] = document([("Secret 1", letter, 0), ("Secret 2", letter, 0)]).build(
        security=Rc4R2("flowfolio", "owner", file_id), file_id=file_id
    )

    files["broken-xref.pdf"] = document([("Repaired", letter, 0)]).build(break_xref=True)
    files["not-a-pdf.pdf"] = b"This is a plain text file with a .pdf name.\n" * 20
    files["technical.pdf"] = technical_pdf()

    for name, data in files.items():
        (out_dir / name).write_bytes(data)
    return {name: len(data) for name, data in files.items()}


if __name__ == "__main__":
    target = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfs")
    for name, size in generate(target).items():
        print(f"{size:7d}  {name}")
