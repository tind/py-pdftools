"""High-level Python operations.

The native call boundary is introduced in milestone M2. Keeping the public
entry points here now lets callers and type checkers rely on the documented
surface while producing an explicit error if an operation is attempted before
the native backend is available.
"""

from __future__ import annotations

from ._exceptions import InvalidPdfError, NativeLibraryError
from ._models import OcrDocument, OcrTextLayerOptions, PdfInfo


def _validated_pdf_bytes(pdf: bytes | bytearray | memoryview) -> bytes:
    if not isinstance(pdf, (bytes, bytearray, memoryview)):
        raise TypeError("pdf must be bytes, bytearray, or memoryview")

    value = bytes(pdf)
    if not value:
        raise InvalidPdfError("pdf must not be empty")
    return value


def _native_backend_unavailable() -> NativeLibraryError:
    return NativeLibraryError(
        "the native py-pdftools backend has not been installed or initialized"
    )


def add_ocr_text_layer(
    pdf: bytes | bytearray | memoryview,
    ocr: OcrDocument,
    *,
    options: OcrTextLayerOptions | None = None,
) -> bytes:
    """Return a copy of a PDF with an invisible OCR text layer added."""

    _validated_pdf_bytes(pdf)
    if not isinstance(ocr, OcrDocument):
        raise TypeError("ocr must be an OcrDocument")
    if options is not None and not isinstance(options, OcrTextLayerOptions):
        raise TypeError("options must be an OcrTextLayerOptions or None")

    raise _native_backend_unavailable()


def inspect_pdf(pdf: bytes | bytearray | memoryview) -> PdfInfo:
    """Return basic page and geometry information about a PDF."""

    _validated_pdf_bytes(pdf)
    raise _native_backend_unavailable()
