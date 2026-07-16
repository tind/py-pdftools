# `py-pdftools` Specification

**Status:** Implemented
**Target version:** 0.1.0
**Python distribution name:** `tindtechnologies-py-pdftools`
**Python import package:** `py_pdftools`
**Native implementation:** Java, Apache PDFBox, GraalVM Native Image
**Primary use case:** Add an invisible, searchable OCR text layer to an existing PDF.

---

## 1. Overview

`py-pdftools` is a Python library providing high-level PDF transformation operations backed by Apache PDFBox.

The initial release focuses on one primary operation:

```python
add_ocr_text_layer(...)
```

The library accepts an existing PDF and structured OCR results, inserts invisible searchable text at the corresponding page locations, and returns a modified PDF.

The Python caller is not required to understand:

* PDF page coordinate systems
* Crop boxes or media boxes
* Page rotation matrices
* PDF content streams
* Font resources
* Text rendering modes
* GraalVM isolates
* Native memory management
* Apache PDFBox classes

## The library is intended to replace the current PyMuPDF-based text-layer implementation. The existing implementation opens the original PDF, processes OCR results page by page, inserts one invisible text item for each OCR line, and saves a modified copy.

## 2. Goals

The library shall:

1. Add invisible, searchable OCR text to existing PDFs.
2. Accept PDFs and produce PDFs as Python byte sequences.
3. Accept OCR coordinates normalized to the visible page.
4. Correctly handle pages rotated by 0, 90, 180, or 270 degrees.
5. Preserve the existing visible appearance of the PDF.
6. Preserve existing PDF page content.
7. Support multipage PDFs.
8. provide a small, stable, type-annotated Python API.
9. Operate without requiring a JVM at runtime.
10. Package the Java implementation as a platform-specific native library.
11. Hide the C ABI and GraalVM runtime from normal Python callers.
12. Be independent of AWS Textract, boto3, S3, and other OCR-service infrastructure.

---

## 3. Non-goals

Version 0.1 does not aim to provide:

* A general-purpose Python wrapper around PDFBox.
* Direct access to `PDDocument`, `PDPage`, or `PDPageContentStream`.
* Arbitrary text drawing or page-layout APIs.
* PDF creation from scratch.
* OCR execution.
* Textract API calls.
* Textract response pagination.
* S3 downloading or uploading.
* hOCR generation.
* Transcript generation.
* Image rendering.
* PDF merging or splitting.
* Annotation manipulation.
* Form filling.
* Digital-signature preservation guarantees.
* Incremental PDF saving.
* In-place modification of the input file.
* Transparent proxy objects for arbitrary Java objects.

These features may be considered separately in later versions.

---

## 4. Architecture

The library consists of three layers:

```text
Python public API
    ↓
Private Python FFI adapter
    ↓
C-compatible native ABI
    ↓
GraalVM Native Image shared library
    ↓
Java transformation library
    ↓
Apache PDFBox
```

Responsibilities are divided as follows.

### 4.1 Python layer

The Python layer shall:

* Define the public data models.
* Validate Python-level argument types.
* Normalize OCR-provider-specific data into the library’s OCR model.
* Serialize requests for the native library.
* Load the platform-specific shared library.
* Create and manage the GraalVM isolate.
* Convert native failures into Python exceptions.
* Copy returned PDF data into Python-managed memory.
* Release native buffers.
* Expose the public high-level functions.

### 4.2 Java layer

The Java layer shall:

* Open and validate the PDF.
* Inspect page geometry.
* Interpret crop boxes and page rotation.
* Convert normalized OCR coordinates into PDF user-space coordinates.
* Select and embed the OCR font.
* Calculate text placement and fitting.
* Append invisible PDF text content.
* Save the modified PDF.
* Close all PDFBox resources.
* Convert exceptions into stable native error results.

### 4.3 Application layer

Code using `py-pdftools` shall remain responsible for:

