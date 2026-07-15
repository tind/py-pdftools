from __future__ import annotations

import ctypes
import os
import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import patch

from py_pdftools import InvalidPdfError, NativeLibraryError
from py_pdftools._ffi import (
    _NATIVE_LIBRARY_ENV,
    _NativeBuffer,
    CtypesNativeBackend,
    discover_native_library,
    native_library_filename,
)
from py_pdftools._protocol import NativeStatus


class FakeFunction:
    def __init__(self, callback: object) -> None:
        self.callback = callback
        self.argtypes: list[object] | None = None
        self.restype: object = None

    def __call__(self, *arguments: object) -> object:
        return self.callback(*arguments)  # type: ignore[operator]


class FakeNativeLibrary:
    def __init__(self) -> None:
        self.inspect_status = NativeStatus.SUCCESS
        self.error_message = b"malformed PDF"
        self.freed_buffers: list[int] = []
        self.output_buffers: list[ctypes.Array[ctypes.c_char]] = []
        self.attach_calls = 0
        self.detach_calls = 0
        self.teardown_calls = 0

        self.pdftools_abi_version = FakeFunction(lambda: 1)
        self.pdftools_inspect_pdf = FakeFunction(self._inspect_pdf)
        self.pdftools_add_ocr_text_layer = FakeFunction(self._add_ocr_text_layer)
        self.pdftools_free_buffer = FakeFunction(self._free_buffer)
        self.pdftools_last_error = FakeFunction(self._last_error)
        self.graal_create_isolate = FakeFunction(self._create_isolate)
        self.graal_attach_thread = FakeFunction(self._attach_thread)
        self.graal_detach_thread = FakeFunction(self._detach_thread)
        self.graal_tear_down_isolate = FakeFunction(self._tear_down_isolate)

    def _inspect_pdf(
        self,
        isolate_thread: object,
        pdf_data: object,
        pdf_length: int,
        output: object,
    ) -> int:
        if self.inspect_status != NativeStatus.SUCCESS:
            return self.inspect_status
        self._set_output(output, b'{"schemaVersion":1}')
        return NativeStatus.SUCCESS

    def _add_ocr_text_layer(
        self,
        isolate_thread: object,
        pdf_data: object,
        pdf_length: int,
        request_data: object,
        request_length: int,
        output: object,
    ) -> int:
        self._set_output(output, b"%PDF-output")
        return NativeStatus.SUCCESS

    def _set_output(self, output: object, data: bytes) -> None:
        buffer = ctypes.create_string_buffer(data)
        self.output_buffers.append(buffer)
        native_output = ctypes.cast(output, ctypes.POINTER(_NativeBuffer)).contents
        native_output.data = ctypes.cast(buffer, ctypes.c_void_p).value
        native_output.length = len(data)

    def _free_buffer(self, isolate_thread: object, data: int) -> None:
        self.freed_buffers.append(data)

    def _last_error(
        self,
        isolate_thread: object,
        buffer: object,
        capacity: int,
        required_size: object,
    ) -> int:
        encoded = self.error_message + b"\0"
        required = ctypes.cast(required_size, ctypes.POINTER(ctypes.c_size_t)).contents
        required.value = len(encoded)
        if buffer and capacity:
            ctypes.memmove(buffer, encoded, min(capacity, len(encoded)))
        return NativeStatus.SUCCESS

    @staticmethod
    def _create_isolate(
        parameters: object,
        isolate: object,
        isolate_thread: object,
    ) -> int:
        ctypes.cast(isolate, ctypes.POINTER(ctypes.c_void_p)).contents.value = 1001
        ctypes.cast(isolate_thread, ctypes.POINTER(ctypes.c_void_p)).contents.value = 2001
        return 0

    def _attach_thread(self, isolate: object, isolate_thread: object) -> int:
        self.attach_calls += 1
        ctypes.cast(isolate_thread, ctypes.POINTER(ctypes.c_void_p)).contents.value = 2002
        return 0

    def _detach_thread(self, isolate_thread: object) -> int:
        self.detach_calls += 1
        return 0

    def _tear_down_isolate(self, isolate_thread: object) -> int:
        self.teardown_calls += 1
        return 0


class NativeLibraryDiscoveryTests(unittest.TestCase):
    def test_maps_supported_platforms_to_library_names(self) -> None:
        self.assertEqual(native_library_filename("linux"), "libpy_pdftools.so")
        self.assertEqual(native_library_filename("linux2"), "libpy_pdftools.so")
        self.assertEqual(native_library_filename("darwin"), "libpy_pdftools.dylib")
        self.assertEqual(native_library_filename("win32"), "py_pdftools.dll")

    def test_rejects_an_unsupported_platform(self) -> None:
        with self.assertRaisesRegex(NativeLibraryError, "unsupported native platform"):
            native_library_filename("plan9")

    def test_environment_override_selects_an_existing_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            library = Path(directory) / "custom-native-library"
            library.touch()
            with patch.dict(os.environ, {_NATIVE_LIBRARY_ENV: str(library)}):
                self.assertEqual(discover_native_library(), library.resolve())

    def test_environment_override_rejects_a_missing_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            missing = Path(directory) / "missing"
            with patch.dict(os.environ, {_NATIVE_LIBRARY_ENV: str(missing)}):
                with self.assertRaisesRegex(NativeLibraryError, "does not exist"):
                    discover_native_library()

    def test_loading_an_invalid_library_has_a_clear_error(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            invalid = Path(directory) / "invalid-library"
            invalid.touch()

            with self.assertRaisesRegex(NativeLibraryError, "failed to load"):
                CtypesNativeBackend(invalid)


class CtypesNativeBackendTests(unittest.TestCase):
    def create_backend(self) -> tuple[CtypesNativeBackend, FakeNativeLibrary]:
        library = FakeNativeLibrary()
        with patch("py_pdftools._ffi.ctypes.CDLL", return_value=library):
            backend = CtypesNativeBackend(Path("fake-native-library"))
        return backend, library

    def test_invokes_operations_copies_results_and_frees_native_buffers(self) -> None:
        backend, library = self.create_backend()

        self.assertEqual(backend.abi_version, 1)
        backend.initialize()
        inspection = backend.inspect_pdf(b"%PDF-input")
        transformed = backend.add_ocr_text_layer(b"%PDF-input", b'{"request":1}')
        backend.close()

        self.assertEqual(inspection, b'{"schemaVersion":1}')
        self.assertEqual(transformed, b"%PDF-output")
        self.assertEqual(len(library.freed_buffers), 2)
        self.assertEqual(library.teardown_calls, 1)

    def test_retrieves_and_maps_native_error_details(self) -> None:
        backend, library = self.create_backend()
        library.inspect_status = NativeStatus.INVALID_PDF
        backend.initialize()

        with self.assertRaisesRegex(InvalidPdfError, "malformed PDF"):
            backend.inspect_pdf(b"%PDF-broken")

        backend.close()

    def test_attaches_and_detaches_a_non_creator_thread(self) -> None:
        backend, library = self.create_backend()
        backend.initialize()
        failures: list[BaseException] = []

        def inspect() -> None:
            try:
                backend.inspect_pdf(b"%PDF-input")
            except BaseException as error:
                failures.append(error)

        thread = threading.Thread(target=inspect)
        thread.start()
        thread.join()
        backend.close()

        self.assertEqual(failures, [])
        self.assertEqual(library.attach_calls, 1)
        self.assertEqual(library.detach_calls, 1)


if __name__ == "__main__":
    unittest.main()
