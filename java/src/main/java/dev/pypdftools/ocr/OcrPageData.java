package dev.pypdftools.ocr;

import java.util.List;

/** OCR lines associated with one zero-based PDF page. */
public record OcrPageData(int pageIndex, int orientation, List<OcrLineData> lines) {
    public OcrPageData {
        if (pageIndex < 0) {
            throw new IllegalArgumentException("pageIndex must be at least 0");
        }
        if (orientation != 0
                && orientation != 90
                && orientation != 180
                && orientation != 270) {
            throw new IllegalArgumentException(
                    "orientation must be one of 0, 90, 180, or 270");
        }
        lines = List.copyOf(lines);
    }
}
