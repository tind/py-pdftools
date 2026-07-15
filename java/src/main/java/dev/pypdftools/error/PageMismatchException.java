package dev.pypdftools.error;

/** OCR page records do not match the pages in the supplied PDF. */
public final class PageMismatchException extends InvalidOcrDataException {
    public PageMismatchException(String message) {
        super(message);
    }
}
