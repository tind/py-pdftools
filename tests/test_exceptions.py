from __future__ import annotations

import unittest

from py_pdftools import (
    FontError,
    InvalidOcrDataError,
    InvalidPdfError,
    NativeLibraryError,
    PageMismatchError,
    PdfPasswordRequiredError,
    PdfPermissionError,
    PdfProcessingError,
    PdfToolsError,
)


class ExceptionHierarchyTests(unittest.TestCase):
    def test_all_public_exceptions_have_the_documented_base(self) -> None:
        exception_types = (
            NativeLibraryError,
            InvalidPdfError,
            InvalidOcrDataError,
            PageMismatchError,
            PdfPasswordRequiredError,
            PdfPermissionError,
            FontError,
            PdfProcessingError,
        )
        for exception_type in exception_types:
            with self.subTest(exception_type=exception_type.__name__):
                self.assertTrue(issubclass(exception_type, PdfToolsError))

    def test_specialized_exceptions_have_the_documented_parent(self) -> None:
        self.assertTrue(issubclass(PageMismatchError, InvalidOcrDataError))
        self.assertTrue(issubclass(PdfPasswordRequiredError, InvalidPdfError))
        self.assertTrue(issubclass(PdfPermissionError, InvalidPdfError))


if __name__ == "__main__":
    unittest.main()
