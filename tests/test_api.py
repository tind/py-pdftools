from __future__ import annotations

import unittest

import py_pdftools
from py_pdftools import (
    InvalidPdfError,
    NativeLibraryError,
    OcrDocument,
    add_ocr_text_layer,
    inspect_pdf,
)


class PublicApiTests(unittest.TestCase):
    def test_package_exports_the_documented_public_contract(self) -> None:
        self.assertEqual(
            set(py_pdftools.__all__),
            {
                "FontError",
                "InvalidOcrDataError",
                "InvalidPdfError",
                "NativeLibraryError",
                "NormalizedRect",
                "OcrDocument",
                "OcrLine",
                "OcrPage",
                "OcrTextLayerOptions",
                "Orientation",
                "PageMismatchError",
                "PdfBox",
                "PdfInfo",
                "PdfPageInfo",
                "PdfPasswordRequiredError",
                "PdfPermissionError",
                "PdfProcessingError",
                "PdfToolsError",
                "add_ocr_text_layer",
                "inspect_pdf",
            },
        )

    def test_pdf_input_accepts_all_documented_byte_types(self) -> None:
        ocr = OcrDocument(pages=())

        for pdf in (b"%PDF", bytearray(b"%PDF"), memoryview(b"%PDF")):
            with self.subTest(input_type=type(pdf).__name__):
                with self.assertRaises(NativeLibraryError):
                    add_ocr_text_layer(pdf, ocr)
                with self.assertRaises(NativeLibraryError):
                    inspect_pdf(pdf)

    def test_non_byte_pdf_input_is_rejected(self) -> None:
        with self.assertRaises(TypeError):
            inspect_pdf("%PDF")  # type: ignore[arg-type]

    def test_empty_pdf_input_is_rejected(self) -> None:
        with self.assertRaises(InvalidPdfError):
            inspect_pdf(b"")

    def test_add_requires_public_model_types(self) -> None:
        with self.assertRaises(TypeError):
            add_ocr_text_layer(b"%PDF", object())  # type: ignore[arg-type]
        with self.assertRaises(TypeError):
            add_ocr_text_layer(
                b"%PDF",
                OcrDocument(pages=()),
                options=object(),  # type: ignore[arg-type]
            )


if __name__ == "__main__":
    unittest.main()
