package dev.pypdftools.inspection;

/** A rectangular PDF box in PDF user-space points. */
public record PdfBoxInfo(
        double lowerLeftX,
        double lowerLeftY,
        double upperRightX,
        double upperRightY) {

    public PdfBoxInfo {
        requireFinite(lowerLeftX, "lowerLeftX");
        requireFinite(lowerLeftY, "lowerLeftY");
        requireFinite(upperRightX, "upperRightX");
        requireFinite(upperRightY, "upperRightY");
        if (upperRightX <= lowerLeftX || upperRightY <= lowerLeftY) {
            throw new IllegalArgumentException("PDF box must have positive width and height");
        }
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
