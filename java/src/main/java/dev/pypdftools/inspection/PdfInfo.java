package dev.pypdftools.inspection;

import java.util.List;
import java.util.Objects;

/** Basic page and encryption information for a PDF document. */
public record PdfInfo(int pageCount, List<PdfPageInfo> pages, boolean encrypted) {
    public PdfInfo {
        if (pageCount < 0) {
            throw new IllegalArgumentException("pageCount must not be negative");
        }
        pages = List.copyOf(Objects.requireNonNull(pages, "pages"));
        if (pages.size() != pageCount) {
            throw new IllegalArgumentException("pageCount must equal the number of pages");
        }
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            if (pages.get(pageIndex).pageIndex() != pageIndex) {
                throw new IllegalArgumentException("page indexes must be ordered and contiguous");
            }
        }
    }
}
