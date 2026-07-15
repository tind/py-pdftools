"""Validate a tagged set of platform wheels before publication."""

from __future__ import annotations

import argparse
import re
import sys
import zipfile
from email.parser import BytesParser
from pathlib import Path

try:
    import tomllib
except ModuleNotFoundError:  # pragma: no cover - exercised by Python 3.10 CI
    import tomli as tomllib

WHEEL_TAGS = (
    "py3-none-macosx_10_15_x86_64",
    "py3-none-macosx_11_0_arm64",
    "py3-none-manylinux_2_28_aarch64",
    "py3-none-manylinux_2_28_x86_64",
    "py3-none-win_amd64",
)
REQUIRED_LICENSE_FILES = (
    "LICENSE",
    "LICENSES/PDFBOX-3.0.8.txt",
    "NOTICE",
    "java/src/main/resources/dev/pypdftools/fonts/LICENSE.txt",
)


class ReleaseValidationError(ValueError):
    """The release tag or wheel set does not match project metadata."""


def _wheel_distribution_name(project_name: str) -> str:
    return re.sub(r"[-_.]+", "_", project_name)


def _project_metadata(project_file: Path) -> tuple[str, str, str]:
    with project_file.open("rb") as stream:
        project = tomllib.load(stream)["project"]
    return project["name"], project["version"], project["license"]


def expected_wheel_names(project_name: str, version: str) -> set[str]:
    distribution = _wheel_distribution_name(project_name)
    return {
        f"{distribution}-{version}-{wheel_tag}.whl" for wheel_tag in WHEEL_TAGS
    }


def validate_release(
    release_tag: str,
    project_file: Path,
    dist_directory: Path,
) -> tuple[Path, ...]:
    project_name, version, license_expression = _project_metadata(project_file)
    expected_tag = f"v{version}"
    if release_tag != expected_tag:
        raise ReleaseValidationError(
            f"release tag {release_tag!r} does not match {expected_tag!r}"
        )

    expected_names = expected_wheel_names(project_name, version)
    wheels = tuple(sorted(dist_directory.glob("*.whl")))
    actual_names = {wheel.name for wheel in wheels}
    if actual_names != expected_names:
        missing = sorted(expected_names - actual_names)
        unexpected = sorted(actual_names - expected_names)
        details = []
        if missing:
            details.append(f"missing: {', '.join(missing)}")
        if unexpected:
            details.append(f"unexpected: {', '.join(unexpected)}")
        raise ReleaseValidationError("invalid wheel set (" + "; ".join(details) + ")")

    distribution = _wheel_distribution_name(project_name)
    dist_info = f"{distribution}-{version}.dist-info"
    for wheel in wheels:
        filename_tag = wheel.name.removesuffix(".whl").split("-", 2)[2]
        with zipfile.ZipFile(wheel) as archive:
            metadata = BytesParser().parsebytes(archive.read(f"{dist_info}/METADATA"))
            if metadata["Name"] != project_name:
                raise ReleaseValidationError(f"{wheel.name} has the wrong project name")
            if metadata["Version"] != version:
                raise ReleaseValidationError(f"{wheel.name} has the wrong version")
            if metadata["License-Expression"] != license_expression:
                raise ReleaseValidationError(
                    f"{wheel.name} has the wrong license expression"
                )
            license_files = set(metadata.get_all("License-File", ()))
            if not set(REQUIRED_LICENSE_FILES).issubset(license_files):
                raise ReleaseValidationError(f"{wheel.name} omits required legal files")
            wheel_metadata = archive.read(f"{dist_info}/WHEEL").decode("utf-8")
            if f"Tag: {filename_tag}\n" not in wheel_metadata:
                raise ReleaseValidationError(
                    f"{wheel.name} metadata does not match its platform tag"
                )

    return wheels


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True, help="GitHub release tag")
    parser.add_argument("--project", type=Path, default=Path("pyproject.toml"))
    parser.add_argument("--dist", type=Path, default=Path("dist"))
    arguments = parser.parse_args(argv)
    try:
        wheels = validate_release(arguments.tag, arguments.project, arguments.dist)
    except (KeyError, OSError, ReleaseValidationError, tomllib.TOMLDecodeError) as error:
        print(f"release validation failed: {error}", file=sys.stderr)
        return 1
    print(f"validated {len(wheels)} wheels for {arguments.tag}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
