from __future__ import annotations

import os
import unittest

from py_pdftools import InvalidPdfError, inspect_pdf
from py_pdftools._runtime import _shutdown_runtime


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


@unittest.skipUnless(
    os.environ.get("PY_PDFTOOLS_NATIVE_LIBRARY"),
    "requires a built native library",
)
class NativeInspectionEndToEndTests(unittest.TestCase):
    @classmethod
    def tearDownClass(cls) -> None:
        _shutdown_runtime()

    def test_inspects_a_rotated_crop_box_pdf_through_the_real_abi(self) -> None:
        info = inspect_pdf(one_page_pdf())

        self.assertEqual(info.page_count, 1)
        self.assertFalse(info.encrypted)
        page = info.pages[0]
        self.assertEqual(page.rotation, 90)
        self.assertEqual(page.width_points, 600.0)
        self.assertEqual(page.height_points, 400.0)
        self.assertEqual(page.crop_box.lower_left_x, 10.0)
        self.assertEqual(page.crop_box.lower_left_y, 20.0)
        self.assertEqual(page.crop_box.upper_right_x, 410.0)
        self.assertEqual(page.crop_box.upper_right_y, 620.0)

    def test_maps_a_real_native_pdf_error(self) -> None:
        with self.assertRaisesRegex(InvalidPdfError, "PDF could not be read"):
            inspect_pdf(b"not a PDF")

    def test_reuses_the_native_isolate_for_repeated_calls(self) -> None:
        first = inspect_pdf(one_page_pdf())
        second = inspect_pdf(one_page_pdf())

        self.assertEqual(first, second)


if __name__ == "__main__":
    unittest.main()
