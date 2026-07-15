"""Hatch build hook for producing Python-ABI-independent native wheels."""

from __future__ import annotations

import os
import platform
import re
import shutil
import subprocess
import sys
import sysconfig
from pathlib import Path
from typing import Any

from hatchling.builders.hooks.plugin.interface import BuildHookInterface

_SKIP_NATIVE_BUILD_ENV = "PY_PDFTOOLS_SKIP_NATIVE_BUILD"
_WHEEL_PLATFORM_TAG_ENV = "PY_PDFTOOLS_WHEEL_PLATFORM_TAG"
_TRUE_VALUES = frozenset(("1", "true", "yes"))


class CustomBuildHook(BuildHookInterface):
    """Build and include every runtime library emitted by Native Image."""

    def initialize(self, version: str, build_data: dict[str, Any]) -> None:
        if version == "editable":
            return

        project_root = Path(self.root)
        environment = os.environ.copy()
        self._configure_macos_target(environment)
        if environment.get(_SKIP_NATIVE_BUILD_ENV, "").lower() not in _TRUE_VALUES:
            self._build_native_library(project_root, environment)

        native_directory = project_root / "java" / "build" / "native"
        runtime_libraries = self._runtime_libraries(native_directory)
        expected_library = native_directory / self._main_library_filename()
        if expected_library not in runtime_libraries:
            raise RuntimeError(
                "Native Image did not produce the expected shared library: "
                f"{expected_library}"
            )
        staged_libraries = self._stage_runtime_libraries(
            project_root,
            runtime_libraries,
            environment,
        )

        force_include = build_data["force_include"]
        for source in staged_libraries:
            force_include[str(source)] = f"py_pdftools/_native/{source.name}"
        build_data["pure_python"] = False
        build_data["tag"] = f"py3-none-{self._wheel_platform_tag(environment)}"

    @staticmethod
    def _build_native_library(project_root: Path, environment: dict[str, str]) -> None:
        wrapper = "gradlew.bat" if os.name == "nt" else "gradlew"
        command = [str(project_root / wrapper), ":java:nativeCompile"]
        subprocess.run(
            command,
            cwd=project_root,
            env=environment,
            check=True,
        )

    @classmethod
    def _runtime_libraries(cls, native_directory: Path) -> list[Path]:
        extension = cls._runtime_library_extension()
        return sorted(
            path.resolve()
            for path in native_directory.glob(f"*{extension}")
            if path.is_file()
        )

    @classmethod
    def _stage_runtime_libraries(
        cls,
        project_root: Path,
        runtime_libraries: list[Path],
        environment: dict[str, str],
    ) -> list[Path]:
        staging_directory = project_root / "build" / "wheel-native"
        shutil.rmtree(staging_directory, ignore_errors=True)
        staging_directory.mkdir(parents=True)

        if sys.platform != "darwin":
            for source in runtime_libraries:
                shutil.copy2(source, staging_directory / source.name)
            return sorted(staging_directory.iterdir())

        target = environment["MACOSX_DEPLOYMENT_TARGET"]
        sdk = subprocess.run(
            ["xcrun", "--sdk", "macosx", "--show-sdk-version"],
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        retargetable_shims = frozenset(("libjava.dylib", "libjvm.dylib"))
        for source in runtime_libraries:
            destination = staging_directory / source.name
            minimum = cls._macos_minimum_version(source)
            if cls._version_tuple(minimum) <= cls._version_tuple(target):
                shutil.copy2(source, destination)
                continue
            if source.name not in retargetable_shims:
                raise RuntimeError(
                    f"{source.name} requires macOS {minimum}, which exceeds "
                    f"the wheel target {target}"
                )
            subprocess.run(
                [
                    "xcrun",
                    "vtool",
                    "-set-build-version",
                    "macos",
                    target,
                    sdk,
                    "-replace",
                    "-output",
                    str(destination),
                    str(source),
                ],
                check=True,
            )
            subprocess.run(
                ["codesign", "--force", "--sign", "-", str(destination)],
                check=True,
            )

        for staged in staging_directory.iterdir():
            minimum = cls._macos_minimum_version(staged)
            if cls._version_tuple(minimum) > cls._version_tuple(target):
                raise RuntimeError(
                    f"staged {staged.name} still requires macOS {minimum}"
                )
        return sorted(staging_directory.iterdir())

    @staticmethod
    def _macos_minimum_version(library: Path) -> str:
        output = subprocess.run(
            ["xcrun", "vtool", "-show-build", str(library)],
            check=True,
            capture_output=True,
            text=True,
        ).stdout
        match = re.search(r"^\s*minos\s+(\d+(?:\.\d+)*)\s*$", output, re.MULTILINE)
        if match is None:
            raise RuntimeError(f"could not determine minimum macOS for {library}")
        return match.group(1)

    @staticmethod
    def _version_tuple(value: str) -> tuple[int, ...]:
        return tuple(int(part) for part in value.split("."))

    @staticmethod
    def _main_library_filename() -> str:
        if sys.platform.startswith("linux"):
            return "libpy_pdftools.so"
        if sys.platform == "darwin":
            return "libpy_pdftools.dylib"
        if sys.platform in {"win32", "cygwin"}:
            return "py_pdftools.dll"
        raise RuntimeError(f"unsupported native build platform: {sys.platform}")

    @staticmethod
    def _runtime_library_extension() -> str:
        if sys.platform.startswith("linux"):
            return ".so"
        if sys.platform == "darwin":
            return ".dylib"
        if sys.platform in {"win32", "cygwin"}:
            return ".dll"
        raise RuntimeError(f"unsupported native build platform: {sys.platform}")

    @classmethod
    def _wheel_platform_tag(cls, environment: dict[str, str]) -> str:
        override = environment.get(_WHEEL_PLATFORM_TAG_ENV)
        if override:
            tag = override
        elif sys.platform == "darwin":
            deployment_target = environment["MACOSX_DEPLOYMENT_TARGET"]
            major, minor = deployment_target.split(".", 1)
            tag = f"macosx_{major}_{minor}_{platform.machine().lower()}"
        else:
            tag = sysconfig.get_platform().replace("-", "_").replace(".", "_")
        if re.fullmatch(r"[A-Za-z0-9_.]+", tag) is None:
            raise RuntimeError(f"invalid wheel platform tag: {tag!r}")
        return tag

    @staticmethod
    def _configure_macos_target(environment: dict[str, str]) -> None:
        if sys.platform != "darwin":
            return
        default_target = "11.0" if platform.machine().lower() == "arm64" else "10.15"
        environment.setdefault("MACOSX_DEPLOYMENT_TARGET", default_target)
