package dev.pypdftools.ocr;

/** One normalized rectangle in visible-page coordinates. */
public record OcrBounds(double left, double top, double width, double height) {
    private static final double TOLERANCE = 1e-6;

    public OcrBounds {
        requireFinite(left, "left");
        requireFinite(top, "top");
        requireFinite(width, "width");
        requireFinite(height, "height");
        if (width <= 0.0) {
            throw new IllegalArgumentException("width must be greater than 0.0");
        }
        if (height <= 0.0) {
            throw new IllegalArgumentException("height must be greater than 0.0");
        }

        double right = left + width;
        double bottom = top + height;
        if (left < -TOLERANCE || top < -TOLERANCE) {
            throw new IllegalArgumentException(
                    "rectangle starts outside the normalized page");
        }
        if (right > 1.0 + TOLERANCE || bottom > 1.0 + TOLERANCE) {
            throw new IllegalArgumentException(
                    "rectangle ends outside the normalized page");
        }

        double clampedLeft = clamp(left);
        double clampedTop = clamp(top);
        double clampedRight = clamp(right);
        double clampedBottom = clamp(bottom);
        left = clampedLeft;
        top = clampedTop;
        width = clampedRight - clampedLeft;
        height = clampedBottom - clampedTop;
        if (width <= 0.0 || height <= 0.0) {
            throw new IllegalArgumentException(
                    "rectangle must overlap the normalized page");
        }
    }

    private static double clamp(double value) {
        return Math.min(1.0, Math.max(0.0, value));
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