* Calling Textract or another OCR provider.
* Parsing the provider response.
* Downloading the source PDF.
* Uploading the resulting PDF.
* Persisting job state.
* Generating hOCR.
* Generating plain-text transcripts.
* Invoking callbacks or webhooks.

---

## 5. Public Python API

Version 0.1 exposes two public functions:

```python
from py_pdftools import add_ocr_text_layer, inspect_pdf
```

### 5.1 `add_ocr_text_layer`

```python
def add_ocr_text_layer(
    pdf: bytes | bytearray | memoryview,
    ocr: OcrDocument,
    *,
    options: OcrTextLayerOptions | None = None,
) -> bytes:
    """Return a copy of a PDF with an invisible OCR text layer added."""
```

This is the primary public operation.

The function shall:

1. Validate the input OCR model.
2. Open the supplied PDF in the native Java library.
3. Add OCR text to the requested pages.
4. Save the complete modified PDF.
5. Close the PDF document.
6. Return the output as immutable Python `bytes`.

The input object shall not be modified.

### 5.2 `inspect_pdf`

```python
def inspect_pdf(
    pdf: bytes | bytearray | memoryview,
) -> PdfInfo:
    """Return basic page and geometry information about a PDF."""
```

This operation is intended for:

* Input validation.
* OCR pipeline diagnostics.
* Obtaining page dimensions before OCR.
* Comparing PDF pages with OCR result pages.
* Troubleshooting rotation and crop-box behavior.

It shall not modify the PDF.

---

## 6. Public data model

All public models shall be immutable dataclasses.

```python
from dataclasses import dataclass
from typing import Literal, TypeAlias
```

### 6.1 Orientation

```python
Orientation: TypeAlias = Literal[0, 90, 180, 270]
```

Orientation describes the OCR text direction in the visible, globally rotated
page coordinate system. That coordinate system has its origin at the visible
page's top-left: `0 = right`, `90 = down`, `180 = left`, and `270 = up`.

It does not describe the raw PDF page content-stream coordinate system.

### 6.2 Normalized rectangle

```python
@dataclass(frozen=True, slots=True)
class NormalizedRect:
    left: float
    top: float
    width: float
    height: float
```

Coordinates use the following convention:

```text
Origin: top-left of the visible page
X direction: right
Y direction: down
Units: normalized fractions of page width and height
```

The normal range is `0.0` through `1.0`.

For example:

```python
NormalizedRect(
    left=0.10,
    top=0.20,
    width=0.40,
    height=0.03,
)
```

describes a rectangle starting 10% from the left and 20% from the top, with a width of 40% and a height of 3% of the page.

### 6.3 OCR line

```python
@dataclass(frozen=True, slots=True)
class OcrLine:
    text: str
    bounds: NormalizedRect
    confidence: float | None = None
```

`text` is the complete text to place within the rectangle.

`confidence`, when provided, uses a range from `0.0` to `100.0`.

Version 0.1 places text at line granularity. It does not require word-level coordinates.

### 6.4 OCR page

```python
@dataclass(frozen=True, slots=True)
class OcrPage:
    page_index: int
    orientation: Orientation
    lines: tuple[OcrLine, ...]
```

`page_index` is zero-based.

For example:

```python
OcrPage(
    page_index=0,
    orientation=0,
    lines=(...),
)
```

refers to the first page.

OCR page records may be supplied in any order. Duplicate page indexes are invalid.

### 6.5 OCR document

```python
@dataclass(frozen=True, slots=True)
class OcrDocument:
    pages: tuple[OcrPage, ...]
```

The OCR model is deliberately independent of Textract.

Textract-specific code shall convert Textract blocks into this representation before calling `py-pdftools`.

### 6.6 Text-layer options

```python
@dataclass(frozen=True, slots=True)
class OcrTextLayerOptions:
    allow_partial_document: bool = False
    minimum_confidence: float | None = None
    debug_visible_text: bool = False
```

#### `allow_partial_document`

When `False`, the OCR document shall contain exactly one `OcrPage` for every PDF page.

