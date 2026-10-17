"""Writes page-labels.pdf: 5 pages labelled i, ii, 1, 2, 3 plus a 2-entry outline. Stdlib only."""
import sys

def build() -> bytes:
    objs = {}
    n_pages = 5
    page_ids = [10 + i for i in range(n_pages)]
    content_ids = [20 + i for i in range(n_pages)]
    objs[1] = "<< /Type /Catalog /Pages 2 0 R /Outlines 3 0 R /PageLabels << /Nums [0 << /S /r >> 2 << /S /D /St 1 >>] >> >>"
    objs[2] = "<< /Type /Pages /Kids [%s] /Count %d >>" % (" ".join(f"{p} 0 R" for p in page_ids), n_pages)
    objs[3] = "<< /Type /Outlines /First 4 0 R /Last 5 0 R /Count 2 >>"
    objs[4] = "<< /Title (Front matter) /Parent 3 0 R /Next 5 0 R /Dest [10 0 R /Fit] >>"
    objs[5] = "<< /Title (Chapter 1) /Parent 3 0 R /Prev 4 0 R /Dest [12 0 R /Fit] >>"
    objs[6] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
    labels = ["i", "ii", "1", "2", "3"]
    for i in range(n_pages):
        stream = f"BT /F1 24 Tf 72 700 Td (Physical page {i + 1}, printed {labels[i]}) Tj ET"
        objs[content_ids[i]] = f"<< /Length {len(stream)} >>\nstream\n{stream}\nendstream"
        objs[page_ids[i]] = (f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                             f"/Resources << /Font << /F1 6 0 R >> >> /Contents {content_ids[i]} 0 R >>")
    out = bytearray(b"%PDF-1.7\n")
    offsets = {}
    for num in sorted(objs):
        offsets[num] = len(out)
        out += f"{num} 0 obj\n{objs[num]}\nendobj\n".encode("latin-1")
    size = max(objs) + 1
    xref = len(out)
    out += f"xref\n0 {size}\n0000000000 65535 f \n".encode()
    for num in range(1, size):
        out += (f"{offsets[num]:010d} 00000 n \n" if num in offsets else "0000000000 65535 f \n").encode()
    out += f"trailer\n<< /Size {size} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    return bytes(out)

if __name__ == "__main__":
    open(sys.argv[1] if len(sys.argv) > 1 else "page-labels.pdf", "wb").write(build())
