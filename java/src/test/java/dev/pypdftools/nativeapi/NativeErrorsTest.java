package dev.pypdftools.nativeapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NativeErrorsTest {
    @AfterEach
    void clearError() {
        NativeErrors.clear();
    }

    @Test
    void recordsStatusAndDiagnostic() {
        int status = NativeErrors.fail(NativeStatus.INVALID_PDF, " malformed PDF ");

        assertEquals(NativeStatus.INVALID_PDF, status);
        assertEquals("malformed PDF", NativeErrors.current());
    }

    @Test
    void usesThrowableTypeWhenMessageIsMissing() {
        NativeErrors.fail(NativeStatus.PDF_PROCESSING, new IllegalStateException());

        assertEquals("IllegalStateException", NativeErrors.current());

        NativeErrors.fail(
                NativeStatus.PDF_PROCESSING,
                new IllegalStateException("outer", new IllegalArgumentException("inner")));
        assertEquals(
                "IllegalStateException: outer caused by IllegalArgumentException: inner",
                NativeErrors.current());
    }

    @Test
    void diagnosticStateIsLocalToTheAttachedJavaThread() throws Exception {
        NativeErrors.fail(NativeStatus.INVALID_PDF, "main-thread error");
        AtomicReference<String> otherThreadError = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            NativeErrors.fail(NativeStatus.FONT_ERROR, "worker-thread error");
            otherThreadError.set(NativeErrors.current());
        });

        thread.start();
        thread.join();

        assertEquals("worker-thread error", otherThreadError.get());
        assertEquals("main-thread error", NativeErrors.current());
        assertNotEquals(otherThreadError.get(), NativeErrors.current());
    }
}
