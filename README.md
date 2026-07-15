# py-pdftools

`py-pdftools` is a Python library for high-level PDF transformations backed by
Apache PDFBox and compiled to a native library with GraalVM Native Image.

The initial release adds invisible, searchable OCR text layers to existing PDFs
without requiring Java at runtime. It accepts provider-independent OCR lines,
maps normalized visible-page coordinates through crop boxes and page rotation,
fits and embeds Unicode text, and returns a new PDF as bytes.

Self-contained wheels are available from
[PyPI](https://pypi.org/project/tindtechnologies-py-pdftools/) for all five
supported platforms. The complete implementation passes native, Python,
installation, and installed-wheel smoke tests. See
[`PROGRESS.md`](https://github.com/tind/py-pdftools/blob/main/PROGRESS.md) for
the release verification record.

## Installation

```bash
python -m pip install tindtechnologies-py-pdftools
```

The PyPI distribution is named `tindtechnologies-py-pdftools`; the Python
import remains `py_pdftools`.

## Runtime requirements

- Python 3.10 or newer.
- A platform wheel matching Linux x86-64/ARM64, macOS x86-64/ARM64, or Windows
  x86-64.
- No Java, JVM, Gradle, Maven, or GraalVM installation.

To build a wheel from source, use
[`BUILDING.md`](https://github.com/tind/py-pdftools/blob/main/BUILDING.md).

## Add an OCR text layer

```python
from pathlib import Path

from py_pdftools import (
    NormalizedRect,
    OcrDocument,
    OcrLine,
    OcrPage,
    OcrTextLayerOptions,
    add_ocr_text_layer,
)

source = Path("scan.pdf").read_bytes()
ocr = OcrDocument(
    pages=(
        OcrPage(
            page_index=0,
            orientation=0,
            lines=(
                OcrLine(
                    text="Example OCR text",
                    bounds=NormalizedRect(
                        left=0.10,
                        top=0.20,
                        width=0.40,
                        height=0.03,
                    ),
                    confidence=98.4,
                ),
            ),
        ),
    ),
)

result = add_ocr_text_layer(
    source,
    ocr,
    options=OcrTextLayerOptions(minimum_confidence=80.0),
)
Path("searchable.pdf").write_bytes(result)
```

OCR bounds use normalized coordinates relative to the visible crop box after
page rotation. The origin is at the visible top-left. `orientation` is the OCR
text direction in that same visible coordinate system and must be one of `0`,
`90`, `180`, or `270`.

By default the OCR document must contain every PDF page exactly once. Set
`allow_partial_document=True` to modify only supplied pages. Lines below
`minimum_confidence` are skipped; lines without confidence are retained.
`debug_visible_text=True` renders the inserted text for placement diagnostics.

## Inspect page geometry

```python
from pathlib import Path

from py_pdftools import inspect_pdf

info = inspect_pdf(Path("scan.pdf").read_bytes())
for page in info.pages:
    print(
        page.page_index,
        page.width_points,
        page.height_points,
        page.rotation,
        page.crop_box,
    )
```

Visible width and height reflect the crop box after page rotation. Box
coordinates themselves remain in PDF user space.

## Behavior and limits

- Existing page streams are preserved and the OCR layer is appended.
- Normal OCR text uses PDF rendering mode 3 (neither fill nor stroke), while
  remaining searchable and extractable.
- Noto Sans is subset and embedded. Unsupported characters raise `FontError`
  instead of being silently replaced.
- Ordinary left-to-right text is supported. Advanced bidirectional and complex
  script shaping is not guaranteed in version 0.1.
- The library consumes OCR results; it does not render pages or run an OCR
  engine.
- Password input is not supported. PDFs requiring a password raise
  `PdfPasswordRequiredError`, and modification restrictions raise
  `PdfPermissionError`.

All public failures derive from `PdfToolsError`. More specific exceptions
distinguish invalid PDFs, invalid OCR data, page mismatches, font coverage,
permissions, native loading, and PDF processing failures.

## License

Project code is licensed under the Apache License 2.0. The bundled Noto Sans
font is licensed separately under the SIL Open Font License 1.1. See
[`LICENSE`](https://github.com/tind/py-pdftools/blob/main/LICENSE),
[`NOTICE`](https://github.com/tind/py-pdftools/blob/main/NOTICE), and the
bundled third-party license texts for details.

## Project documentation

- [`SPECS.md`](https://github.com/tind/py-pdftools/blob/main/SPECS.md) — version
  0.1 behavior and acceptance contract.
- [`BUILDING.md`](https://github.com/tind/py-pdftools/blob/main/BUILDING.md) —
  local builds, tests, and platform wheels.
- [`docs/textract-normalization.md`](https://github.com/tind/py-pdftools/blob/main/docs/textract-normalization.md) — convert
  Textract-shaped mappings without adding boto3 to the package.
- [`docs/native-abi-v1.md`](https://github.com/tind/py-pdftools/blob/main/docs/native-abi-v1.md) — private Python/native
  protocol.
- [`docs/releasing.md`](https://github.com/tind/py-pdftools/blob/main/docs/releasing.md) — wheel and publication checklist.
