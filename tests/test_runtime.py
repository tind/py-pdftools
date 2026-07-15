from __future__ import annotations

import json
import threading
import time
import unittest

from py_pdftools import (
    InvalidPdfError,
    NativeLibraryError,
    NormalizedRect,
    OcrDocument,
    OcrLine,
    OcrPage,
    add_ocr_text_layer,
    inspect_pdf,
)
from py_pdftools._protocol import NATIVE_ABI_VERSION
from py_pdftools._runtime import (
    _reset_runtime_for_testing,
    _set_backend_factory_for_testing,
    _shutdown_runtime,
)


def inspection_response() -> bytes:
    return json.dumps(
        {
            "schemaVersion": 1,
            "pageCount": 1,
            "encrypted": False,
            "pages": [
                {
                    "pageIndex": 0,
                    "widthPoints": 612.0,
                    "heightPoints": 792.0,
                    "rotation": 0,
                    "cropBox": {
                        "lowerLeftX": 0.0,
                        "lowerLeftY": 0.0,
                        "upperRightX": 612.0,
                        "upperRightY": 792.0,
                    },
                    "mediaBox": {
                        "lowerLeftX": 0.0,
                        "lowerLeftY": 0.0,
                        "upperRightX": 612.0,
                        "upperRightY": 792.0,
                    },
                }
            ],
        }
    ).encode()


class FakeBackend:
    def __init__(self, *, abi_version: int = NATIVE_ABI_VERSION) -> None:
        self.abi_version = abi_version
        self.initialize_calls = 0
        self.close_calls = 0
        self.inspection_calls: list[bytes] = []
        self.transformation_calls: list[tuple[bytes, bytes]] = []
        self.active_calls = 0
        self.maximum_active_calls = 0
        self._activity_lock = threading.Lock()

    def initialize(self) -> None:
        self.initialize_calls += 1

    def inspect_pdf(self, pdf: bytes) -> bytes:
        with self._activity_lock:
            self.active_calls += 1
            self.maximum_active_calls = max(self.maximum_active_calls, self.active_calls)
        time.sleep(0.002)
        try:
            self.inspection_calls.append(pdf)
            return inspection_response()
        finally:
            with self._activity_lock:
                self.active_calls -= 1

    def add_ocr_text_layer(self, pdf: bytes, request: bytes) -> bytes:
        self.transformation_calls.append((pdf, request))
        return b"%PDF-transformed"

    def close(self) -> None:
        self.close_calls += 1


class RuntimeLifecycleTests(unittest.TestCase):
    def tearDown(self) -> None:
        _reset_runtime_for_testing()

    def test_initializes_lazily_and_reuses_one_backend(self) -> None:
        backend = FakeBackend()
        factory_calls = 0

        def factory() -> FakeBackend:
            nonlocal factory_calls
            factory_calls += 1
            return backend

        _set_backend_factory_for_testing(factory)
        self.assertEqual(factory_calls, 0)

        first = inspect_pdf(b"%PDF-first")
        second = inspect_pdf(bytearray(b"%PDF-second"))

        self.assertEqual(first.page_count, 1)
        self.assertEqual(second.page_count, 1)
        self.assertEqual(factory_calls, 1)
        self.assertEqual(backend.initialize_calls, 1)
        self.assertEqual(
            backend.inspection_calls,
            [b"%PDF-first", b"%PDF-second"],
        )

    def test_transformation_crosses_boundary_once_with_one_versioned_request(self) -> None:
        backend = FakeBackend()
        _set_backend_factory_for_testing(lambda: backend)
        ocr = OcrDocument(
            (
                OcrPage(
                    0,
                    0,
                    (OcrLine("Searchable", NormalizedRect(0.1, 0.2, 0.3, 0.04)),),
                ),
            )
        )

        output = add_ocr_text_layer(b"%PDF-input", ocr)

        self.assertEqual(output, b"%PDF-transformed")
        self.assertEqual(len(backend.transformation_calls), 1)
        pdf, request = backend.transformation_calls[0]
        self.assertEqual(pdf, b"%PDF-input")
        payload = json.loads(request)
        self.assertEqual(payload["schemaVersion"], 1)
        self.assertEqual(payload["pages"][0]["lines"][0]["text"], "Searchable")

    def test_rejects_an_incompatible_native_abi_before_initialization(self) -> None:
        backend = FakeBackend(abi_version=NATIVE_ABI_VERSION + 1)
        _set_backend_factory_for_testing(lambda: backend)

        with self.assertRaisesRegex(NativeLibraryError, "ABI version mismatch"):
            inspect_pdf(b"%PDF")

        self.assertEqual(backend.initialize_calls, 0)
        self.assertEqual(backend.close_calls, 1)

    def test_shutdown_closes_the_initialized_backend_once(self) -> None:
        backend = FakeBackend()
        _set_backend_factory_for_testing(lambda: backend)
        inspect_pdf(b"%PDF")

        _shutdown_runtime()
        _shutdown_runtime()

        self.assertEqual(backend.close_calls, 1)

    def test_native_domain_errors_are_preserved(self) -> None:
        class InvalidPdfBackend(FakeBackend):
            def inspect_pdf(self, pdf: bytes) -> bytes:
                raise InvalidPdfError("malformed PDF")

        _set_backend_factory_for_testing(InvalidPdfBackend)

        with self.assertRaisesRegex(InvalidPdfError, "malformed PDF"):
            inspect_pdf(b"%PDF")

    def test_calls_from_multiple_python_threads_are_serialized(self) -> None:
        backend = FakeBackend()
        _set_backend_factory_for_testing(lambda: backend)
        barrier = threading.Barrier(8)
        failures: list[BaseException] = []

        def inspect_from_thread() -> None:
            try:
                barrier.wait()
                inspect_pdf(b"%PDF")
            except BaseException as error:
                failures.append(error)

        threads = [threading.Thread(target=inspect_from_thread) for _ in range(8)]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()

        self.assertEqual(failures, [])
        self.assertEqual(len(backend.inspection_calls), 8)
        self.assertEqual(backend.maximum_active_calls, 1)

    def test_rejects_invalid_backend_results(self) -> None:
        class InvalidResultBackend(FakeBackend):
            def inspect_pdf(self, pdf: bytes) -> bytes:
                return bytearray(b"not bytes")  # type: ignore[return-value]

            def add_ocr_text_layer(self, pdf: bytes, request: bytes) -> bytes:
                return b""

        _set_backend_factory_for_testing(InvalidResultBackend)
        with self.assertRaisesRegex(NativeLibraryError, "non-bytes"):
            inspect_pdf(b"%PDF")
        with self.assertRaisesRegex(NativeLibraryError, "empty PDF"):
            add_ocr_text_layer(b"%PDF", OcrDocument(()))


if __name__ == "__main__":
    unittest.main()
