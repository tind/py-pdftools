package dev.pypdftools.ocr;

/** Options controlling OCR page matching, filtering, and diagnostic rendering. */
public record OcrOptions(
        boolean allowPartialDocument,
        Double minimumConfidence,
        boolean debugVisibleText) {
    public OcrOptions {
        if (minimumConfidence != null
                && (!Double.isFinite(minimumConfidence)
                        || minimumConfidence < 0.0
                        || minimumConfidence > 100.0)) {
            throw new IllegalArgumentException(
                    "minimumConfidence must be between 0.0 and 100.0");
        }
    }
}
