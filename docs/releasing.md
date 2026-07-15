# Release checklist

Version 0.1 is not published yet. Publication should happen only after the
cross-platform workflow has passed for all supported artifacts.

## Before tagging

1. Confirm `PROGRESS.md` has no unresolved release blocker.
2. Run Java tests, the C ABI smoke test, Python tests against the real library,
   and the installed-wheel smoke test.
3. Confirm the version in `pyproject.toml` and the native ABI/schema versions.
4. Review dependency locks, bundled font source/checksum/license, and public
   documentation.
5. Run the wheel workflow for:
   - manylinux 2.28 x86-64
   - manylinux 2.28 ARM64
   - macOS 10.15 x86-64
   - macOS 11 ARM64
   - Windows x86-64

## Artifact review

For every wheel:

- Verify the filename and `WHEEL` tag are platform-specific and
  Python-ABI-independent (`py3-none-<platform>`).
- Confirm `Root-Is-Purelib: false`.
- Confirm the main native library and all generated runtime helper libraries
  are under `py_pdftools/_native`.
- Install into a clean environment and run `tools/installed_wheel_smoke.py`
  without Java, GraalVM, `PYTHONPATH`, or `PY_PDFTOOLS_NATIVE_LIBRARY`.
- Record SHA-256 checksums for the final release artifacts.

## GitHub and PyPI setup requiring maintainer action

Before the first release, a maintainer must:

1. Create or select the GitHub repository and push the local history so the
   platform workflow can run.
2. Create the PyPI project/maintainer account arrangement.
3. Configure a protected GitHub release environment and PyPI trusted publisher,
   or choose another explicit credential-management policy.
4. Approve the final version/tag and public project license/metadata.

No workflow in this repository currently publishes to PyPI. That is deliberate:
publishing authority and trusted-publisher configuration must be established by
the maintainer first.
