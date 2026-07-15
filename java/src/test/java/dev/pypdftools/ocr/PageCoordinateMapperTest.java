package dev.pypdftools.ocr;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PageCoordinateMapperTest {
    private static final double TOLERANCE = 1e-9;
    private static final PDRectangle OFFSET_CROP_BOX =
            new PDRectangle(10.0f, 20.0f, 400.0f, 600.0f);
    private static final OcrBounds BOUNDS = new OcrBounds(0.25, 0.20, 0.20, 0.10);

    @ParameterizedTest
    @CsvSource({
        "0,150,470,1,0,0,1,80,60",
        "90,110,230,0,1,-1,0,120,40",
        "180,270,170,-1,0,0,-1,80,60",
        "270,310,410,0,-1,1,0,120,40"
    })
    void mapsCropOffsetAndEveryPdfPageRotation(
            int pageRotation,
            double centerX,
            double centerY,
            double baselineX,
            double baselineY,
            double glyphUpX,
            double glyphUpY,
            double readingLength,
            double lineThickness) {
        TextRegion result = PageCoordinateMapper.map(
                OFFSET_CROP_BOX,
                pageRotation,
                BOUNDS,
                0);

        assertRegion(
                result,
                centerX,
                centerY,
                baselineX,
                baselineY,
                glyphUpX,
                glyphUpY,
                readingLength,
                lineThickness);
    }

    @ParameterizedTest
    @CsvSource({
        "0,1,0,0,1,80,60",
        "90,0,-1,1,0,60,80",
        "180,-1,0,0,-1,80,60",
        "270,0,1,-1,0,60,80"
    })
    void mapsEveryOcrOrientationOnAnUnrotatedPage(
            int ocrOrientation,
            double baselineX,
            double baselineY,
            double glyphUpX,
            double glyphUpY,
            double readingLength,
            double lineThickness) {
        TextRegion result = PageCoordinateMapper.map(
                OFFSET_CROP_BOX,
                0,
                BOUNDS,
                ocrOrientation);

        assertRegion(
                result,
                150,
                470,
                baselineX,
                baselineY,
                glyphUpX,
                glyphUpY,
                readingLength,
                lineThickness);
    }

    @Test
    void rejectsInvalidPageGeometryAndOrientations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PageCoordinateMapper.map(null, 0, BOUNDS, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> PageCoordinateMapper.map(OFFSET_CROP_BOX, 45, BOUNDS, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> PageCoordinateMapper.map(OFFSET_CROP_BOX, 0, BOUNDS, 45));
        assertThrows(
                IllegalArgumentException.class,
                () -> PageCoordinateMapper.map(
                        new PDRectangle(0.0f, 0.0f, 0.0f, 10.0f),
                        0,
                        BOUNDS,
                        0));
    }

    private static void assertRegion(
            TextRegion result,
            double centerX,
            double centerY,
            double baselineX,
            double baselineY,
            double glyphUpX,
            double glyphUpY,
            double readingLength,
            double lineThickness) {
        assertAll(
                () -> assertEquals(centerX, result.centerX(), TOLERANCE),
                () -> assertEquals(centerY, result.centerY(), TOLERANCE),
                () -> assertEquals(baselineX, result.baselineX(), TOLERANCE),
                () -> assertEquals(baselineY, result.baselineY(), TOLERANCE),
                () -> assertEquals(glyphUpX, result.glyphUpX(), TOLERANCE),
                () -> assertEquals(glyphUpY, result.glyphUpY(), TOLERANCE),
                () -> assertEquals(readingLength, result.readingLength(), TOLERANCE),
                () -> assertEquals(lineThickness, result.lineThickness(), TOLERANCE));
    }
}
