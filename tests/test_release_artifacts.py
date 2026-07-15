from __future__ import annotations

import tempfile
import unittest
import zipfile
from pathlib import Path

from tools.check_release_artifacts import (
    REQUIRED_LICENSE_FILES,
    WHEEL_TAGS,
    ReleaseValidationError,
    expected_wheel_names,
    validate_release,
)


class ReleaseArtifactTests(unittest.TestCase):
    project_name = "tindtechnologies-py-pdftools"
    version = "0.1.0"
    license_expression = "Apache-2.0"

    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.project_file = self.root / "pyproject.toml"
        self.dist_directory = self.root / "dist"
        self.dist_directory.mkdir()
        self.project_file.write_text(
            "[project]\n"
            f'name = "{self.project_name}"\n'
            f'version = "{self.version}"\n'
            f'license = "{self.license_expression}"\n',
            encoding="utf-8",
        )
        self._write_all_wheels()

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def _write_all_wheels(self) -> None:
        for wheel_tag in WHEEL_TAGS:
            self._write_wheel(wheel_tag)

    def _write_wheel(
        self,
        wheel_tag: str,
        *,
        metadata_name: str | None = None,
    ) -> Path:
        distribution = self.project_name.replace("-", "_")
        filename = f"{distribution}-{self.version}-{wheel_tag}.whl"
        wheel = self.dist_directory / filename
        dist_info = f"{distribution}-{self.version}.dist-info"
        license_headers = "".join(
            f"License-File: {path}\n" for path in REQUIRED_LICENSE_FILES
        )
        metadata = (
            "Metadata-Version: 2.4\n"
            f"Name: {metadata_name or self.project_name}\n"
            f"Version: {self.version}\n"
            f"License-Expression: {self.license_expression}\n"
            f"{license_headers}\n"
        )
        with zipfile.ZipFile(wheel, "w") as archive:
            archive.writestr(f"{dist_info}/METADATA", metadata)
            archive.writestr(f"{dist_info}/WHEEL", f"Tag: {wheel_tag}\n")
        return wheel

    def test_accepts_the_complete_release_wheel_set(self) -> None:
        wheels = validate_release("v0.1.0", self.project_file, self.dist_directory)
        self.assertEqual(
            {wheel.name for wheel in wheels},
            expected_wheel_names(self.project_name, self.version),
        )

    def test_rejects_a_tag_that_does_not_match_project_version(self) -> None:
        with self.assertRaisesRegex(ReleaseValidationError, "does not match"):
            validate_release("v0.2.0", self.project_file, self.dist_directory)

    def test_rejects_an_incomplete_wheel_set(self) -> None:
        next(self.dist_directory.glob("*win_amd64.whl")).unlink()
        with self.assertRaisesRegex(ReleaseValidationError, "missing"):
            validate_release("v0.1.0", self.project_file, self.dist_directory)

    def test_rejects_incorrect_wheel_metadata(self) -> None:
        self._write_wheel(WHEEL_TAGS[0], metadata_name="wrong-project")
        with self.assertRaisesRegex(ReleaseValidationError, "wrong project name"):
            validate_release("v0.1.0", self.project_file, self.dist_directory)
