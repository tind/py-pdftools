# Progress

This file records implementation milestones, verification results, and decisions that affect later work.

## Current milestone

### M2 — Native protocol boundary

Status: complete

- [x] Define native ABI, request-schema, and inspection-schema version 1.
- [x] Serialize complete OCR requests as deterministic, dependency-free UTF-8 JSON.
- [x] Validate and deserialize native PDF inspection responses.
- [x] Define stable native status values and public exception mapping.
- [x] Discover and bind the platform-specific native library.
- [x] Lazily initialize one native runtime and verify its ABI version.
- [x] Serialize native calls across Python threads and release runtime resources.
- [x] Connect the public operations to the native adapter and test with fakes.

The JSON protocol is private and shipped in lockstep with the native library.
It was chosen over CBOR for the first release to keep the Python core
dependency-free and avoid adding a binary codec to both language layers.

Verification:

- `PYTHONPATH=src python3 -m unittest discover -v` — 51 tests passed on Python 3.14.6.
- `python3 -m compileall -q src tests` — passed.
- `git diff --check` — passed.

The low-level adapter is tested against a fake C library, including buffer
copy/free behavior, native error retrieval, status mapping, and GraalVM thread
attach/detach behavior. Runtime tests cover lazy reuse, ABI mismatch, shutdown,
and serialized access from eight Python threads.

No native library is bundled yet. Public calls now reach the complete Python
boundary and then report `NativeLibraryError` until M4 supplies the GraalVM
artifact.

## Completed milestones

### M1 — Python public contract

Status: complete

- [x] Add Python packaging metadata and a `src`-layout package.
- [x] Implement the public exception hierarchy.
- [x] Implement immutable public data models and Python-side validation.
- [x] Export the documented public contract.
- [x] Add dependency-free unit tests and run them on the available Python interpreter.

Verification:

- `PYTHONPATH=src python3 -m unittest discover -v` — 26 tests passed on Python 3.14.6.
- `python3 -m compileall -q src tests` — passed.
- `git diff --check` — passed.

At the M1 checkpoint, public operations stopped after validation; M2 replaced
that temporary seam with native serialization and dispatch.

## Planned milestones

1. **M1 — Python public contract:** models, validation, exceptions, packaging, and tests.
2. **M2 — Native protocol boundary:** versioned serialization, FFI adapter contract, library discovery, and test doubles.
3. **M3 — Java PDF inspection:** Gradle project, PDFBox integration, geometry inspection, and Java tests.
4. **M4 — Native inspection path:** GraalVM ABI implementation and Python `inspect_pdf` end to end.
5. **M5 — OCR text transformation:** coordinate conversion, fitting, invisible text, fonts, and fixtures.
6. **M6 — Distribution:** native builds, platform wheels, CI, documentation, and release checks.

## Environment baseline

- Repository began with `SPECS.md` only; commit `25c8829` is the baseline specification.
- Python 3.14.6 is available.
- Java and Gradle are available.
- GraalVM `native-image` is not currently available.
- No Python test/build tools are installed globally; M1 tests will use `unittest`.

## Decisions

- Work proceeds in independently testable milestones, with a progress update and commit at each useful checkpoint.
- Native operations will not be represented as working until an end-to-end implementation exists.

## Log

- 2026-07-15: Read the complete specification and selected the Python public contract as the first milestone.
- 2026-07-15: Completed M1 with package metadata, public models, validation, exceptions, API exports, typing marker, and tests.
- 2026-07-15: Defined the M2 versioned JSON protocol and native status mapping; protocol tests also caught and fixed float drift in normalized rectangles.
- 2026-07-15: Completed M2 with native library discovery, ctypes bindings, lazy ABI-checked lifecycle management, thread serialization, public dispatch, and 51 passing tests.