When `True`, the OCR document may contain only a subset of PDF pages. Pages not present in the OCR document remain unchanged.

#### `minimum_confidence`

OCR lines with a lower confidence shall be skipped.

Lines without a confidence value shall not be skipped.

The value must be in the range `0.0` through `100.0`.

#### `debug_visible_text`

When `False`, inserted text shall be invisible.

When `True`, the implementation may render inserted text visibly for diagnostics.

Debug rendering characteristics are implementation-defined and shall not be relied upon by production code.

---

## 7. PDF inspection model

### 7.1 Page information

```python
@dataclass(frozen=True, slots=True)
class PdfPageInfo:
    page_index: int
    width_points: float
    height_points: float
    rotation: Orientation
    crop_box: PdfBox
    media_box: PdfBox
```

### 7.2 PDF box

```python
@dataclass(frozen=True, slots=True)
class PdfBox:
    lower_left_x: float
    lower_left_y: float
    upper_right_x: float
    upper_right_y: float
```

### 7.3 Document information

```python
@dataclass(frozen=True, slots=True)
class PdfInfo:
    page_count: int
    pages: tuple[PdfPageInfo, ...]
    encrypted: bool
```

`width_points` and `height_points` describe the visible crop box after applying page rotation.

One PDF point equals 1/72 inch.

---

## 8. Example usage

### 8.1 Adding an OCR layer

```python
from py_pdftools import (
    NormalizedRect,
    OcrDocument,
    OcrLine,
    OcrPage,
    add_ocr_text_layer,
)

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

output_pdf = add_ocr_text_layer(
    pdf=input_pdf,
    ocr=ocr,
)
```

### 8.2 Using a confidence threshold

```python
from py_pdftools import OcrTextLayerOptions, add_ocr_text_layer

output_pdf = add_ocr_text_layer(
    pdf=input_pdf,
    ocr=ocr,
    options=OcrTextLayerOptions(
        minimum_confidence=80.0,
    ),
)
```

### 8.3 Inspecting a PDF

```python
from py_pdftools import inspect_pdf

info = inspect_pdf(input_pdf)

for page in info.pages:
    print(
        page.page_index,
        page.width_points,
        page.height_points,
        page.rotation,
    )
```

---

## 9. Coordinate semantics

Correct coordinate behavior is a core library responsibility.

### 9.1 OCR coordinate space

OCR rectangles shall be interpreted relative to the visible page after applying:

* The page crop box.
* The page rotation value.

This coordinate space uses a top-left origin. OCR text direction uses
`0 = right`, `90 = down`, `180 = left`, and `270 = up`.

### 9.2 PDF coordinate space

PDF page content normally uses a bottom-left origin and may contain additional transformations.

The Java implementation shall convert OCR rectangles into the coordinate system required by PDFBox.

The Python caller shall not perform PDF derotation.

### 9.3 Rotation

The implementation shall support:

* 0 degrees
* 90 degrees
* 180 degrees
* 270 degrees

The final local text orientation shall be derived from:

1. OCR page orientation.
2. PDF page rotation.
3. The transformation between visible-page coordinates and PDF user space.

### 9.4 Crop box

OCR coordinates shall be relative to the crop box when a crop box is present.

The implementation shall not assume that the media box and crop box are identical.

### 9.5 Numeric tolerance

Normalized coordinates within `1e-6` of a valid boundary may be clamped.

Coordinates materially outside the valid range shall result in `InvalidOcrDataError`.

---

## 10. Text placement semantics

### 10.1 Granularity

Version 0.1 inserts one PDF text element for each `OcrLine`.

Word-level placement is reserved for a future version.

### 10.2 Invisible text

Normal output shall use the PDF text rendering mode that neither fills nor strokes glyphs.

The text shall:

* Remain searchable.
* Remain extractable.
* Not be visibly rendered.
* Not be represented as an annotation.
* Be appended to page content.

Opacity alone shall not be the primary invisibility mechanism.

### 10.3 Existing page content

