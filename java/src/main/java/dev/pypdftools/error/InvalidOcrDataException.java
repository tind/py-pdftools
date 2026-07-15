package dev.pypdftools.error;

/** The native OCR request is malformed or contains invalid values. */
public class InvalidOcrDataException extends PdfToolsException {
    public InvalidOcrDataException(String message) {
        super(message);
    }

    public InvalidOcrDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
