package dev.pypdftools.error;

/** PDFBox failed while transforming or saving a PDF. */
public final class PdfProcessingException extends PdfToolsException {
    public PdfProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