The implementation shall append a new content stream.

It shall not rewrite, remove, flatten, or reorder existing page content unless required by PDFBox to save a valid document.

### 10.4 Text fitting

Each OCR line shall be fitted along its reading direction within the supplied OCR rectangle.

The implementation may use:

* Font-size selection.
* Horizontal text scaling.
* A PDF text matrix.
* A combination of these techniques.

The fitting algorithm shall prioritize:

1. Correct text order.
2. Search and selection usability.
3. Placement within the OCR rectangle.
4. Reasonable alignment with the visible source text.

Exact typographic reproduction is not required.

### 10.5 Text anchor

The Java implementation shall select the correct text origin based on:

* The transformed OCR rectangle.
* The OCR orientation.
* The PDF page rotation.
* Font ascent and descent metrics.

The Python caller shall not provide baseline coordinates.

### 10.6 Empty lines

Lines whose `text` value is empty shall be skipped.

Lines containing only whitespace may be skipped.

---

## 11. Fonts and character support

### 11.1 Default font

The native library shall include or otherwise provide access to a default embeddable font suitable for OCR text.

The default font shall provide broad Unicode coverage appropriate to the supported document corpus.

### 11.2 Embedding

The font shall be embedded or subset into the output PDF as required.

### 11.3 Missing glyphs

If a line contains characters unsupported by the selected font, the operation shall fail with `FontError`.

The implementation shall not silently replace unsupported characters with unrelated glyphs.

### 11.4 Text shaping

Version 0.1 guarantees ordinary left-to-right text placement.

Advanced shaping behavior for the following is not guaranteed:

* Bidirectional text.
* Arabic joining.
* Indic shaping.
* Complex combining sequences.
* Color emoji.

Support may be improved in later versions without changing the high-level API.

---

## 12. Input validation

The Python layer shall reject:

* Empty PDF byte sequences.
* Non-byte PDF input.
* Duplicate OCR page indexes.
* Negative page indexes.
* Unsupported orientation values.
* Non-finite numeric coordinates.
* Rectangles with non-positive width or height.
* Rectangles outside the normalized page range.
* Confidence values outside `0.0` through `100.0`.
* Invalid option combinations.

The Java layer shall independently validate all native inputs.

Python validation shall not be considered a security boundary.

---

## 13. Page matching

When `allow_partial_document` is `False`:

* OCR page count must equal PDF page count.
* Every PDF page index must occur exactly once.
* OCR page indexes must form the range `0` through `page_count - 1`.

When `allow_partial_document` is `True`:

* Every supplied OCR page index must exist in the PDF.
* Duplicate indexes remain invalid.
* Missing pages remain unchanged.

Extra OCR pages always result in an error.

---

## 14. Encrypted PDFs

Version 0.1 does not accept a password argument.

Behavior shall be:

* An unencrypted PDF is processed normally.
* An encrypted PDF that PDFBox can open without a password may be processed.
* A PDF requiring a password raises `PdfPasswordRequiredError`.
* A PDF whose permissions prohibit modification raises `PdfPermissionError`.

Password support may be added in a later version.

---

## 15. Output guarantees

On success, the output PDF shall:

* Be parseable by Apache PDFBox.
* Have the same page count as the input.
* Preserve page dimensions.
* Preserve page rotation values.
* Preserve the visible appearance of existing page content.
* Contain the inserted OCR text in text extraction results.
* Contain no visible OCR text when debug mode is disabled.

The output need not be byte-for-byte or object-for-object equivalent to the input.

The operation may alter:

* Object numbering.
* Compression.
* Cross-reference representation.
* Metadata modified timestamps.
* Resource ordering.
* Font resources.
* Internal serialization details.

---

## 16. Python exceptions

All public exceptions inherit from:

```python
class PdfToolsError(Exception):
    """Base exception for py-pdftools."""
```

The initial hierarchy is:

