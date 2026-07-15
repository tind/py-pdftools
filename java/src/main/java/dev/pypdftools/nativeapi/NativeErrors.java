package dev.pypdftools.nativeapi;

/** Per-attached-thread diagnostic state for the native ABI. */
final class NativeErrors {
    private static final ThreadLocal<String> LAST_ERROR =
            ThreadLocal.withInitial(() -> "native PDF operation failed");

    private NativeErrors() {}

    static void clear() {
        LAST_ERROR.remove();
    }

    static int fail(int status, String message) {
        String diagnostic = message == null ? "" : message.strip();
        LAST_ERROR.set(diagnostic.isEmpty() ? "native PDF operation failed" : diagnostic);
        return status;
    }

    static int fail(int status, Throwable error) {
        StringBuilder diagnostic = new StringBuilder();
        Throwable current = error;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (depth > 0) {
                diagnostic.append(" caused by ");
            }
            diagnostic.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                diagnostic.append(": ").append(message.strip());
            }
            current = current.getCause();
        }
        return fail(status, diagnostic.toString());
    }

    static String current() {
        return LAST_ERROR.get();
    }
}
