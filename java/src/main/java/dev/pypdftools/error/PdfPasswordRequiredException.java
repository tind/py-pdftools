package dev.pypdftools.error;

/** The supplied PDF cannot be opened without a password. */
public final class PdfPasswordRequiredException extends InvalidPdfException {
    public PdfPasswordRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
