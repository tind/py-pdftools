# Progress

This file records implementation milestones, verification results, and decisions that affect later work.

## Latest milestone

### M9 — Orientation and Unicode fidelity

Status: complete

- [x] Make the public visible-page orientation contract explicit.
- [x] Verify 90/270-degree glyph order and placement across every page rotation.
- [x] Prevent font ligature substitution from changing copied Unicode text.
- [x] Verify exact extraction with PDFBox and an independent extractor.
- [x] Re-run the complete JVM, native, and Python regression suites.
- [x] Publish and verify the 0.1.2 platform wheels.

Orientation checkpoint:

- Public model documentation, README usage, the specification, native ABI
  documentation, and the Textract normalization example now define the same
  visible-page top-left contract: 0 is right, 90 down, 180 left, and 270 up.
- End-to-end PDFBox tests cover orientations 90 and 270 for page rotations 0,
  90, 180, and 270 with a non-origin crop box. They assert supplied selection
  order, first/last characters, cross-axis alignment, and the expected
  first-to-last vertical direction.
- `./gradlew java:test --tests dev.pypdftools.ocr.OcrTextLayerTransformerTest
  --tests dev.pypdftools.ocr.PageCoordinateMapperTest` — passed.

Unicode checkpoint:

- PDFBox 3.0.8 applied Noto Sans GSUB ligatures when a complete line was passed
  to `showText`, producing U+FB00/U+FB03 `ToUnicode` entries. Its own extractor
  compatibility-normalized those entries, while pypdf exposed the incorrect
  underlying mappings.
- OCR text is now emitted as code-point strings in one continuous `TJ`
  operation. This prevents cross-character substitutions while retaining the
  existing font metrics, fitted placement, selection order, and rendering mode.
- PDFBox verifies both normalized extraction and the underlying per-glyph
  Unicode sequence. A native end-to-end test uses pypdf 6 as the independent
  extractor and requires the exact source string.
- `./gradlew java:test` — complete Java suite passed.
- `./gradlew java:nativeSmoke -PgraalVmHome=/opt/homebrew/opt/graalvm` — native
  image rebuilt and the C ABI smoke test passed.
- `PYTHONPATH=src PY_PDFTOOLS_NATIVE_LIBRARY=java/build/native/libpy_pdftools.dylib
  ./venv/bin/python -m unittest discover -v` — 63 Python tests passed.

Public release checkpoint:

