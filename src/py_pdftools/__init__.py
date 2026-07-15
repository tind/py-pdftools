"""Public API for :mod:`py_pdftools`."""

from ._api import add_ocr_text_layer, inspect_pdf
from ._exceptions import (
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
from ._models import (
    NormalizedRect,
    OcrDocument,
    OcrLine,
    OcrPage,
    OcrTextLayerOptions,
    Orientation,
    PdfBox,
    PdfInfo,
    PdfPageInfo,
)

__all__ = [
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
]
