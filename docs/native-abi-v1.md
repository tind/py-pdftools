# Native ABI version 1

This document fixes the private contract between the Python package and the
GraalVM Native Image shared library shipped with the same wheel. It is not a
public extension API.

## Platform types and symbols

The ABI uses the platform C ABI, `size_t` for lengths, and `uint32_t` for
versions and operation statuses. The library exports the GraalVM isolate
lifecycle symbols in addition to these project symbols:

```c
typedef uint32_t pdftools_status_t;

typedef struct {
    void *data;
    size_t length;
} pdftools_buffer_t;

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

`pdftools_abi_version()` returns `1` for this contract.

## Status values

| Value | Name | Python exception |
| ---: | --- | --- |
| 0 | success | none |
| 1 | invalid PDF | `InvalidPdfError` |
| 2 | invalid OCR data | `InvalidOcrDataError` |
| 3 | page mismatch | `PageMismatchError` |
| 4 | PDF password required | `PdfPasswordRequiredError` |
| 5 | PDF permission denied | `PdfPermissionError` |
| 6 | font error | `FontError` |
| 7 | PDF processing error | `PdfProcessingError` |

Unknown nonzero values map to `NativeLibraryError`.

## Buffers and errors

Input buffers remain owned by Python and are valid only for the duration of a
call. On success, an operation returns an allocated output buffer. Python
copies that buffer immediately and calls `pdftools_free_buffer()` exactly once.
Python does not call `pdftools_free_buffer()` for a null pointer.

On failure, the operation returns a nonzero status and stores a thread-local
UTF-8 diagnostic. Python queries it by calling `pdftools_last_error()` first
with a null buffer and zero capacity. `required_size` includes the terminating
null byte. A second call copies the null-terminated message. Error messages are
limited to 1 MiB by the Python adapter.

## Request schema version 1

The OCR request is compact UTF-8 JSON. All finite numbers are JSON numbers;
missing confidence values are JSON `null`.

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

The native implementation independently validates every field and rejects
unknown schema versions with status 2.

## Inspection response schema version 1

`pdftools_inspect_pdf()` returns compact UTF-8 JSON with this shape:

```json
{
  "schemaVersion": 1,
  "pageCount": 1,
  "encrypted": false,
  "pages": [
    {
      "pageIndex": 0,
      "widthPoints": 612.0,
      "heightPoints": 792.0,
      "rotation": 0,
      "cropBox": {
        "lowerLeftX": 0.0,
        "lowerLeftY": 0.0,
        "upperRightX": 612.0,
        "upperRightY": 792.0
      },
      "mediaBox": {
        "lowerLeftX": 0.0,
        "lowerLeftY": 0.0,
        "upperRightX": 612.0,
        "upperRightY": 792.0
      }
    }
  ]
}
```

Pages are ordered by `pageIndex`, starting at zero. Visible width and height
reflect the crop box after page rotation.

## Lifecycle and threading

Python loads one library and lazily creates one isolate per process. Native
operations are serialized with a process-local reentrant lock. Calls made from
an OS thread other than the isolate creator attach that thread before the call
and detach it afterward. Interpreter shutdown tears down the isolate.

The packaged filenames are `libpy_pdftools.so`, `libpy_pdftools.dylib`, and
`py_pdftools.dll`. `PY_PDFTOOLS_NATIVE_LIBRARY` is a private development and
testing override for loading a specific artifact.