```python
class NativeLibraryError(PdfToolsError):
    pass


class InvalidPdfError(PdfToolsError):
    pass


class InvalidOcrDataError(PdfToolsError):
    pass


class PageMismatchError(InvalidOcrDataError):
    pass


class PdfPasswordRequiredError(InvalidPdfError):
    pass


class PdfPermissionError(InvalidPdfError):
    pass


class FontError(PdfToolsError):
    pass


class PdfProcessingError(PdfToolsError):
    pass
```

### 16.1 Exception meanings

`NativeLibraryError`
The shared library could not be found, loaded, initialized, or used.

`InvalidPdfError`
The input is not a valid or supported PDF.

`InvalidOcrDataError`
The OCR model is structurally or numerically invalid.

`PageMismatchError`
The OCR pages do not match the PDF pages under the selected policy.

`PdfPasswordRequiredError`
The document requires a password.

`PdfPermissionError`
The document’s security settings prevent modification.

`FontError`
Text cannot be represented using the configured OCR font.

`PdfProcessingError`
PDFBox failed while transforming or saving the document.

Native Java exception class names are diagnostic details and are not part of the public Python API.

---

## 17. Native ABI

The native ABI is private implementation detail.

It shall be versioned separately from the Python package.

The initial ABI should expose one-shot operations rather than Java object proxies.

Conceptual interface:

```c
uint32_t pdftools_abi_version(void);

pdftools_status_t pdftools_inspect_pdf(
    graal_isolatethread_t *thread,
    const uint8_t *pdf_data,
    size_t pdf_length,
    pdftools_buffer_t *out_result
);

pdftools_status_t pdftools_add_ocr_text_layer(
    graal_isolatethread_t *thread,
    const uint8_t *pdf_data,
    size_t pdf_length,
    const uint8_t *request_data,
    size_t request_length,
    pdftools_buffer_t *out_pdf
);

void pdftools_free_buffer(
    graal_isolatethread_t *thread,
    void *buffer
);

pdftools_status_t pdftools_last_error(
    graal_isolatethread_t *thread,
    char *buffer,
    size_t capacity,
    size_t *required_size
);
```

A document-handle API is not required for version 0.1.

The Java implementation shall open, process, save, and close the PDF during one native call.

This design reduces:

* Native resource leaks.
* Handle invalidation problems.
* Cross-language lifecycle complexity.
* Python finalizer dependence.
* Thread-sharing concerns.

A handle-based API may be added later if multiple transformations on the same open document become necessary.

---

## 18. Native request format

The Python OCR model shall be serialized into a private versioned request.

A binary format such as CBOR is recommended.

Conceptual request:

```json
{
  "schemaVersion": 1,
  "options": {
    "allowPartialDocument": false,
    "minimumConfidence": null,
    "debugVisibleText": false
  },
  "pages": [
    {
      "pageIndex": 0,
      "orientation": 0,
      "lines": [
        {
          "text": "Example OCR text",
          "confidence": 98.4,
          "bounds": {
            "left": 0.1,
            "top": 0.2,
            "width": 0.4,
            "height": 0.03
          }
        }
      ]
    }
  ]
}
```

The request format is private and may change between compatible Python and native-library builds.

Python package and shared-library versions must be distributed together.

---

## 19. Runtime lifecycle

The Python package shall lazily initialize the native runtime on the first operation.

The normal process lifecycle is:

```text
Import package
    ↓
First PDF operation
    ↓
Load shared library
    ↓
Create one GraalVM isolate
    ↓
Execute any number of one-shot operations
    ↓
Interpreter shutdown
    ↓
Destroy isolate
```

The package shall not create one isolate for each PDF.

The package shall register an interpreter-shutdown cleanup handler.

Explicit public isolate management is not part of version 0.1.

---

## 20. Threading

The implementation shall be safe for normal use from multiple Python threads.

Version 0.1 may serialize native calls with a process-local lock.

Correctness takes priority over concurrent throughput.

The library does not guarantee that one operation internally uses multiple CPU cores.

The native isolate and isolate-thread rules shall remain private implementation details.

