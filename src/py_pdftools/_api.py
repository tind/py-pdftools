"""High-level Python operations."""

from __future__ import annotations

from . import _runtime
from ._exceptions import InvalidPdfError
from ._models import OcrDocument, OcrTextLayerOptions, PdfInfo
from ._protocol import deserialize_pdf_info, serialize_ocr_request


def _validated_pdf_bytes(pdf: bytes | bytearray | memoryview) -> bytes:
    if not isinstance(pdf, (bytes, bytearray, memoryview)):
        raise TypeError("pdf must be bytes, bytearray, or memoryview")

    value = bytes(pdf)
    if not value:
        raise InvalidPdfError("pdf must not be empty")
    return value


def add_ocr_text_layer(
    pdf: bytes | bytearray | memoryview,
    ocr: OcrDocument,
    *,
    options: OcrTextLayerOptions | None = None,
) -> bytes:
    """Return a copy of a PDF with an invisible OCR text layer added."""

    pdf_bytes = _validated_pdf_bytes(pdf)
    if not isinstance(ocr, OcrDocument):
        raise TypeError("ocr must be an OcrDocument")
    if options is not None and not isinstance(options, OcrTextLayerOptions):
        raise TypeError("options must be an OcrTextLayerOptions or None")

    effective_options = options or OcrTextLayerOptions()
    request = serialize_ocr_request(ocr, effective_options)
    return _runtime.add_ocr_text_layer(pdf_bytes, request)


def inspect_pdf(pdf: bytes | bytearray | memoryview) -> PdfInfo:
    """Return basic page and geometry information about a PDF."""

    pdf_bytes = _validated_pdf_bytes(pdf)
    response = _runtime.inspect_pdf(pdf_bytes)
    return deserialize_pdf_info(response)
