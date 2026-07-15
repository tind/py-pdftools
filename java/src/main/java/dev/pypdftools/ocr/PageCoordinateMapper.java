package dev.pypdftools.ocr;

import org.apache.pdfbox.pdmodel.common.PDRectangle;

/** Maps visible top-left OCR coordinates into PDF page user space. */
public final class PageCoordinateMapper {
    private PageCoordinateMapper() {}

    public static TextRegion map(
            PDRectangle cropBox,
            int pageRotation,
            OcrBounds bounds,
            int ocrOrientation) {
        if (cropBox == null) {
            throw new IllegalArgumentException("cropBox must not be null");
        }
        requireOrientation(pageRotation, "page rotation");
        requireOrientation(ocrOrientation, "OCR orientation");

        double rawWidth = cropBox.getWidth();
        double rawHeight = cropBox.getHeight();
        if (!(rawWidth > 0.0) || !(rawHeight > 0.0)) {
            throw new IllegalArgumentException("crop box must have positive dimensions");
        }
        double visibleWidth = pageRotation == 90 || pageRotation == 270
                ? rawHeight
                : rawWidth;
        double visibleHeight = pageRotation == 90 || pageRotation == 270
                ? rawWidth
                : rawHeight;

        double visibleCenterX = (bounds.left() + bounds.width() / 2.0) * visibleWidth;
        double visibleCenterY = (bounds.top() + bounds.height() / 2.0) * visibleHeight;
        Point center = mapPoint(
                cropBox,
                pageRotation,
                visibleCenterX,
                visibleCenterY);

        Vector visibleBaseline = switch (ocrOrientation) {
            case 0 -> new Vector(1.0, 0.0);
            case 90 -> new Vector(0.0, 1.0);
            case 180 -> new Vector(-1.0, 0.0);
            case 270 -> new Vector(0.0, -1.0);
            default -> throw new IllegalStateException("orientation was already validated");
        };
        Vector visibleGlyphUp = switch (ocrOrientation) {
            case 0 -> new Vector(0.0, -1.0);
            case 90 -> new Vector(1.0, 0.0);
            case 180 -> new Vector(0.0, 1.0);
            case 270 -> new Vector(-1.0, 0.0);
            default -> throw new IllegalStateException("orientation was already validated");
        };
        Vector baseline = mapVector(pageRotation, visibleBaseline);
        Vector glyphUp = mapVector(pageRotation, visibleGlyphUp);

        boolean horizontalReading = ocrOrientation == 0 || ocrOrientation == 180;
        double readingLength = horizontalReading
                ? bounds.width() * visibleWidth
                : bounds.height() * visibleHeight;
        double lineThickness = horizontalReading
                ? bounds.height() * visibleHeight
                : bounds.width() * visibleWidth;
        return new TextRegion(
                center.x(),
                center.y(),
                baseline.x(),
                baseline.y(),
                glyphUp.x(),
                glyphUp.y(),
                readingLength,
                lineThickness);
    }

    private static Point mapPoint(
            PDRectangle cropBox,
            int pageRotation,
            double visibleX,
            double visibleY) {
        double left = cropBox.getLowerLeftX();
        double bottom = cropBox.getLowerLeftY();
        double width = cropBox.getWidth();
        double height = cropBox.getHeight();
        return switch (pageRotation) {
            case 0 -> new Point(left + visibleX, bottom + height - visibleY);
            case 90 -> new Point(left + visibleY, bottom + visibleX);
            case 180 -> new Point(left + width - visibleX, bottom + visibleY);
            case 270 -> new Point(left + width - visibleY, bottom + height - visibleX);
            default -> throw new IllegalStateException("rotation was already validated");
        };
    }

    private static Vector mapVector(int pageRotation, Vector vector) {
        return switch (pageRotation) {
            case 0 -> new Vector(vector.x(), -vector.y());
            case 90 -> new Vector(vector.y(), vector.x());
            case 180 -> new Vector(-vector.x(), vector.y());
            case 270 -> new Vector(-vector.y(), -vector.x());
            default -> throw new IllegalStateException("rotation was already validated");
        };
    }

    private static void requireOrientation(int value, String field) {
        if (value != 0 && value != 90 && value != 180 && value != 270) {
            throw new IllegalArgumentException(
                    field + " must be one of 0, 90, 180, or 270");
        }
    }

    private record Point(double x, double y) {}

    private record Vector(double x, double y) {}
}
