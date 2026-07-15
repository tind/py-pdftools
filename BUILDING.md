# Building py-pdftools

Runtime wheels are self-contained, but producing them requires Python, a JDK,
Gradle, a C toolchain, and GraalVM Native Image. Native artifacts must be built
on their target operating system and architecture.

## Local setup

Create the project-local environment and install only build tooling:

```bash
python3 -m venv venv
./venv/bin/python -m pip install "hatchling>=1.31,<2" build
```

The Python package itself has no runtime dependencies. Tests use the standard
library `unittest` runner, so pytest is optional.

## GraalVM location

The Gradle build accepts GraalVM through either `GRAALVM_HOME` or the
`graalVmHome` property. GraalVM does not need to be placed on the global PATH.
For the Homebrew installation used during development:

```bash
./gradlew :java:nativeSmoke \
  -PgraalVmHome=/opt/homebrew/opt/graalvm
```

For wheel builds, export the location only for the build command:

```bash
GRAALVM_HOME=/opt/homebrew/opt/graalvm \
  ./venv/bin/python -m build --wheel --no-isolation
```

## Tests

Run JVM tests:

```bash
./gradlew --offline :java:test
```

Build the shared library and run the independent C ABI smoke test on Linux or
macOS:

```bash
./gradlew --offline :java:nativeSmoke \
  -PgraalVmHome=/opt/homebrew/opt/graalvm
```

Run Python tests against that real native library on macOS:

```bash
PYTHONPATH=src \
PY_PDFTOOLS_NATIVE_LIBRARY=java/build/native/libpy_pdftools.dylib \
./venv/bin/python -m unittest discover -v
```

Use `libpy_pdftools.so` on Linux and `py_pdftools.dll` on Windows.

## Platform wheel build

The Hatch hook performs four steps:

1. Runs the Gradle `:java:nativeCompile` task.
2. Stages every Native Image runtime shared library.
3. Marks the wheel as platform-specific and Python-ABI-independent.
4. Includes the staged libraries under `py_pdftools/_native`.

The default local tag comes from the build platform. These environment
variables are available for controlled builds:

- `GRAALVM_HOME`: GraalVM installation root.
- `MACOSX_DEPLOYMENT_TARGET`: macOS minimum version; defaults to 11.0 on ARM64
  and 10.15 on x86-64.
- `PY_PDFTOOLS_WHEEL_PLATFORM_TAG`: explicit wheel platform component, such as
  `manylinux_2_28_x86_64`.
- `PY_PDFTOOLS_SKIP_NATIVE_BUILD=1`: reuse an already-built Native Image output.
  This is intended only for controlled packaging jobs.

Linux release wheels must be built inside the matching PyPA manylinux 2.28
container. Setting a manylinux tag on a binary built on a newer general-purpose
distribution is not sufficient. The GitHub Actions workflow uses native
x86-64/ARM64 runners with the correct containers.

## Verify an installed wheel

For the local macOS ARM64 artifact:

```bash
python3 -m venv /tmp/py-pdftools-wheel-venv
/tmp/py-pdftools-wheel-venv/bin/python -m pip install --no-deps \
  dist/tindtechnologies_py_pdftools-0.1.0-py3-none-macosx_11_0_arm64.whl

env -u PYTHONPATH \
  -u PY_PDFTOOLS_NATIVE_LIBRARY \
  -u GRAALVM_HOME \
  -u JAVA_HOME \
  PATH=/usr/bin:/bin \
  /tmp/py-pdftools-wheel-venv/bin/python tools/installed_wheel_smoke.py
```

The smoke test inspects a rotated crop-box PDF, adds Unicode OCR text, and
reinspects the transformed output using only files installed from the wheel.
