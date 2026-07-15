package dev.pypdftools.error;

/** The supplied bytes are not a valid or supported PDF. */
public class InvalidPdfException extends PdfToolsException {
    public InvalidPdfException(String message) {
        super(message);
    }

    public InvalidPdfException(String message, Throwable cause) {
        super(message, cause);
    }
}
