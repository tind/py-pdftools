from __future__ import annotations

import json
import unittest

from py_pdftools import (
    FontError,
    InvalidOcrDataError,
    InvalidPdfError,
    NativeLibraryError,
    NormalizedRect,
    OcrDocument,
    OcrLine,
    OcrPage,
    OcrTextLayerOptions,
    PageMismatchError,
    PdfPasswordRequiredError,
    PdfPermissionError,
    PdfProcessingError,
)
from py_pdftools._protocol import (
    INSPECTION_SCHEMA_VERSION,
    NATIVE_ABI_VERSION,
    REQUEST_SCHEMA_VERSION,
    NativeStatus,
    deserialize_pdf_info,
    exception_for_status,
    serialize_ocr_request,
)


def inspection_response() -> bytes:
    return json.dumps(
        {
            "schemaVersion": 1,
            "pageCount": 1,
            "encrypted": False,
            "pages": [
                {
                    "pageIndex": 0,
                    "widthPoints": 792.0,
                    "heightPoints": 612.0,
                    "rotation": 90,
                    "cropBox": {
                        "lowerLeftX": 10.0,
                        "lowerLeftY": 20.0,
                        "upperRightX": 622.0,
                        "upperRightY": 812.0,
                    },
                    "mediaBox": {
                        "lowerLeftX": 0.0,
                        "lowerLeftY": 0.0,
                        "upperRightX": 612.0,
                        "upperRightY": 792.0,
                    },
                }
            ],
        },
        separators=(",", ":"),
    ).encode("utf-8")


class OcrRequestSerializationTests(unittest.TestCase):
    def test_protocol_versions_begin_at_one(self) -> None:
        self.assertEqual(NATIVE_ABI_VERSION, 1)
        self.assertEqual(REQUEST_SCHEMA_VERSION, 1)
        self.assertEqual(INSPECTION_SCHEMA_VERSION, 1)

    def test_serializes_the_complete_document_and_options(self) -> None:
        ocr = OcrDocument(
            pages=(
                OcrPage(
                    page_index=2,
                    orientation=270,
                    lines=(
                        OcrLine(
                            text="Blåbær 日本語",
                            bounds=NormalizedRect(0.1, 0.2, 0.3, 0.04),
                            confidence=97.5,
                        ),
                    ),
                ),
            )
        )
        options = OcrTextLayerOptions(
            allow_partial_document=True,
            minimum_confidence=80.0,
            debug_visible_text=True,
        )

        encoded = serialize_ocr_request(ocr, options)
        payload = json.loads(encoded)

        self.assertIn("日本語".encode(), encoded)
        self.assertEqual(payload["schemaVersion"], 1)
        self.assertEqual(
            payload["options"],
            {
                "allowPartialDocument": True,
                "minimumConfidence": 80.0,
                "debugVisibleText": True,
            },
        )
        self.assertEqual(payload["pages"][0]["pageIndex"], 2)
        self.assertEqual(payload["pages"][0]["orientation"], 270)
        self.assertEqual(payload["pages"][0]["lines"][0]["text"], "Blåbær 日本語")
        self.assertEqual(
            payload["pages"][0]["lines"][0]["bounds"],
            {"left": 0.1, "top": 0.2, "width": 0.3, "height": 0.04},
        )

    def test_serialization_is_compact_and_deterministic(self) -> None:
        ocr = OcrDocument(pages=())
        options = OcrTextLayerOptions()

        first = serialize_ocr_request(ocr, options)
        second = serialize_ocr_request(ocr, options)

        self.assertEqual(first, second)
        self.assertNotIn(b"\n", first)
        self.assertNotIn(b": ", first)


class InspectionDeserializationTests(unittest.TestCase):
    def test_deserializes_page_geometry(self) -> None:
        info = deserialize_pdf_info(inspection_response())

        self.assertEqual(info.page_count, 1)
        self.assertFalse(info.encrypted)
        self.assertEqual(info.pages[0].rotation, 90)
        self.assertEqual(info.pages[0].width_points, 792.0)
        self.assertEqual(info.pages[0].crop_box.lower_left_x, 10.0)

    def test_rejects_unsupported_schema_version(self) -> None:
        data = inspection_response().replace(b'"schemaVersion":1', b'"schemaVersion":2')

        with self.assertRaisesRegex(NativeLibraryError, "unsupported.*version 2"):
            deserialize_pdf_info(data)

    def test_rejects_malformed_or_inconsistent_responses(self) -> None:
        malformed_responses = (
            b"not json",
            b"\xff",
            b'{"schemaVersion":1,"pageCount":0,"encrypted":false,"pages":NaN}',
            b'{"schemaVersion":1,"pageCount":1,"encrypted":false,"pages":[]}',
        )
        for response in malformed_responses:
            with self.subTest(response=response):
                with self.assertRaises(NativeLibraryError):
                    deserialize_pdf_info(response)


class NativeStatusTests(unittest.TestCase):
    def test_maps_every_error_status_to_the_public_exception_hierarchy(self) -> None:
        expected = {
            NativeStatus.INVALID_PDF: InvalidPdfError,
            NativeStatus.INVALID_OCR_DATA: InvalidOcrDataError,
            NativeStatus.PAGE_MISMATCH: PageMismatchError,
            NativeStatus.PDF_PASSWORD_REQUIRED: PdfPasswordRequiredError,
            NativeStatus.PDF_PERMISSION: PdfPermissionError,
            NativeStatus.FONT_ERROR: FontError,
            NativeStatus.PDF_PROCESSING: PdfProcessingError,
        }
        for status, exception_type in expected.items():
            with self.subTest(status=status):
                error = exception_for_status(status, "diagnostic")
                self.assertIsInstance(error, exception_type)
                self.assertEqual(str(error), "diagnostic")

    def test_unknown_status_is_a_native_library_error(self) -> None:
        error = exception_for_status(999, "unexpected")

        self.assertIsInstance(error, NativeLibraryError)
        self.assertIn("unknown status 999", str(error))

    def test_success_status_cannot_be_converted_to_an_exception(self) -> None:
        with self.assertRaises(ValueError):
            exception_for_status(NativeStatus.SUCCESS, "")


if __name__ == "__main__":
    unittest.main()
