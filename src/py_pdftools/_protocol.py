"""Private, versioned data contract shared with the native implementation."""

from __future__ import annotations

import json
from enum import IntEnum
from typing import NoReturn

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
    OcrDocument,
    OcrTextLayerOptions,
    PdfBox,
    PdfInfo,
    PdfPageInfo,
)

NATIVE_ABI_VERSION = 1
REQUEST_SCHEMA_VERSION = 1
INSPECTION_SCHEMA_VERSION = 1


class NativeStatus(IntEnum):
    """Stable status values returned by the private C ABI."""

    SUCCESS = 0
    INVALID_PDF = 1
    INVALID_OCR_DATA = 2
    PAGE_MISMATCH = 3
    PDF_PASSWORD_REQUIRED = 4
    PDF_PERMISSION = 5
    FONT_ERROR = 6
    PDF_PROCESSING = 7


_STATUS_EXCEPTIONS: dict[NativeStatus, type[PdfToolsError]] = {
    NativeStatus.INVALID_PDF: InvalidPdfError,
    NativeStatus.INVALID_OCR_DATA: InvalidOcrDataError,
    NativeStatus.PAGE_MISMATCH: PageMismatchError,
    NativeStatus.PDF_PASSWORD_REQUIRED: PdfPasswordRequiredError,
    NativeStatus.PDF_PERMISSION: PdfPermissionError,
    NativeStatus.FONT_ERROR: FontError,
    NativeStatus.PDF_PROCESSING: PdfProcessingError,
}


def serialize_ocr_request(
    ocr: OcrDocument,
    options: OcrTextLayerOptions,
) -> bytes:
    """Serialize one complete OCR transformation request as UTF-8 JSON."""

    request = {
        "schemaVersion": REQUEST_SCHEMA_VERSION,
        "options": {
            "allowPartialDocument": options.allow_partial_document,
            "minimumConfidence": options.minimum_confidence,
            "debugVisibleText": options.debug_visible_text,
        },
        "pages": [
            {
                "pageIndex": page.page_index,
                "orientation": page.orientation,
                "lines": [
                    {
                        "text": line.text,
                        "confidence": line.confidence,
                        "bounds": {
                            "left": line.bounds.left,
                            "top": line.bounds.top,
                            "width": line.bounds.width,
                            "height": line.bounds.height,
                        },
                    }
                    for line in page.lines
                ],
            }
            for page in ocr.pages
        ],
    }
    return json.dumps(
        request,
        allow_nan=False,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")


def deserialize_pdf_info(data: bytes) -> PdfInfo:
    """Validate and convert a native PDF-inspection response."""

    if not isinstance(data, bytes):
        raise NativeLibraryError("native inspection response must be bytes")

    try:
        payload = json.loads(
            data.decode("utf-8"),
            parse_constant=_reject_json_constant,
        )
        root = _require_mapping(payload, name="inspection response")
        schema_version = root["schemaVersion"]
        if isinstance(schema_version, bool) or not isinstance(schema_version, int):
            raise TypeError("schemaVersion must be an int")
        if schema_version != INSPECTION_SCHEMA_VERSION:
            raise ValueError(
                "unsupported inspection schema version "
                f"{schema_version}; expected {INSPECTION_SCHEMA_VERSION}"
            )

        raw_pages = root["pages"]
        if not isinstance(raw_pages, list):
            raise TypeError("pages must be a list")
        pages = tuple(_deserialize_page(page) for page in raw_pages)
        return PdfInfo(
            page_count=root["pageCount"],
            pages=pages,
            encrypted=root["encrypted"],
        )
    except (KeyError, TypeError, ValueError, UnicodeError, InvalidOcrDataError) as error:
        raise NativeLibraryError(f"invalid native inspection response: {error}") from error


def exception_for_status(status: int, message: str) -> PdfToolsError:
    """Map a native status and diagnostic message onto the public hierarchy."""

    try:
        native_status = NativeStatus(status)
    except ValueError:
        return NativeLibraryError(
            f"native call failed with unknown status {status}: {_message_or_default(message)}"
        )

    if native_status is NativeStatus.SUCCESS:
        raise ValueError("success status does not represent an exception")
    exception_type = _STATUS_EXCEPTIONS[native_status]
    return exception_type(_message_or_default(message))


def _deserialize_page(value: object) -> PdfPageInfo:
    page = _require_mapping(value, name="page")
    return PdfPageInfo(
        page_index=page["pageIndex"],
        width_points=page["widthPoints"],
        height_points=page["heightPoints"],
        rotation=page["rotation"],
        crop_box=_deserialize_box(page["cropBox"]),
        media_box=_deserialize_box(page["mediaBox"]),
    )


def _deserialize_box(value: object) -> PdfBox:
    box = _require_mapping(value, name="PDF box")
    return PdfBox(
        lower_left_x=box["lowerLeftX"],
        lower_left_y=box["lowerLeftY"],
        upper_right_x=box["upperRightX"],
        upper_right_y=box["upperRightY"],
    )


def _require_mapping(value: object, *, name: str) -> dict[str, object]:
    if not isinstance(value, dict):
        raise TypeError(f"{name} must be an object")
    if not all(isinstance(key, str) for key in value):
        raise TypeError(f"{name} keys must be strings")
    return value


def _reject_json_constant(value: str) -> NoReturn:
    raise ValueError(f"non-finite JSON number {value} is not allowed")


def _message_or_default(message: str) -> str:
    message = message.strip()
    return message or "native PDF operation failed"
