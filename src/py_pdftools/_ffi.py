"""Low-level ctypes adapter for the private GraalVM native ABI."""

from __future__ import annotations

import ctypes
import os
import sys
import threading
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator

from ._exceptions import NativeLibraryError
from ._protocol import NativeStatus, exception_for_status

_NATIVE_LIBRARY_ENV = "PY_PDFTOOLS_NATIVE_LIBRARY"
_MAX_ERROR_MESSAGE_BYTES = 1024 * 1024


class _NativeBuffer(ctypes.Structure):
    _fields_ = [
        ("data", ctypes.c_void_p),
        ("length", ctypes.c_size_t),
    ]


def native_library_filename(platform: str | None = None) -> str:
    """Return the packaged library filename for a supported operating system."""

    platform = sys.platform if platform is None else platform
    if platform.startswith("linux"):
        return "libpy_pdftools.so"
    if platform == "darwin":
        return "libpy_pdftools.dylib"
    if platform in {"win32", "cygwin"}:
        return "py_pdftools.dll"
    raise NativeLibraryError(f"unsupported native platform: {platform}")


def discover_native_library() -> Path:
    """Locate an override library or the native artifact bundled in the package."""

    override = os.environ.get(_NATIVE_LIBRARY_ENV)
    if override:
        path = Path(override)
        if not path.is_file():
            raise NativeLibraryError(
                f"native library from {_NATIVE_LIBRARY_ENV} does not exist: {path}"
            )
        return path.resolve()

    path = Path(__file__).with_name("_native") / native_library_filename()
    if not path.is_file():
        raise NativeLibraryError(
            "native py-pdftools library was not found; expected bundled artifact "
            f"at {path}"
        )
    return path


