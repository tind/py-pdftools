# Release checklist

Version 0.1 is not published yet. Releases are wheel-only because every wheel
contains a platform-specific native library; publishing a source archive would
not provide an installable runtime package on its own.

## One-time maintainer setup

Complete these steps before publishing the first GitHub release. The PyPI
project does not need to exist first: a pending Trusted Publisher creates it
on its first successful upload.

1. In the public `tind/py-pdftools` repository's
   [environment settings](https://github.com/tind/py-pdftools/settings/environments),
   create a GitHub Actions environment named exactly `pypi`.
2. Require a trusted reviewer for that environment so publication always has
   a manual approval gate. If there is only one releaser, do not enable
   GitHub's “prevent self-review” option for that reviewer.
3. Restrict the environment's deployment tags to the release pattern `v*`.
4. While signed in to the intended public PyPI account, open the account's
   [Publishing page](https://pypi.org/manage/account/publishing/) and add a
   pending GitHub publisher with these exact values:

   | Field | Value |
   | --- | --- |
   | PyPI project name | `tindtechnologies-py-pdftools` |
   | GitHub owner | `tind` |
   | Repository name | `py-pdftools` |
   | Workflow filename | `publish.yml` |
   | Environment name | `pypi` |

No long-lived PyPI API token or GitHub Actions secret is required. Pending
publishers do not reserve names, so configure the publisher reasonably close
to the first release.

## Before tagging

1. Confirm `PROGRESS.md` has no unresolved release blocker.
2. Set the intended version in `pyproject.toml` and review the native
   ABI/schema versions.
3. Run Java tests, the C ABI smoke test, Python tests against the real native
   library, and the installed-wheel smoke test.
4. Review dependency locks, bundled font source/checksum/license, and public
   documentation.
5. Push the release commit and wait for the platform workflow to pass on all
   five supported targets:

   - manylinux 2.28 x86-64
   - manylinux 2.28 ARM64
   - macOS 10.15 x86-64
   - macOS 11 ARM64
   - Windows x86-64

## Publish

1. Create a tag named exactly `v<version>`, such as `v0.1.0`, on the reviewed
   release commit.
2. Create and publish a non-prerelease GitHub release for that tag. Draft
   releases do not trigger publication, and prereleases are deliberately
   skipped.
3. Wait for the reusable wheel matrix and release validation job to pass.
4. Review and approve the `pypi` environment deployment.

The release validator rejects a tag that does not exactly match the project
version, missing or unexpected wheels, incorrect platform tags or metadata,
and omitted legal files. Only the final publish job receives `id-token: write`;
that job downloads the validated artifacts and invokes PyPI Trusted Publishing.

## Artifact review

For every wheel:

- Verify the filename and `WHEEL` tag are platform-specific and
  Python-ABI-independent (`py3-none-<platform>`).
- Confirm `Root-Is-Purelib: false`.
- Confirm the main native library and all generated runtime helper libraries
  are under `py_pdftools/_native`.
- Confirm the Apache, PDFBox, Noto, and notice files appear under the wheel's
  `.dist-info/licenses` directory.
- Install into a clean environment and run `tools/installed_wheel_smoke.py`
  without Java, GraalVM, `PYTHONPATH`, or `PY_PDFTOOLS_NATIVE_LIBRARY`.

## After publication

1. Confirm PyPI lists exactly the five expected files and the Apache-2.0
   metadata.
2. Install `tindtechnologies-py-pdftools==<version>` from PyPI on at least one
   supported platform and run the installed-wheel smoke test.
3. Update the pre-release wording in `README.md` and record the release URL,
   workflow run, and verification in `PROGRESS.md`.

PyPI release files are immutable. Never delete and reuse a published version;
if uploaded artifacts need to change, fix the issue and publish a new version.
