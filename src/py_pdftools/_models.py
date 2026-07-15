"""Immutable public data models and their local validation rules."""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Literal, TypeAlias, cast

from ._exceptions import InvalidOcrDataError

Orientation: TypeAlias = Literal[0, 90, 180, 270]

_VALID_ORIENTATIONS = frozenset((0, 90, 180, 270))
_NORMALIZED_TOLERANCE = 1e-6


def _require_int(value: object, *, field: str, minimum: int = 0) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise TypeError(f"{field} must be an int")
    if value < minimum:
        raise InvalidOcrDataError(f"{field} must be at least {minimum}")
    return value


def _require_finite_number(value: object, *, field: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise TypeError(f"{field} must be a finite number")
    result = float(value)
    if not math.isfinite(result):
        raise InvalidOcrDataError(f"{field} must be finite")
    return result


def _require_orientation(value: object, *, field: str = "orientation") -> Orientation:
    if isinstance(value, bool) or not isinstance(value, int):
        raise TypeError(f"{field} must be an int")
    if value not in _VALID_ORIENTATIONS:
        raise InvalidOcrDataError(f"{field} must be one of 0, 90, 180, or 270")
    return cast(Orientation, value)


def _require_tuple(value: object, *, field: str) -> tuple[object, ...]:
    if not isinstance(value, tuple):
        raise TypeError(f"{field} must be a tuple")
    return value


def _require_confidence(value: object, *, field: str) -> float:
    result = _require_finite_number(value, field=field)
    if not 0.0 <= result <= 100.0:
        raise InvalidOcrDataError(f"{field} must be between 0.0 and 100.0")
    return result


@dataclass(frozen=True, slots=True)
class NormalizedRect:
    """A rectangle in normalized visible-page coordinates."""

    left: float
    top: float
    width: float
    height: float

    def __post_init__(self) -> None:
        left = _require_finite_number(self.left, field="left")
        top = _require_finite_number(self.top, field="top")
        width = _require_finite_number(self.width, field="width")
        height = _require_finite_number(self.height, field="height")

        if width <= 0.0:
            raise InvalidOcrDataError("width must be greater than 0.0")
        if height <= 0.0:
            raise InvalidOcrDataError("height must be greater than 0.0")

        right = left + width
        bottom = top + height
        tolerance = _NORMALIZED_TOLERANCE
        if left < -tolerance or top < -tolerance:
            raise InvalidOcrDataError("rectangle starts outside the normalized page")
        if right > 1.0 + tolerance or bottom > 1.0 + tolerance:
            raise InvalidOcrDataError("rectangle ends outside the normalized page")

        clamped_left = min(1.0, max(0.0, left))
        clamped_top = min(1.0, max(0.0, top))
        clamped_right = min(1.0, max(0.0, right))
        clamped_bottom = min(1.0, max(0.0, bottom))
        clamped_width = clamped_right - clamped_left
        clamped_height = clamped_bottom - clamped_top
        if clamped_width <= 0.0 or clamped_height <= 0.0:
            raise InvalidOcrDataError("rectangle must overlap the normalized page")

        object.__setattr__(self, "left", clamped_left)
        object.__setattr__(self, "top", clamped_top)
        object.__setattr__(self, "width", clamped_width)
        object.__setattr__(self, "height", clamped_height)


@dataclass(frozen=True, slots=True)
class OcrLine:
    """One OCR text line and its normalized page bounds."""

    text: str
    bounds: NormalizedRect
    confidence: float | None = None

    def __post_init__(self) -> None:
        if not isinstance(self.text, str):
            raise TypeError("text must be a str")
        if not isinstance(self.bounds, NormalizedRect):
            raise TypeError("bounds must be a NormalizedRect")
        if self.confidence is not None:
            object.__setattr__(
                self,
                "confidence",
                _require_confidence(self.confidence, field="confidence"),
            )


@dataclass(frozen=True, slots=True)
class OcrPage:
    """OCR lines for one zero-based PDF page index."""

    page_index: int
    orientation: Orientation
    lines: tuple[OcrLine, ...]

    def __post_init__(self) -> None:
        object.__setattr__(
            self,
            "page_index",
            _require_int(self.page_index, field="page_index"),
        )
        object.__setattr__(
            self,
            "orientation",
            _require_orientation(self.orientation),
        )
        lines = _require_tuple(self.lines, field="lines")
        if not all(isinstance(line, OcrLine) for line in lines):
            raise TypeError("lines must contain only OcrLine values")


@dataclass(frozen=True, slots=True)
class OcrDocument:
    """Provider-independent OCR data for a PDF document."""

    pages: tuple[OcrPage, ...]

    def __post_init__(self) -> None:
        pages = _require_tuple(self.pages, field="pages")
        if not all(isinstance(page, OcrPage) for page in pages):
            raise TypeError("pages must contain only OcrPage values")
        page_indexes = [page.page_index for page in pages]
        if len(page_indexes) != len(set(page_indexes)):
            raise InvalidOcrDataError("duplicate OCR page indexes are not allowed")


@dataclass(frozen=True, slots=True)
class OcrTextLayerOptions:
    """Controls page matching, filtering, and diagnostic text rendering."""

    allow_partial_document: bool = False
    minimum_confidence: float | None = None
    debug_visible_text: bool = False

    def __post_init__(self) -> None:
        if not isinstance(self.allow_partial_document, bool):
            raise TypeError("allow_partial_document must be a bool")
        if not isinstance(self.debug_visible_text, bool):
            raise TypeError("debug_visible_text must be a bool")
        if self.minimum_confidence is not None:
            object.__setattr__(
                self,
                "minimum_confidence",
                _require_confidence(
                    self.minimum_confidence,
                    field="minimum_confidence",
                ),
            )


@dataclass(frozen=True, slots=True)
class PdfBox:
    """A rectangular PDF box in PDF user-space points."""

    lower_left_x: float
    lower_left_y: float
    upper_right_x: float
    upper_right_y: float

    def __post_init__(self) -> None:
        lower_left_x = _require_finite_number(
            self.lower_left_x,
            field="lower_left_x",
        )
        lower_left_y = _require_finite_number(
            self.lower_left_y,
            field="lower_left_y",
        )
        upper_right_x = _require_finite_number(
            self.upper_right_x,
            field="upper_right_x",
        )
        upper_right_y = _require_finite_number(
            self.upper_right_y,
            field="upper_right_y",
        )
        if upper_right_x <= lower_left_x or upper_right_y <= lower_left_y:
            raise InvalidOcrDataError("PDF box must have positive width and height")

        object.__setattr__(self, "lower_left_x", lower_left_x)
        object.__setattr__(self, "lower_left_y", lower_left_y)
        object.__setattr__(self, "upper_right_x", upper_right_x)
        object.__setattr__(self, "upper_right_y", upper_right_y)


@dataclass(frozen=True, slots=True)
class PdfPageInfo:
    """Visible dimensions and page boxes for one PDF page."""

    page_index: int
    width_points: float
    height_points: float
    rotation: Orientation
    crop_box: PdfBox
    media_box: PdfBox

    def __post_init__(self) -> None:
        page_index = _require_int(self.page_index, field="page_index")
        width_points = _require_finite_number(
            self.width_points,
            field="width_points",
        )
        height_points = _require_finite_number(
            self.height_points,
            field="height_points",
        )
        if width_points <= 0.0 or height_points <= 0.0:
            raise InvalidOcrDataError("page dimensions must be greater than 0.0")
        if not isinstance(self.crop_box, PdfBox):
            raise TypeError("crop_box must be a PdfBox")
        if not isinstance(self.media_box, PdfBox):
            raise TypeError("media_box must be a PdfBox")

        object.__setattr__(self, "page_index", page_index)
        object.__setattr__(self, "width_points", width_points)
        object.__setattr__(self, "height_points", height_points)
        object.__setattr__(self, "rotation", _require_orientation(self.rotation, field="rotation"))


@dataclass(frozen=True, slots=True)
class PdfInfo:
    """Basic page and encryption information for a PDF document."""

    page_count: int
    pages: tuple[PdfPageInfo, ...]
    encrypted: bool

    def __post_init__(self) -> None:
        page_count = _require_int(self.page_count, field="page_count")
        pages = _require_tuple(self.pages, field="pages")
        if not all(isinstance(page, PdfPageInfo) for page in pages):
            raise TypeError("pages must contain only PdfPageInfo values")
        if not isinstance(self.encrypted, bool):
            raise TypeError("encrypted must be a bool")
        if len(pages) != page_count:
            raise InvalidOcrDataError("page_count must equal the number of pages")
        if tuple(page.page_index for page in pages) != tuple(range(page_count)):
            raise InvalidOcrDataError("PDF page indexes must be ordered and contiguous")

        object.__setattr__(self, "page_count", page_count)
