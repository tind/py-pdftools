package dev.pypdftools.ocr;

/** A visible OCR rectangle expressed as an oriented basis in PDF user space. */
public record TextRegion(
        double centerX,
        double centerY,
        double baselineX,
        double baselineY,
        double glyphUpX,
        double glyphUpY,
        double readingLength,
        double lineThickness) {}
