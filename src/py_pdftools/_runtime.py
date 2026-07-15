"""Lazy process-wide native runtime management."""

from __future__ import annotations

import atexit
import threading
from collections.abc import Callable
from typing import Protocol

from ._exceptions import NativeLibraryError, PdfToolsError
from ._ffi import load_native_backend
from ._protocol import NATIVE_ABI_VERSION


class NativeBackend(Protocol):
    """Internal adapter contract implemented by the ctypes backend and test fakes."""

    @property
    def abi_version(self) -> int: ...

    def initialize(self) -> None: ...

    def inspect_pdf(self, pdf: bytes) -> bytes: ...

    def add_ocr_text_layer(self, pdf: bytes, request: bytes) -> bytes: ...

    def close(self) -> None: ...


BackendFactory = Callable[[], NativeBackend]

_lock = threading.RLock()
_backend: NativeBackend | None = None
_backend_factory: BackendFactory = load_native_backend
_shutdown_registered = False


def inspect_pdf(pdf: bytes) -> bytes:
    """Run one serialized native inspection call."""

    with _lock:
        result = _get_backend().inspect_pdf(pdf)
        return _require_bytes(result, operation="inspection")


def add_ocr_text_layer(pdf: bytes, request: bytes) -> bytes:
    """Run one serialized native OCR transformation call."""

    with _lock:
        result = _get_backend().add_ocr_text_layer(pdf, request)
        result = _require_bytes(result, operation="OCR transformation")
        if not result:
            raise NativeLibraryError("native OCR transformation returned an empty PDF")
        return result


def _get_backend() -> NativeBackend:
    global _backend, _shutdown_registered

    if _backend is not None:
        return _backend

    candidate: NativeBackend | None = None
    try:
        candidate = _backend_factory()
        candidate.initialize()
        actual_version = candidate.abi_version
        if actual_version != NATIVE_ABI_VERSION:
            raise NativeLibraryError(
                "native ABI version mismatch: "
                f"expected {NATIVE_ABI_VERSION}, got {actual_version}"
            )
    except PdfToolsError:
        _close_safely(candidate)
        raise
    except Exception as error:
        _close_safely(candidate)
        raise NativeLibraryError(f"failed to initialize native runtime: {error}") from error

    _backend = candidate
    if not _shutdown_registered:
        atexit.register(_shutdown_runtime)
        _shutdown_registered = True
    return candidate


def _shutdown_runtime() -> None:
    """Close the active backend without allowing shutdown errors to escape."""

    global _backend

    with _lock:
        backend = _backend
        _backend = None
        _close_safely(backend)


def _close_safely(backend: NativeBackend | None) -> None:
    if backend is None:
        return
    try:
        backend.close()
    except Exception:
        pass


def _require_bytes(value: object, *, operation: str) -> bytes:
    if not isinstance(value, bytes):
        raise NativeLibraryError(f"native {operation} returned a non-bytes result")
    return value


def _set_backend_factory_for_testing(factory: BackendFactory) -> None:
    """Replace the backend factory and close any cached backend in tests."""

    global _backend_factory

    _shutdown_runtime()
    with _lock:
        _backend_factory = factory


def _reset_runtime_for_testing() -> None:
    """Restore default runtime state after a test."""

    global _backend_factory

    _shutdown_runtime()
    with _lock:
        _backend_factory = load_native_backend