class CtypesNativeBackend:
    """One GraalVM isolate and the one-shot operations exported by the C ABI."""

    def __init__(self, library_path: Path | None = None) -> None:
        self._library_path = library_path or discover_native_library()
        try:
            self._library = ctypes.CDLL(str(self._library_path))
        except OSError as error:
            raise NativeLibraryError(
                f"failed to load native library {self._library_path}: {error}"
            ) from error

        try:
            self._bind_functions()
        except AttributeError as error:
            raise NativeLibraryError(
                f"native library {self._library_path} is missing a required symbol: {error}"
            ) from error

        self._isolate: ctypes.c_void_p | None = None
        self._creator_thread: ctypes.c_void_p | None = None
        self._creator_ident: int | None = None

    @property
    def abi_version(self) -> int:
        """Return the ABI version reported by the loaded library."""

        return int(self._pdftools_abi_version())

    def initialize(self) -> None:
        """Create the process-wide GraalVM isolate used by this backend."""

        if self._isolate is not None:
            return

        isolate = ctypes.c_void_p()
        isolate_thread = ctypes.c_void_p()
        status = self._graal_create_isolate(
            None,
            ctypes.byref(isolate),
            ctypes.byref(isolate_thread),
        )
        if status != 0 or not isolate.value or not isolate_thread.value:
            raise NativeLibraryError(
                f"failed to create GraalVM isolate (status {status})"
            )
        self._isolate = isolate
        self._creator_thread = isolate_thread
        self._creator_ident = threading.get_ident()

    def inspect_pdf(self, pdf: bytes) -> bytes:
        """Invoke the one-shot native PDF inspection operation."""

        with self._attached_thread() as isolate_thread:
            pdf_buffer = self._input_buffer(pdf)
            output = _NativeBuffer()
            status = self._pdftools_inspect_pdf(
                isolate_thread,
                pdf_buffer,
                len(pdf),
                ctypes.byref(output),
            )
            return self._consume_output(isolate_thread, status, output)

    def add_ocr_text_layer(self, pdf: bytes, request: bytes) -> bytes:
        """Invoke the one-shot native OCR text-layer operation."""

        with self._attached_thread() as isolate_thread:
            pdf_buffer = self._input_buffer(pdf)
            request_buffer = self._input_buffer(request)
            output = _NativeBuffer()
            status = self._pdftools_add_ocr_text_layer(
                isolate_thread,
                pdf_buffer,
                len(pdf),
                request_buffer,
                len(request),
                ctypes.byref(output),
            )
            return self._consume_output(isolate_thread, status, output)

    def close(self) -> None:
        """Tear down the GraalVM isolate, if it has been initialized."""

        isolate = self._isolate
        creator_thread = self._creator_thread
        creator_ident = self._creator_ident
        if isolate is None or creator_thread is None:
            return

        self._isolate = None
        self._creator_thread = None
        self._creator_ident = None

        if creator_ident == threading.get_ident():
            isolate_thread = creator_thread
        else:
            isolate_thread = ctypes.c_void_p()
            status = self._graal_attach_thread(isolate, ctypes.byref(isolate_thread))
            if status != 0 or not isolate_thread.value:
                raise NativeLibraryError(
                    f"failed to attach thread for GraalVM shutdown (status {status})"
                )

        status = self._graal_tear_down_isolate(isolate_thread)
        if status != 0:
            raise NativeLibraryError(
                f"failed to tear down GraalVM isolate (status {status})"
            )

    def _bind_functions(self) -> None:
        self._pdftools_abi_version = self._bind(
            "pdftools_abi_version",
            [],
            ctypes.c_uint32,
        )
        self._pdftools_inspect_pdf = self._bind(
            "pdftools_inspect_pdf",
            [
                ctypes.c_void_p,
                ctypes.POINTER(ctypes.c_uint8),
                ctypes.c_size_t,
                ctypes.POINTER(_NativeBuffer),
            ],
            ctypes.c_uint32,
        )
        self._pdftools_add_ocr_text_layer = self._bind(
            "pdftools_add_ocr_text_layer",
            [
                ctypes.c_void_p,
                ctypes.POINTER(ctypes.c_uint8),
                ctypes.c_size_t,
                ctypes.POINTER(ctypes.c_uint8),
                ctypes.c_size_t,
                ctypes.POINTER(_NativeBuffer),
            ],
            ctypes.c_uint32,
        )
        self._pdftools_free_buffer = self._bind(
            "pdftools_free_buffer",
            [ctypes.c_void_p, ctypes.c_void_p],
            None,
        )
        self._pdftools_last_error = self._bind(
            "pdftools_last_error",
            [
                ctypes.c_void_p,
                ctypes.c_void_p,
                ctypes.c_size_t,
                ctypes.POINTER(ctypes.c_size_t),
            ],
            ctypes.c_uint32,
        )
        self._graal_create_isolate = self._bind(
            "graal_create_isolate",
            [
                ctypes.c_void_p,
                ctypes.POINTER(ctypes.c_void_p),
                ctypes.POINTER(ctypes.c_void_p),
            ],
            ctypes.c_int,
        )
        self._graal_attach_thread = self._bind(
            "graal_attach_thread",
            [ctypes.c_void_p, ctypes.POINTER(ctypes.c_void_p)],
            ctypes.c_int,
        )
        self._graal_detach_thread = self._bind(
            "graal_detach_thread",
            [ctypes.c_void_p],
            ctypes.c_int,
        )
        self._graal_tear_down_isolate = self._bind(
            "graal_tear_down_isolate",
            [ctypes.c_void_p],
            ctypes.c_int,
        )

    def _bind(self, name: str, argument_types: list[object], result_type: object) -> Any:
        function = getattr(self._library, name)
        function.argtypes = argument_types
        function.restype = result_type
        return function

    @contextmanager
    def _attached_thread(self) -> Iterator[ctypes.c_void_p]:
        isolate = self._isolate
        creator_thread = self._creator_thread
        if isolate is None or creator_thread is None:
            raise NativeLibraryError("native runtime has not been initialized")

        if self._creator_ident == threading.get_ident():
            yield creator_thread
            return

        isolate_thread = ctypes.c_void_p()
        status = self._graal_attach_thread(isolate, ctypes.byref(isolate_thread))
        if status != 0 or not isolate_thread.value:
            raise NativeLibraryError(
                f"failed to attach thread to GraalVM isolate (status {status})"
            )
        try:
            yield isolate_thread
        except BaseException:
            self._graal_detach_thread(isolate_thread)
            raise
        else:
            detach_status = self._graal_detach_thread(isolate_thread)
            if detach_status != 0:
                raise NativeLibraryError(
                    "failed to detach thread from GraalVM isolate "
                    f"(status {detach_status})"
                )

    def _consume_output(
        self,
        isolate_thread: ctypes.c_void_p,
        status: int,
        output: _NativeBuffer,
    ) -> bytes:
        try:
            if status != NativeStatus.SUCCESS:
                message = self._last_error_message(isolate_thread, status)
                raise exception_for_status(status, message)
            if output.length and not output.data:
                raise NativeLibraryError(
                    "native operation returned a null buffer with a non-zero length"
                )
            return ctypes.string_at(output.data, output.length) if output.length else b""
        finally:
            if output.data:
                self._pdftools_free_buffer(isolate_thread, output.data)

    def _last_error_message(
        self,
        isolate_thread: ctypes.c_void_p,
        operation_status: int,
    ) -> str:
        required_size = ctypes.c_size_t()
        status = self._pdftools_last_error(
            isolate_thread,
            None,
            0,
            ctypes.byref(required_size),
        )
        if status != NativeStatus.SUCCESS or required_size.value == 0:
            return f"native operation failed with status {operation_status}"
        if required_size.value > _MAX_ERROR_MESSAGE_BYTES:
            return (
                "native operation returned an oversized error message "
                f"({required_size.value} bytes)"
            )

        buffer = ctypes.create_string_buffer(required_size.value)
        status = self._pdftools_last_error(
            isolate_thread,
            buffer,
            len(buffer),
            ctypes.byref(required_size),
        )
        if status != NativeStatus.SUCCESS:
            return f"native operation failed with status {operation_status}"
        return buffer.raw.split(b"\0", 1)[0].decode("utf-8", errors="replace")

    @staticmethod
    def _input_buffer(data: bytes) -> Any:
        return (ctypes.c_uint8 * len(data)).from_buffer_copy(data)


def load_native_backend() -> CtypesNativeBackend:
    """Create the default ctypes-backed native adapter."""

    return CtypesNativeBackend()
