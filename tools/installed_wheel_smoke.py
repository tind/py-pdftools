"""Exercise an installed wheel without importing from the repository source tree."""

from __future__ import annotations

from py_pdftools import (
    NormalizedRect,
    OcrDocument,
    OcrLine,
    OcrPage,
    add_ocr_text_layer,
    inspect_pdf,
)


def one_page_pdf() -> bytes:
    objects = (
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        (
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
            b"/CropBox [10 20 410 620] /Rotate 90 /Resources << >> "
            b"/Contents 4 0 R >>"
        ),
        b"<< /Length 0 >>\nstream\n\nendstream",
    )
    pdf = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = [0]
    for object_number, value in enumerate(objects, start=1):
        offsets.append(len(pdf))
        pdf.extend(f"{object_number} 0 obj\n".encode("ascii"))
        pdf.extend(value)
        pdf.extend(b"\nendobj\n")

    xref_offset = len(pdf)
    pdf.extend(f"xref\n0 {len(objects) + 1}\n".encode("ascii"))
    pdf.extend(b"0000000000 65535 f \n")
    for offset in offsets[1:]:
        pdf.extend(f"{offset:010d} 00000 n \n".encode("ascii"))
    pdf.extend(
        (
            f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\n"
            f"startxref\n{xref_offset}\n%%EOF\n"
        ).encode("ascii")
    )
    return bytes(pdf)


def main() -> None:
    source = one_page_pdf()
    source_info = inspect_pdf(source)
    assert source_info.page_count == 1
    assert source_info.pages[0].rotation == 90

    ocr = OcrDocument(
        pages=(
            OcrPage(
                page_index=0,
                orientation=0,
                lines=(
                    OcrLine(
                        text="Installed wheel OCR café Ω Привет",
                        bounds=NormalizedRect(0.1, 0.2, 0.6, 0.08),
                        confidence=99.0,
                    ),
                ),
            ),
        ),
    )
    transformed = add_ocr_text_layer(source, ocr)
    assert transformed.startswith(b"%PDF-")
    assert len(transformed) > len(source)

    transformed_info = inspect_pdf(transformed)
    assert transformed_info == source_info
    print("installed wheel smoke test passed")


if __name__ == "__main__":
    main()
