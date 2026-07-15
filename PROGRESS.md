# Progress

This file records implementation milestones, verification results, and decisions that affect later work.

## Current milestone

### M1 — Python public contract

Status: in progress

- [ ] Add Python packaging metadata and a `src`-layout package.
- [ ] Implement the public exception hierarchy.
- [ ] Implement immutable public data models and Python-side validation.
- [ ] Export the documented public contract.
- [ ] Add dependency-free unit tests and run them on the available Python interpreter.

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