- Pre-tag workflow run
  [`29488041481`](https://github.com/tind/py-pdftools/actions/runs/29488041481)
  passed the complete five-platform matrix at commit `23991d1`.
- Published GitHub release
  [`v0.1.2`](https://github.com/tind/py-pdftools/releases/tag/v0.1.2) from an
  annotated tag on that exact green commit.
- Release workflow run
  [`29488617565`](https://github.com/tind/py-pdftools/actions/runs/29488617565)
  rebuilt all five wheels, passed native/Python and installed-wheel tests on
  every target, validated the exact artifact set, and published through PyPI
  Trusted Publishing.
- Public PyPI metadata lists exactly the five expected 0.1.2 wheels. After
  Simple Index propagation, a clean environment installed the macOS ARM64
  wheel and passed without Java, GraalVM, source paths, or native overrides.
- The public wheel independently round-tripped
  `effect office affinity efficient` exactly with pypdf 6.

## Completed milestones

### M8 — PyPI description correction

Status: complete

- [x] Replace transitional pre-release wording with durable installation text.
- [x] Correct the distribution name in the specification's wheel examples.
- [x] Reject transitional release wording in packaged wheel metadata.
- [x] Set the patch version to 0.1.1.
- [x] Pass the complete five-platform wheel matrix for the release commit.
- [x] Publish `v0.1.1` through the protected Trusted Publishing workflow.
- [x] Verify the corrected public metadata and a fresh PyPI installation.

This is a metadata-only patch release. The Python, Java, and native runtime
sources are unchanged from 0.1.0. Version 0.1.0 remains functional and
installable, so it will not be yanked or deleted.

Release preparation checkpoint:

- `PYTHONPATH=src ./venv/bin/python -m unittest discover -v` — 62 tests passed,
  including the new stale-description release check; five native-only tests
  skipped in the source-path run.
- Built
  `tindtechnologies_py_pdftools-0.1.1-py3-none-macosx_11_0_arm64.whl` locally.
  Its metadata reports version 0.1.1 and Apache-2.0, includes the durable
  README description, and contains none of the rejected transitional phrases.
- Installed the local 0.1.1 wheel into a clean environment and passed
  `tools/installed_wheel_smoke.py` with Java, GraalVM, source paths, and native
  library overrides removed.

Public patch-release checkpoint:

- Pre-tag workflow run
  [`29419203961`](https://github.com/tind/py-pdftools/actions/runs/29419203961)
  passed the complete five-platform matrix at commit `0ce602a`.
- Published GitHub release
  [`v0.1.1`](https://github.com/tind/py-pdftools/releases/tag/v0.1.1) from an
  annotated tag on that exact green commit. No duplicate tag-push matrix ran.
- Release workflow run
  [`29419828882`](https://github.com/tind/py-pdftools/actions/runs/29419828882)
  built all five wheels, rejected stale-description metadata during exact
  artifact validation, passed protected-environment review, and published via
  PyPI Trusted Publishing.
- PyPI now reports 0.1.1 as the latest version and renders the durable
  self-contained-wheel installation description. The public release contains
  exactly the five expected non-yanked wheels, with Apache-2.0 metadata and
  `Requires-Python: >=3.10`; the five functional 0.1.0 wheels remain available
  and non-yanked.
- After the Simple Index finished propagating, a fresh macOS ARM64 environment
  installed `tindtechnologies-py-pdftools==0.1.1` from public PyPI and selected
  the expected `py3-none-macosx_11_0_arm64` wheel. The installed-wheel smoke
  test passed without Java, GraalVM, source paths, or native overrides.

### M7 — Public release

Status: complete

- [x] Select the project and distribution licenses.
- [x] Select the public PyPI distribution name.
- [x] Add a tag-checked Trusted Publishing workflow.
- [x] Verify release metadata in a built wheel.
- [x] Document and hand off the one-time release configuration.
- [x] Configure the maintainer-controlled GitHub environment and pending PyPI
  publisher.
- [x] Publish and verify the first public release.

Licensing and naming checkpoint:

- Project code uses the Apache License 2.0, matching Apache PDFBox.
- The distribution archive records Apache-2.0 metadata and includes the
  project license, third-party notices, Apache PDFBox's complete bundled
  license text, and the SIL Open Font License for the embedded Noto font.
- The public distribution is `tindtechnologies-py-pdftools`; the import
  package remains `py_pdftools`.
- Built
  `tindtechnologies_py_pdftools-0.1.0-py3-none-macosx_11_0_arm64.whl`.
  Its metadata reports the requested name, Apache-2.0 expression, all four
  legal files, a platform-specific `py3-none` tag, and
  `Root-Is-Purelib: false`.
- Installed that wheel into a clean environment and passed
  `tools/installed_wheel_smoke.py` without Java or build-path overrides.

Release automation checkpoint:

- Made the five-platform wheel workflow reusable by the release workflow.
- Added a dependency-free release validator that requires an exact
  `v<project-version>` tag, exactly one wheel for every supported platform,
  matching wheel metadata/tags, and the required legal files.
- Added a stable-release-only workflow that builds the wheels, validates them
  without OIDC authority, then gates a two-step Trusted Publishing job on the
  protected `pypi` environment.
- Initial post-push run `29414781349` built the renamed wheel on all five
  targets and passed every existing native/Python test, but Python 3.10 could
  not import the new release test because `tomllib` was added in Python 3.11.
  The validator now uses the `tomli` compatibility package on Python 3.10,
  declared only for tests/build tooling; runtime dependencies remain empty.
- Follow-up run `29415240601` passed the complete pipeline on all five targets:
  native tests, the Python 3.10 suite including release checks, clean wheel
  installation, installed-wheel smoke, and artifact upload.
- Downloaded and merged the five wheels from that run, then ran
  `tools/check_release_artifacts.py --tag v0.1.0`; it validated the exact
  distribution names, platform set, metadata, tags, and legal files.
- GitHub registers both `Platform wheels` and `Publish release to PyPI` as
  active workflows. The publish workflow did not run on ordinary pushes.
- `PYTHONPATH=src ./venv/bin/python -m unittest discover -v` — 61 tests passed,
  including four release-validation tests; five native-only tests skipped in
  this source-path run. Both workflow files also parse as YAML.

Release hand-off checkpoint:

- Before maintainer setup, confirmed the public repository identity as
  `tind/py-pdftools` and the absence of an existing GitHub environment.
- Before publication, confirmed `tindtechnologies-py-pdftools` was unclaimed on
  PyPI; the pending publisher claimed it during the first trusted upload.
- Documented the exact GitHub `pypi` environment and PyPI pending-publisher
  fields, stable tag/release flow, post-publication checks, and immutable-file
  recovery rule.
- Verified the maintainer-created `pypi` environment: deployments are limited
  to tags matching `v*`, publishing requires review by `thms-rmb`, and the
  reviewer may approve their own deployment. The pending PyPI publisher was
  configured by the maintainer for this environment and release workflow.
- Limited direct `Platform wheels` push runs to branches. A published release
  still calls the reusable workflow at its tag, but pushing the tag itself no
  longer starts a duplicate five-platform build.

Public release checkpoint:

- Final pre-tag workflow run
  [`29417273988`](https://github.com/tind/py-pdftools/actions/runs/29417273988)
  passed the complete five-platform matrix at commit `43f8783`.
- Published GitHub release
  [`v0.1.0`](https://github.com/tind/py-pdftools/releases/tag/v0.1.0) from an
  annotated tag on that commit. The tag push did not start a duplicate matrix.
- Release workflow run
  [`29417877615`](https://github.com/tind/py-pdftools/actions/runs/29417877615)
  built all five wheels, validated the exact tag and artifact set, passed the
  protected-environment review, and published with PyPI Trusted Publishing.
- The public
  [`tindtechnologies-py-pdftools 0.1.0`](https://pypi.org/project/tindtechnologies-py-pdftools/0.1.0/)
  release lists exactly the expected macOS x86-64/ARM64, manylinux 2.28
  x86-64/ARM64, and Windows x86-64 wheels. All are non-yanked; no source
  archive or unexpected file was published.
- Public metadata reports version `0.1.0`, `Requires-Python: >=3.10`, and the
  Apache-2.0 license expression.
- Installed `tindtechnologies-py-pdftools==0.1.0` from the public PyPI index in
  a fresh temporary environment on macOS ARM64. The index selected the correct
  `py3-none-macosx_11_0_arm64` wheel, and
  `tools/installed_wheel_smoke.py` passed with Java, GraalVM, source paths, and
  native-library overrides removed.

Post-publication metadata follow-up:

- The 0.1.0 files and runtime are correct, but the README embedded in that
  immutable release retains its pre-publication status paragraph. PyPI stores
  metadata from the first file uploaded for a version, so the description
  cannot be corrected in place.
- Version 0.1.1 resolved the public description while retaining 0.1.0 as a
  functional, non-yanked release. Main uses durable, version-independent
  installation wording, and release validation now rejects the transitional
  phrases before publication.

### M6 — Distribution

Status: complete

- [x] Stage the native library and its runtime dependencies into the Python
  package.
- [x] Build and inspect a platform-specific wheel.
- [x] Test an installed wheel without Java or repository-path overrides.
- [x] Add supported-platform CI build and test jobs.
- [x] Complete public usage, build, and release documentation.

macOS ARM64 wheel checkpoint:

- Added a Hatch build hook that invokes `:java:nativeCompile`, stages every
  runtime shared library, marks the wheel as platform-specific, and uses a
  Python-ABI-independent `py3-none-<platform>` tag.
- The macOS build targets 11.0 for ARM64 (10.15 for x86-64). Native Image's
  generated `libjava` and `libjvm` forwarding shims are safely normalized to
  the same deployment target in the staged copy and ad-hoc re-signed.
- Built `py_pdftools-0.1.0-py3-none-macosx_11_0_arm64.whl`. Inspection shows
  `Root-Is-Purelib: false`, all ten required dylibs, valid signatures, and an
  11.0 minimum on every bundled binary.
- Installed the wheel into a clean `/tmp` venv and ran
  `tools/installed_wheel_smoke.py` with Java/GraalVM and all development
  overrides removed from the environment — passed.

Cross-platform CI checkpoint:

- Added a wheel workflow for manylinux 2.28 x86-64/ARM64, macOS 11 ARM64,
  macOS 10.15 x86-64, and Windows x86-64.
- Each job runs JVM tests, builds Native Image and the wheel, exercises the
  real Python/native path, installs the wheel, and runs the installed-wheel
  smoke test without build-tool environment variables.
- Linux jobs build inside the matching PyPA manylinux containers, and all jobs
  upload their platform wheel for later release assembly.
- Initial GitHub Actions run `29410678210` proved the macOS ARM64 wheel and its
  installed-wheel smoke test, while macOS Intel passed native tests. Both
  Linux architectures built wheels but exposed the same missing
  `java.awt.GraphicsEnvironment` JNI reachability registration during the
  native smoke test.
- Added the Linux-specific AWT JNI registration and extended the JVM metadata
  probe. `./gradlew --offline :java:test` and the macOS ARM64 native C smoke
  test both pass with the updated metadata. A follow-up matrix run is required
  before the milestone is complete.
- Follow-up run `29411158015` exposed a Windows launcher issue after its JVM
  tests: Gradle attempted to start the extensionless POSIX Native Image
  command. The native build now selects `native-image.cmd` and invokes it via
  `cmd /c` on Windows while retaining direct execution on POSIX.
- The same run completed both macOS wheels and installed-wheel smoke tests.
  Linux advanced past the class lookup, then exposed the paired
  `GraphicsEnvironment.isHeadless()` JNI method lookup used by OpenJDK's AWT
  loader; that static method is now registered too.
- The POSIX launcher refactor and complete AWT registration pass a forced
  macOS ARM64 native rebuild and C smoke test. Another matrix run is needed to
  verify the Windows and Linux corrections.
- Third run `29411644929` verified the complete Windows wheel pipeline,
  including native/Python tests and the installed-wheel smoke test. Linux
  advanced through AWT headless detection and exposed OpenJDK's final loader
  call, `System.load(String)`, which is now also registered for JNI access.
- GitHub Actions run `29412126420` passed all five supported wheel jobs:
  manylinux 2.28 x86-64/ARM64, macOS 11 ARM64, macOS 10.15 x86-64, and Windows
  x86-64. Every job built a wheel, exercised the real native/Python path,
  installed the wheel, ran without build tools, and uploaded its artifact.
- Updated artifact uploads from `actions/upload-artifact@v4` to current v7 to
  avoid the retired Node 20 action runtime. Disabled the ineffective Gradle
  cache integration inside manylinux job containers, where it emitted an
  empty-path warning, and on Windows, where Git's `tar.exe` failed while
  saving; neither cache produced a reusable entry.

Documentation checkpoint:

- Expanded the README with the pre-release status, runtime requirements,
  complete OCR and inspection examples, coordinate semantics, options,
  behavior, limits, and exception guidance.
- Added reproducible local build/test/wheel instructions, including use of the
  Homebrew GraalVM prefix without changing global `PATH`.
- Added a dependency-free Textract mapping example and a release checklist
  that separates local verification from maintainer-controlled GitHub/PyPI
  configuration.

### M5 — OCR text transformation

Status: complete

- [x] Validate the OCR request independently in Java.
- [x] Convert normalized OCR rectangles into PDF page coordinates.
- [x] Fit and write invisible Unicode text without changing visible content.
- [x] Preserve metadata, page geometry, and existing page content.
- [x] Exercise transformation through the native ABI and Python API.

Request-schema checkpoint:

- Added a dependency-free strict JSON and UTF-8 decoder for private request
  schema version 1.
- Java independently validates field types, orientations, confidence values,
  normalized bounds, duplicate page indexes, document matching, and internal
  request-size limits.
- `./gradlew --offline :java:test` — 28 Java tests passed.

Coordinate-mapping checkpoint:

- OCR rectangles now map from the visible, crop-relative top-left coordinate
  space into an oriented PDF user-space basis.
- Mapping covers offset crop boxes, PDF rotations 0/90/180/270, and OCR
  orientations 0/90/180/270 without Python-side derotation.
- `./gradlew --offline :java:test --tests
  dev.pypdftools.ocr.PageCoordinateMapperTest` — 9 focused tests passed.

PDFBox transformation checkpoint:

- Bundled the static Noto Sans Regular font at a pinned upstream revision with
  its SIL Open Font License and SHA-256 checksum.
- The transformer filters blank and low-confidence lines, fits eligible text
  to the OCR reading direction, appends a new content stream, and uses PDF
  rendering mode 3 for normal invisible output.
- The font is subset and embedded. Latin, Greek, and Cyrillic text round-trips
  through PDFBox extraction; unsupported glyphs fail with `FontException`.
- Tests reopen transformed files and verify document information, page count,
  media/crop boxes, page rotation, existing text, rendering modes, page
  matching, encryption permissions, and every page-rotation/OCR-orientation
  combination.
- `./gradlew --offline :java:test` — 61 Java tests passed.

Native OCR checkpoint:

- Connected the one-shot OCR operation to the exported C entry point with
  stable status mapping for invalid requests/PDFs, page mismatches, passwords,
  permissions, font failures, and PDF processing failures.
- Native Image explicitly bundles both the OCR font and PDFBox's dynamically
  loaded `Identity-H` CMap.
- The C smoke test transforms a PDF, reinspects the returned bytes, frees both
  native outputs, and verifies malformed-request status handling.
- `./gradlew --offline :java:nativeSmoke
  -PgraalVmHome=/opt/homebrew/opt/graalvm` — native build and C ABI smoke passed.
- `PY_PDFTOOLS_NATIVE_LIBRARY=... PYTHONPATH=src python3 -m unittest discover
  -v` — 57 Python tests passed, including real OCR success, page-mismatch and
  font-error mapping, transformed-PDF reinspection, and isolate reuse.

### M4 — Native inspection path

Status: complete

- [x] Verify the locally installed GraalVM and Native Image toolchain.
- [x] Adjust ABI-version checking to use a supported isolate-aware entry point.
- [x] Implement ABI-v1 native entry points and unmanaged output buffers.
- [x] Implement per-thread native error reporting and status mapping.
- [x] Build and smoke-test the macOS ARM64 shared library.
- [x] Run Python `inspect_pdf` end to end against the real native library.

GraalVM Native Image 25.0.3 is available at the Homebrew prefix
`/opt/homebrew/opt/graalvm`. The build accepts this location through the
`graalVmHome` Gradle property and does not require global `PATH` changes.

The ABI version function now receives the isolate thread and is called directly
after isolate creation. Supported GraalVM entry points require an isolate
context; the earlier context-free conceptual signature could not be implemented
without internal, unsupported Native Image APIs.

The shared library exports inspection, OCR, buffer-release, and error-query
entry points. At the M4 checkpoint OCR returned a deliberate not-implemented
processing error pending M5; inspection was complete end to end. On POSIX,
Python loads the library with global symbol visibility because Native Image's
dynamically loaded JDK helper libraries resolve symbols from the main image. PDFBox's filter
registry also makes headless AWT and legacy charsets reachable, so the build
includes traced JNI metadata and all runtime charset providers.

Verification:

- `./gradlew --offline :java:test` — 20 Java tests passed.
- `./gradlew --offline :java:nativeSmoke
  -PgraalVmHome=/opt/homebrew/opt/graalvm` — native build and C ABI smoke test passed.
- Native smoke covers ABI negotiation, successful geometry inspection, invalid-PDF status, output release, and isolate teardown.
- `PY_PDFTOOLS_NATIVE_LIBRARY=... PYTHONPATH=src python3 -m unittest discover -v` — 55 Python tests passed, including 3 real-library end-to-end tests.
- The end-to-end tests cover rotated crop-box geometry, native error mapping, and repeated isolate reuse.

### M3 — Java PDF inspection

Status: complete

- [x] Select and pin the Java build and test dependencies.
- [x] Add a reproducible Gradle wrapper and Java 21 compilation target.
- [x] Implement immutable Java PDF inspection models.
- [x] Inspect page count, encryption, visible dimensions, rotation, and page boxes with PDFBox.
- [x] Encode inspection responses using native schema version 1.
- [x] Cover geometry, crop boxes, rotations, multipage, malformed, and encrypted PDFs in Java tests.

Apache PDFBox 3.0.8 was selected because it is the current 3.0.x release and
contains security fixes not present in 3.0.7. JUnit 5.14.4 and Gradle 9.6.1 are
pinned for reproducible tests and builds. Compilation targets Java 21 bytecode;
the current development JDK is Java 26.

Verification:

- `./gradlew --offline clean :java:test` — 16 Java tests passed.
- Gradle dependency locking covers PDFBox, JUnit, and all transitives.
- Gradle distribution and wrapper JAR SHA-256 hashes match the published Gradle 9.6.1 values.
- `javap -verbose .../PdfInspector.class` reports class-file major version 65 (Java 21).
- `PYTHONPATH=src python3 -m unittest discover -v` — 51 Python tests passed.
- `python3 -m compileall -q src tests` — passed.
- `git diff --check` — passed.

PDFBox normalizes invalid raw page rotations when exposing `PDPage.getRotation()`.
The inspection boundary therefore reports PDFBox's normalized rotation; the
immutable Java and Python response models independently accept only 0, 90,
180, or 270.

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

At the M2 checkpoint, no native library was bundled. Public calls reached the
complete Python boundary and then reported `NativeLibraryError`; M4 supplied
the first working GraalVM artifact.

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

## Milestone plan

1. **M1 — Python public contract:** models, validation, exceptions, packaging, and tests.
2. **M2 — Native protocol boundary:** versioned serialization, FFI adapter contract, library discovery, and test doubles.
3. **M3 — Java PDF inspection:** Gradle project, PDFBox integration, geometry inspection, and Java tests.
4. **M4 — Native inspection path:** GraalVM ABI implementation and Python `inspect_pdf` end to end.
5. **M5 — OCR text transformation:** coordinate conversion, fitting, invisible text, fonts, and fixtures.
6. **M6 — Distribution:** native builds, platform wheels, CI, documentation, and release checks.
7. **M7 — Public release:** license metadata, trusted publishing, and first PyPI release.

## Environment baseline

- Repository began with `SPECS.md` only; commit `25c8829` is the baseline specification.
- Python 3.14.6 is available.
- OpenJDK 26.0.1 and Gradle 9.6.1 are available; Java output targets release 21.
- GraalVM Native Image 25.0.3 is available at `/opt/homebrew/opt/graalvm`.
- No Python test/build tools are installed globally; M1 tests will use `unittest`.

## Decisions

- Work proceeds in independently testable milestones, with a progress update and commit at each useful checkpoint.
- Native operations will not be represented as working until an end-to-end implementation exists.

## Log

- 2026-07-15: Read the complete specification and selected the Python public contract as the first milestone.
- 2026-07-15: Completed M1 with package metadata, public models, validation, exceptions, API exports, typing marker, and tests.
- 2026-07-15: Defined the M2 versioned JSON protocol and native status mapping; protocol tests also caught and fixed float drift in normalized rectangles.
- 2026-07-15: Completed M2 with native library discovery, ctypes bindings, lazy ABI-checked lifecycle management, thread serialization, public dispatch, and 51 passing tests.
- 2026-07-15: Completed M3 with a pinned Gradle build, PDFBox 3.0.8 inspection core, schema-v1 encoder, encrypted/malformed PDF handling, and 16 Java tests.
- 2026-07-15: Completed M4 with exported GraalVM entry points, managed native buffers and errors, a macOS ARM64 shared library build, an independent C ABI smoke test, and real Python-to-PDFBox inspection.
- 2026-07-15: Added the first M5 checkpoint with strict native OCR request decoding, independent validation, resource limits, and PDF page-matching rules.
- 2026-07-15: Added crop-relative OCR coordinate mapping across every supported PDF rotation and OCR orientation.
- 2026-07-15: Added the PDFBox OCR writer with fitted invisible text, a pinned embeddable font, confidence filtering, permission handling, and output-preservation tests.
- 2026-07-15: Completed M5 by connecting OCR to the native ABI, bundling the required Native Image resources, expanding the C smoke test, and passing the real Python transformation path.
- 2026-07-15: Added a verified self-contained macOS ARM64 wheel build, installed-wheel smoke test, five-platform wheel CI matrix, and public build/usage/release documentation.
- 2026-07-15: Started M7 with Apache-2.0 project licensing, complete bundled notices, and the `tindtechnologies-py-pdftools` distribution name.
- 2026-07-15: Added the tag-checked reusable wheel release workflow, least-privilege PyPI Trusted Publishing job, and exact maintainer setup guide.
- 2026-07-15: Published `v0.1.0` through the protected Trusted Publishing workflow and verified the exact five-file release on public PyPI.
- 2026-07-15: Completed M7 and the version 0.1 specification after a fresh public-index installation passed the installed-wheel smoke test without build tools.
- 2026-07-15: Started M8 to publish durable README metadata as version 0.1.1; runtime sources remain identical to 0.1.0.
- 2026-07-15: Completed M8 by publishing and verifying `v0.1.1`; PyPI now presents the durable description, and a fresh public installation passed without build tools.
- 2026-07-16: Started M9 by documenting the visible-page orientation contract and adding end-to-end vertical glyph-order and placement coverage across all page rotations and an offset crop box.
- 2026-07-16: Completed M9 by preventing PDFBox GSUB ligature substitution, requiring exact PDFBox and pypdf extraction, and passing the complete JVM, native, and Python regression suites.
- 2026-07-16: Published and verified `v0.1.2` across all five supported wheel platforms and the public PyPI index.
