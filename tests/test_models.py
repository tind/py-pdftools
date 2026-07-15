from __future__ import annotations

import math
import unittest
from dataclasses import FrozenInstanceError

from py_pdftools import (
    InvalidOcrDataError,
    NormalizedRect,
    OcrDocument,
    OcrLine,
    OcrPage,
    OcrTextLayerOptions,
    PdfBox,
    PdfInfo,
    PdfPageInfo,
)


def sample_rect() -> NormalizedRect:
    return NormalizedRect(left=0.1, top=0.2, width=0.3, height=0.04)


def sample_box() -> PdfBox:
    return PdfBox(0.0, 0.0, 612.0, 792.0)


def sample_page_info(page_index: int = 0) -> PdfPageInfo:
    box = sample_box()
    return PdfPageInfo(page_index, 612.0, 792.0, 0, box, box)


class NormalizedRectTests(unittest.TestCase):
    def test_accepts_a_valid_rectangle_and_canonicalizes_numbers(self) -> None:
        rect = NormalizedRect(left=0, top=0, width=1, height=1)

        self.assertEqual(rect, NormalizedRect(0.0, 0.0, 1.0, 1.0))
        self.assertIsInstance(rect.left, float)

    def test_preserves_in_range_float_dimensions_without_recalculation(self) -> None:
        rect = NormalizedRect(left=0.1, top=0.2, width=0.3, height=0.04)

        self.assertEqual(rect.width, 0.3)
        self.assertEqual(rect.height, 0.04)

    def test_clamps_values_within_numeric_tolerance(self) -> None:
        rect = NormalizedRect(-0.0000005, 0.1, 1.000001, 0.2)

        self.assertEqual(rect.left, 0.0)
        self.assertEqual(rect.width, 1.0)

    def test_rejects_non_finite_coordinates(self) -> None:
        for value in (math.nan, math.inf, -math.inf):
            with self.subTest(value=value):
                with self.assertRaises(InvalidOcrDataError):
                    NormalizedRect(value, 0.0, 0.5, 0.5)

    def test_rejects_non_positive_dimensions(self) -> None:
        for width, height in ((0.0, 0.1), (-0.1, 0.1), (0.1, 0.0)):
            with self.subTest(width=width, height=height):
                with self.assertRaises(InvalidOcrDataError):
                    NormalizedRect(0.0, 0.0, width, height)

    def test_rejects_coordinates_materially_outside_page(self) -> None:
        for values in (
            (-0.01, 0.0, 0.5, 0.5),
            (0.0, -0.01, 0.5, 0.5),
            (0.6, 0.0, 0.5, 0.5),
            (0.0, 0.6, 0.5, 0.5),
        ):
            with self.subTest(values=values):
                with self.assertRaises(InvalidOcrDataError):
                    NormalizedRect(*values)

    def test_is_frozen_and_slotted(self) -> None:
        rect = sample_rect()

        with self.assertRaises(FrozenInstanceError):
            rect.left = 0.5  # type: ignore[misc]
        self.assertFalse(hasattr(rect, "__dict__"))


class OcrModelTests(unittest.TestCase):
    def test_builds_a_provider_independent_document(self) -> None:
        line = OcrLine("Example", sample_rect(), 98.4)
        page = OcrPage(0, 90, (line,))
        document = OcrDocument((page,))

        self.assertEqual(document.pages[0].lines[0].text, "Example")
        self.assertEqual(document.pages[0].orientation, 90)
        self.assertEqual(document.pages[0].lines[0].confidence, 98.4)

    def test_rejects_invalid_confidence(self) -> None:
        for confidence in (-0.1, 100.1, math.nan):
            with self.subTest(confidence=confidence):
                with self.assertRaises(InvalidOcrDataError):
                    OcrLine("Example", sample_rect(), confidence)

    def test_rejects_negative_page_index(self) -> None:
        with self.assertRaises(InvalidOcrDataError):
            OcrPage(-1, 0, ())

    def test_rejects_unsupported_orientation(self) -> None:
        with self.assertRaises(InvalidOcrDataError):
            OcrPage(0, 45, ())  # type: ignore[arg-type]

    def test_requires_tuples_to_preserve_immutability(self) -> None:
        with self.assertRaises(TypeError):
            OcrPage(0, 0, [])  # type: ignore[arg-type]
        with self.assertRaises(TypeError):
            OcrDocument([])  # type: ignore[arg-type]

    def test_rejects_duplicate_page_indexes(self) -> None:
        with self.assertRaises(InvalidOcrDataError):
            OcrDocument((OcrPage(1, 0, ()), OcrPage(1, 90, ())))


class OptionsTests(unittest.TestCase):
    def test_defaults_match_the_public_contract(self) -> None:
        options = OcrTextLayerOptions()

        self.assertFalse(options.allow_partial_document)
        self.assertIsNone(options.minimum_confidence)
        self.assertFalse(options.debug_visible_text)

    def test_validates_minimum_confidence(self) -> None:
        self.assertEqual(OcrTextLayerOptions(minimum_confidence=0).minimum_confidence, 0.0)
        self.assertEqual(
            OcrTextLayerOptions(minimum_confidence=100).minimum_confidence,
            100.0,
        )
        with self.assertRaises(InvalidOcrDataError):
            OcrTextLayerOptions(minimum_confidence=100.01)

    def test_requires_boolean_flags(self) -> None:
        with self.assertRaises(TypeError):
            OcrTextLayerOptions(allow_partial_document=1)  # type: ignore[arg-type]


class PdfInspectionModelTests(unittest.TestCase):
    def test_builds_valid_pdf_info(self) -> None:
        page = sample_page_info()

        info = PdfInfo(page_count=1, pages=(page,), encrypted=False)

        self.assertEqual(info.page_count, 1)
        self.assertEqual(info.pages[0].width_points, 612.0)

    def test_rejects_invalid_box_geometry(self) -> None:
        with self.assertRaises(InvalidOcrDataError):
            PdfBox(0.0, 0.0, 0.0, 10.0)

    def test_rejects_non_positive_page_dimensions(self) -> None:
        box = sample_box()
        with self.assertRaises(InvalidOcrDataError):
            PdfPageInfo(0, 0.0, 792.0, 0, box, box)

    def test_rejects_inconsistent_page_collection(self) -> None:
        with self.assertRaises(InvalidOcrDataError):
            PdfInfo(page_count=2, pages=(sample_page_info(),), encrypted=False)
        with self.assertRaises(InvalidOcrDataError):
            PdfInfo(
                page_count=1,
                pages=(sample_page_info(page_index=1),),
                encrypted=False,
            )


if __name__ == "__main__":
    unittest.main()
