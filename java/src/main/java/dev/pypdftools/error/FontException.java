package dev.pypdftools.error;

/** OCR text cannot be represented with the bundled font. */
public final class FontException extends PdfToolsException {
    public FontException(String message) {
        super(message);
    }

    public FontException(String message, Throwable cause) {
        super(message, cause);
    }
}