---

## 21. Resource limits and security

PDFs and OCR data shall be treated as untrusted input.

The implementation should support configurable internal limits for:

* Maximum PDF byte size.
* Maximum output byte size.
* Maximum page count.
* Maximum OCR lines per page.
* Maximum total OCR line count.
* Maximum text length per line.
* Maximum aggregate OCR text size.
* Maximum native allocation size.

The Java implementation shall:

* Close every `PDDocument`.
* Close every content stream.
* Free every unmanaged output allocation.
* Avoid network access.
* Avoid loading classes dynamically from user input.
* Avoid writing temporary files outside configured temporary storage.
* Avoid exposing arbitrary file paths through the public Python API.

Limit configuration may initially be internal rather than public.

---

## 22. Performance requirements

Version 0.1 should meet the following targets on representative infrastructure:

* Native runtime initialization occurs once per Python process.
* A 100-page PDF with approximately 100 OCR lines per page completes without unbounded memory growth.
* Native memory is released after each call.
* Processing time scales approximately linearly with page and OCR-line count.
* The Python/native boundary is crossed once per transformation, not once per OCR line.
* The complete OCR document is transferred in one versioned request.

The output PDF may be held fully in memory in version 0.1.

Streaming input and output are deferred.

---

## 23. Packaging

The project shall use:

* Gradle for Java compilation and Native Image production.
* Standard Python packaging through `pyproject.toml`.
* A Python build backend such as Hatchling.
* Platform-specific wheels containing the native shared library.

Example wheel names:

```text
tindtechnologies_py_pdftools-0.1.0-py3-none-manylinux_2_28_x86_64.whl
tindtechnologies_py_pdftools-0.1.0-py3-none-manylinux_2_28_aarch64.whl
tindtechnologies_py_pdftools-0.1.0-py3-none-macosx_11_0_arm64.whl
tindtechnologies_py_pdftools-0.1.0-py3-none-macosx_10_15_x86_64.whl
tindtechnologies_py_pdftools-0.1.0-py3-none-win_amd64.whl
```

The installed package shall not require:

* Java.
* A JVM.
* GraalVM.
* Gradle.
* Maven.

These are build-time dependencies only.

---

## 24. Supported environments

Initial target environments:

* Python 3.10 or newer.
* Linux x86-64.
* Linux ARM64.
* macOS x86-64.
* macOS ARM64.
* Windows x86-64.

Each platform artifact shall be built on or for its target platform.

Cross-platform binary compatibility shall not be assumed.

---

## 25. Typing and documentation

The package shall:

* Include complete type annotations.
* Include a `py.typed` marker.
* Document all public classes and functions.
* Keep native implementation classes private.
* Keep FFI functions private.
* Provide examples for Textract normalization without importing boto3 into the core package.

The following shall be treated as public API:

```python
add_ocr_text_layer
inspect_pdf

NormalizedRect
OcrLine
OcrPage
OcrDocument
OcrTextLayerOptions

PdfBox
PdfPageInfo
PdfInfo

PdfToolsError
NativeLibraryError
InvalidPdfError
InvalidOcrDataError
PageMismatchError
PdfPasswordRequiredError
PdfPermissionError
FontError
PdfProcessingError
```

Everything else should be private unless explicitly documented.

---

## 26. Textract adapter

Textract integration should live outside the core package or in an optional adapter module:

```python
from py_pdftools.adapters.textract import ocr_document_from_textract
```

Conceptual signature:

```python
def ocr_document_from_textract(
    response: Mapping[str, object],
) -> OcrDocument:
    ...
```

The adapter shall:

* Group `LINE` blocks by page.
* Convert one-based Textract page numbers to zero-based indexes.
* Preserve line text.
* Preserve confidence.
* Preserve normalized bounding boxes.
* Determine page orientation from word positions.
* Return provider-independent `OcrDocument` data.

The core native library shall never receive a raw Textract response.

---

## 27. Testing requirements

### 27.1 Java unit tests

