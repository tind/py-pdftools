package dev.pypdftools.inspection;

import dev.pypdftools.error.InvalidPdfException;
import dev.pypdftools.error.PdfPasswordRequiredException;

/** One-shot inspection operation consumed by the native entry point. */
public final class PdfInspectionOperation {
    private PdfInspectionOperation() {}

    public static byte[] inspect(byte[] pdfData)
            throws InvalidPdfException, PdfPasswordRequiredException {
        return PdfInfoJsonEncoder.encode(PdfInspector.inspect(pdfData));
    }
}
