"""Public exception hierarchy for :mod:`py_pdftools`."""


class PdfToolsError(Exception):
    """Base exception for py-pdftools."""


class NativeLibraryError(PdfToolsError):
    """The native library could not be loaded, initialized, or used."""


class InvalidPdfError(PdfToolsError):
    """The input is not a valid or supported PDF."""


class InvalidOcrDataError(PdfToolsError):
    """The OCR model is structurally or numerically invalid."""


class PageMismatchError(InvalidOcrDataError):
    """The supplied OCR pages do not match the PDF pages."""


class PdfPasswordRequiredError(InvalidPdfError):
    """The PDF requires a password."""


class PdfPermissionError(InvalidPdfError):
    """The PDF security settings prohibit modification."""


class FontError(PdfToolsError):
    """OCR text cannot be represented using the configured font."""


class PdfProcessingError(PdfToolsError):
    """The native backend failed while processing or saving the PDF."""
