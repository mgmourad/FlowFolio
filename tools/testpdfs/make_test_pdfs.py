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

    def build(self, root: int = 1, security: Rc4R2 | None = None, file_id: bytes | None = None,
              break_xref: bool = False, info: int | None = None) -> bytes:
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
                out += f"{num} 0 obj\n<< /Length {len(data)} {extra}>>\nstream\n".encode() + data + b"\nendstream\nendobj\n"
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


def text_page(pdf: Pdf, page_num: int, content_num: int, label: str, box: str = "0 0 612 792",
              rotate: int = 0) -> None:
    """A page with a big black label and a black bar, so tests can find ink at known places."""
    content = (f"0 0 0 rg 36 36 120 24 re f BT /F1 36 Tf 72 400 Td ({label}) Tj ET").encode()
    pdf.stream(content_num, content)
    rot = f" /Rotate {rotate}" if rotate else ""
    pdf.obj(page_num, f"<< /Type /Page /Parent 2 0 R /MediaBox [{box}]{rot} "
                      f"/Resources << /Font << /F1 3 0 R >> >> /Contents {content_num} 0 R >>")


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


def generate(out_dir: Path) -> dict[str, int]:
    out_dir.mkdir(parents=True, exist_ok=True)
    files: dict[str, bytes] = {}
    letter = "0 0 612 792"

    # Labels i, ii, 1, 2, 3 and a two-entry outline (front matter, chapter 1).
    pdf = document([(f"Page {i + 1}", letter, 0) for i in range(5)],
                   "/Outlines 4 0 R /PageLabels << /Nums [0 << /S /r >> 2 << /S /D /St 1 >>] >> ")
    pdf.obj(4, "<< /Type /Outlines /First 5 0 R /Last 6 0 R /Count 2 >>")
    pdf.obj(5, "<< /Title (Front matter) /Parent 4 0 R /Next 6 0 R /Dest [10 0 R /Fit] >>")
    pdf.obj(6, "<< /Title (Chapter 1) /Parent 4 0 R /Prev 5 0 R /Dest [14 0 R /Fit] >>")
    pdf.obj(7, "<< /Title (FlowFolio Test Book) /Author (The FlowFolio Authors) >>")
    files["labels-outline.pdf"] = pdf.build(info=7)

    files["rotated.pdf"] = document([("R0", letter, 0), ("R90", letter, 90), ("R180", letter, 180),
                                     ("R270", letter, 270)]).build()

    # Letter, A4 landscape, a 1-inch square, and the maximum PDF page size (200 x 200 inches).
    files["mixed-sizes.pdf"] = document([("Letter", letter, 0), ("A4L", "0 0 842 595", 0), ("S", "0 0 72 72", 0),
                                         ("Huge", "0 0 14400 14400", 0)]).build()

    empty = Pdf()
    empty.obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
    empty.obj(2, "<< /Type /Pages /Kids [] /Count 0 >>")
    files["zero-pages.pdf"] = empty.build()

    file_id = hashlib.md5(b"flowfolio-encrypted-test").digest()
    files["encrypted-rc4.pdf"] = document([("Secret 1", letter, 0), ("Secret 2", letter, 0)]).build(
        security=Rc4R2("flowfolio", "owner", file_id), file_id=file_id)

    files["broken-xref.pdf"] = document([("Repaired", letter, 0)]).build(break_xref=True)
    files["not-a-pdf.pdf"] = b"This is a plain text file with a .pdf name.\n" * 20

    for name, data in files.items():
        (out_dir / name).write_bytes(data)
    return {name: len(data) for name, data in files.items()}


if __name__ == "__main__":
    target = Path(sys.argv[1] if len(sys.argv) > 1 else "pdfs")
    for name, size in generate(target).items():
        print(f"{size:7d}  {name}")
