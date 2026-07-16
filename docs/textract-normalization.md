# Textract normalization example

`py-pdftools` does not depend on boto3 and never sends a raw provider response
to the native library. Applications can normalize a response mapping returned
by their own AWS integration into the public OCR model.

The following example handles Textract `LINE` blocks. It assumes pages are
upright unless the caller supplies an orientation for a known sideways page.

```python
from collections import defaultdict
from collections.abc import Mapping

from py_pdftools import NormalizedRect, OcrDocument, OcrLine, OcrPage


def ocr_document_from_textract(
    response: Mapping[str, object],
    *,
    page_orientations: Mapping[int, int] | None = None,
) -> OcrDocument:
    raw_blocks = response.get("Blocks")
    if not isinstance(raw_blocks, list):
        raise ValueError("Textract response must contain a Blocks list")

    lines_by_page: dict[int, list[OcrLine]] = defaultdict(list)
    for block in raw_blocks:
        if not isinstance(block, Mapping) or block.get("BlockType") != "LINE":
            continue

        page_number = block.get("Page")
        text = block.get("Text")
        confidence = block.get("Confidence")
        geometry = block.get("Geometry")
        if (
            not isinstance(page_number, int)
            or page_number < 1
            or not isinstance(text, str)
            or not isinstance(geometry, Mapping)
        ):
            raise ValueError("invalid Textract LINE block")
        box = geometry.get("BoundingBox")
        if not isinstance(box, Mapping):
            raise ValueError("Textract LINE block has no bounding box")

        lines_by_page[page_number - 1].append(
            OcrLine(
                text=text,
                confidence=(
                    float(confidence)
                    if isinstance(confidence, (int, float))
                    else None
                ),
                bounds=NormalizedRect(
                    left=float(box["Left"]),
                    top=float(box["Top"]),
                    width=float(box["Width"]),
                    height=float(box["Height"]),
                ),
            )
        )

    orientations = page_orientations or {}
    return OcrDocument(
        pages=tuple(
            OcrPage(
                page_index=page_index,
                orientation=orientations.get(page_index, 0),
                lines=tuple(lines_by_page[page_index]),
            )
            for page_index in sorted(lines_by_page)
        )
    )
```

Textract page numbers are one-based; `OcrPage.page_index` is zero-based. Its
bounding boxes already use the normalized top-left coordinate convention
expected by `py-pdftools`.

For sideways scans, determine orientation from the provider's word polygons or
your OCR pipeline and pass a mapping such as `{0: 90}`. Orientation uses the
visible-page top-left coordinate system: `0 = right`, `90 = down`, `180 = left`,
and `270 = up`. Keep provider-specific orientation heuristics outside the core
PDF package.
