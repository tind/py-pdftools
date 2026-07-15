package dev.pypdftools.error;

/** The PDF security settings prohibit modification. */
public final class PdfPermissionException extends PdfToolsException {
    public PdfPermissionException(String message) {
        super(message);
    }
}
