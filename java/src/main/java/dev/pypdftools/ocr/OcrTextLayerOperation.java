package dev.pypdftools.ocr;

import dev.pypdftools.error.FontException;
import dev.pypdftools.error.InvalidOcrDataException;
import dev.pypdftools.error.InvalidPdfException;
import dev.pypdftools.error.PageMismatchException;
import dev.pypdftools.error.PdfPasswordRequiredException;
import dev.pypdftools.error.PdfPermissionException;
import dev.pypdftools.error.PdfProcessingException;

/** One-shot OCR operation consumed by the native entry point. */
public final class OcrTextLayerOperation {
    private OcrTextLayerOperation() {}

    public static byte[] transform(byte[] pdfData, byte[] requestData)
            throws InvalidOcrDataException,
                    PageMismatchException,
                    InvalidPdfException,
                    PdfPasswordRequiredException,
                    PdfPermissionException,
                    FontException,
                    PdfProcessingException {
        return OcrTextLayerTransformer.transform(
                pdfData,
                OcrRequestDecoder.decode(requestData));
    }
}
