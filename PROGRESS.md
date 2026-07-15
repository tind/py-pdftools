# Progress

This file records implementation milestones, verification results, and decisions that affect later work.

## Current milestone

### M2 — Native protocol boundary

Status: in progress

- [x] Define native ABI, request-schema, and inspection-schema version 1.
- [x] Serialize complete OCR requests as deterministic, dependency-free UTF-8 JSON.
- [x] Validate and deserialize native PDF inspection responses.
- [x] Define stable native status values and public exception mapping.
- [ ] Discover and bind the platform-specific native library.
- [ ] Lazily initialize one native runtime and verify its ABI version.
- [ ] Serialize native calls across Python threads and release runtime resources.
- [ ] Connect the public operations to the native adapter and test with fakes.

The JSON protocol is private and shipped in lockstep with the native library.
It was chosen over CBOR for the first release to keep the Python core
dependency-free and avoid adding a binary codec to both language layers.

Protocol checkpoint verification:

- `PYTHONPATH=src python3 -m unittest discover -v` — 36 tests passed on Python 3.14.6.
- `python3 -m compileall -q src tests` — passed.
- `git diff --check` — passed.

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

The public operations currently validate their Python arguments and raise a
clear `NativeLibraryError`; actual native dispatch is deliberately deferred to
M2 rather than represented as operational.

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
