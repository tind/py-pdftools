package dev.pypdftools.ocr;

import dev.pypdftools.error.PageMismatchException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One complete decoded OCR transformation request. */
public record OcrRequest(OcrOptions options, List<OcrPageData> pages) {
    public OcrRequest {
        if (options == null) {
            throw new NullPointerException("options");
        }
        pages = List.copyOf(pages);
    }

    public void validatePageMatch(int pdfPageCount) throws PageMismatchException {
        if (pdfPageCount < 0) {
            throw new IllegalArgumentException("pdfPageCount must not be negative");
        }
        if (!options.allowPartialDocument() && pages.size() != pdfPageCount) {
            throw new PageMismatchException(
                    "OCR page count " + pages.size()
                            + " does not match PDF page count " + pdfPageCount);
        }

        Set<Integer> pageIndexes = new HashSet<>();
        for (OcrPageData page : pages) {
            int pageIndex = page.pageIndex();
            if (pageIndex >= pdfPageCount) {
                throw new PageMismatchException(
                        "OCR page index " + pageIndex
                                + " does not exist in a " + pdfPageCount + "-page PDF");
            }
            pageIndexes.add(pageIndex);
        }
        if (!options.allowPartialDocument() && pageIndexes.size() != pdfPageCount) {
            throw new PageMismatchException(
                    "OCR page indexes must cover every PDF page exactly once");
        }
    }
}
