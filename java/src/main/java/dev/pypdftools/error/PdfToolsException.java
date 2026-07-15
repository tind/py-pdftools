package dev.pypdftools.error;

/** Base checked exception for failures in the Java transformation library. */
public class PdfToolsException extends Exception {
    public PdfToolsException(String message) {
        super(message);
    }

    public PdfToolsException(String message, Throwable cause) {
        super(message, cause);
    }
}
