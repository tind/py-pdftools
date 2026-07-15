package dev.pypdftools.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PdfInfoTest {
    private static final PdfBoxInfo LETTER = new PdfBoxInfo(0.0, 0.0, 612.0, 792.0);

    @Test
    void defensivelyCopiesThePageList() {
        List<PdfPageInfo> pages = new ArrayList<>();
        pages.add(new PdfPageInfo(0, 612.0, 792.0, 0, LETTER, LETTER));

        PdfInfo info = new PdfInfo(1, pages, false);
        pages.clear();

        assertEquals(1, info.pages().size());
        assertThrows(UnsupportedOperationException.class, () -> info.pages().clear());
    }

    @Test
    void rejectsInconsistentPageCountAndIndexes() {
        PdfPageInfo page = new PdfPageInfo(1, 612.0, 792.0, 0, LETTER, LETTER);

        assertThrows(IllegalArgumentException.class, () -> new PdfInfo(0, List.of(page), false));
        assertThrows(IllegalArgumentException.class, () -> new PdfInfo(1, List.of(page), false));
    }

    @Test
    void rejectsInvalidBoxAndPageGeometry() {
        assertThrows(IllegalArgumentException.class, () -> new PdfBoxInfo(0.0, 0.0, 0.0, 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PdfPageInfo(0, 612.0, 792.0, 45, LETTER, LETTER));
    }
}
