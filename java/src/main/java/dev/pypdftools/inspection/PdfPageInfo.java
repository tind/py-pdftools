package dev.pypdftools.inspection;

import java.util.Objects;

/** Visible dimensions, rotation, and page boxes for one zero-based PDF page. */
public record PdfPageInfo(
        int pageIndex,
        double widthPoints,
        double heightPoints,
        int rotation,
        PdfBoxInfo cropBox,
        PdfBoxInfo mediaBox) {

    public PdfPageInfo {
        if (pageIndex < 0) {
            throw new IllegalArgumentException("pageIndex must not be negative");
        }
        if (!Double.isFinite(widthPoints)
                || !Double.isFinite(heightPoints)
                || widthPoints <= 0.0
                || heightPoints <= 0.0) {
            throw new IllegalArgumentException("page dimensions must be finite and positive");
        }
        if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) {
            throw new IllegalArgumentException("rotation must be 0, 90, 180, or 270");
        }
        Objects.requireNonNull(cropBox, "cropBox");
        Objects.requireNonNull(mediaBox, "mediaBox");
    }
}
