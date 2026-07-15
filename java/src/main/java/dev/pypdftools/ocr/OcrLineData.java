package dev.pypdftools.ocr;

import java.util.Objects;

/** One decoded OCR line. */
public record OcrLineData(String text, OcrBounds bounds, Double confidence) {
    public OcrLineData {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(bounds, "bounds");
        if (confidence != null
                && (!Double.isFinite(confidence)
                        || confidence < 0.0
                        || confidence > 100.0)) {
            throw new IllegalArgumentException(
                    "confidence must be between 0.0 and 100.0");
        }
    }
}