Java tests shall cover:

* PDF opening and closing.
* Page geometry inspection.
* Crop-box handling.
* Coordinate transformation.
* All four page rotations.
* Text fitting.
* Invisible rendering mode.
* Font embedding.
* Missing glyph behavior.
* Invalid page indexes.
* Save and reopen behavior.

### 27.2 Native ABI tests

A C-level smoke test shall:

1. Create a GraalVM isolate.
2. Load a fixture PDF.
3. Submit an OCR request.
4. Receive the output buffer.
5. Save or inspect the returned PDF.
6. Free the native buffer.
7. Tear down the isolate.

### 27.3 Python tests

Python tests shall cover:

* Public model validation.
* Shared-library discovery.
* Native initialization.
* Exception mapping.
* Byte input types.
* Multipage OCR.
* Partial-document mode.
* Confidence filtering.
* Unicode serialization.
* Repeated calls in one process.
* Calls from multiple Python threads.
* Cleanup at interpreter shutdown.

### 27.4 End-to-end fixtures

The test corpus shall include:

* A normal portrait PDF.
* A multipage PDF.
* A page rotated 90 degrees.
* A page rotated 180 degrees.
* A page rotated 270 degrees.
* A sideways scanned page.
* A PDF whose crop box differs from its media box.
* A document containing non-ASCII text.
* A malformed PDF.
* An encrypted PDF.
* A PDF with existing selectable text.

The existing multipage, rotated, and sideways fixture categories should be preserved from the current implementation.

---

## 28. Acceptance criteria

Version 0.1 is accepted when all of the following are true.

### Functional

* `add_ocr_text_layer` returns a valid PDF.
* Extracted text includes the supplied OCR lines.
* OCR text is correctly associated with its page.
* OCR text is positioned in the expected region.
* Rotated pages produce correctly oriented text.
* Crop-box pages use visible-page coordinates correctly.
* The page count is unchanged.
* Page dimensions are unchanged.
* Page rotation values are unchanged.

### Visual

With `debug_visible_text=False`:

* Rendering the input and output pages produces no material visible difference.
* The OCR layer is not visible at normal or high zoom.
* No debug rectangles or annotations appear.

### Operational

* No JVM is required.
* Native buffers are released.
* PDFs are closed after every operation.
* Repeated transformations do not produce continuous native-memory growth.
* Python wheels install and run on all supported target platforms.

### Compatibility

* The Python package verifies the native ABI version at initialization.
* An ABI mismatch produces a clear `NativeLibraryError`.
* Public Python data models remain backward-compatible within the same major version.

---

## 29. Versioning

The Python package follows semantic versioning.

```text
MAJOR.MINOR.PATCH
```

The project also maintains:

* A native ABI version.
* A native request-schema version.

These versions are distinct.

A Python package shall refuse to use a native library with an incompatible ABI.

The package wheel shall contain the exact native library version it was built and tested against.

---

## 30. Future extensions

Possible later additions include:

```python
add_word_level_ocr_text_layer(...)
remove_ocr_text_layer(...)
has_searchable_text(...)
extract_text(...)
merge_pdfs(...)
split_pdf(...)
add_metadata(...)
optimize_pdf(...)
validate_pdf(...)
```

A future document-session API may provide:

```python
with PdfDocument.open(pdf_bytes) as document:
    document.add_ocr_text_layer(ocr)
    document.add_metadata(metadata)
    output = document.save()
```

Such an API should only be introduced when multiple operations on one open document are required.

The one-shot high-level functions shall remain the preferred interface for simple transformations.

---

## 31. Summary

The initial public surface is intentionally small:

```python
output_pdf = add_ocr_text_layer(
    pdf=input_pdf,
    ocr=ocr_document,
)

info = inspect_pdf(input_pdf)
```

All PDFBox-specific operations remain internal.

The principal abstraction is not “a Python binding to PDFBox.” It is:

```text
A high-level PDF transformation service embedded in a Python package.
```
