package dev.pypdftools.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pypdftools.error.InvalidPdfException;
import dev.pypdftools.error.PdfPasswordRequiredException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PdfInspectorTest {
    @Test
    void inspectsPortraitPageGeometry() throws Exception {
        byte[] pdf = PdfTestDocuments.onePage(
                new PDRectangle(612.0f, 792.0f),
                0,
                null);

        PdfInfo info = PdfInspector.inspect(pdf);

        assertEquals(1, info.pageCount());
        assertFalse(info.encrypted());
        PdfPageInfo page = info.pages().getFirst();
        assertEquals(0, page.pageIndex());
        assertEquals(612.0, page.widthPoints());
        assertEquals(792.0, page.heightPoints());
        assertEquals(0, page.rotation());
        assertEquals(new PdfBoxInfo(0.0, 0.0, 612.0, 792.0), page.cropBox());
        assertEquals(page.cropBox(), page.mediaBox());
    }

    @Test
    void inspectsEveryPageInOrder() throws Exception {
        PdfInfo info = PdfInspector.inspect(PdfTestDocuments.multipage());

        assertEquals(2, info.pageCount());
        assertEquals(0, info.pages().get(0).pageIndex());
        assertEquals(1, info.pages().get(1).pageIndex());
        assertEquals(300.0, info.pages().get(1).widthPoints());
        assertEquals(400.0, info.pages().get(1).heightPoints());
    }

    @ParameterizedTest
    @CsvSource({
        "0,612.0,792.0",
        "90,792.0,612.0",
        "180,612.0,792.0",
        "270,792.0,612.0",
        "-90,792.0,612.0",
        "450,792.0,612.0"
    })
    void appliesPageRotationToVisibleDimensions(
            int rotation,
            double expectedWidth,
            double expectedHeight) throws Exception {
        byte[] pdf = PdfTestDocuments.onePage(
                new PDRectangle(612.0f, 792.0f),
                rotation,
                null);

        PdfPageInfo page = PdfInspector.inspect(pdf).pages().getFirst();

        assertEquals(Math.floorMod(rotation, 360), page.rotation());
        assertEquals(expectedWidth, page.widthPoints());
        assertEquals(expectedHeight, page.heightPoints());
    }

    @Test
    void usesOffsetCropBoxForVisibleGeometry() throws Exception {
        PDRectangle mediaBox = new PDRectangle(0.0f, 0.0f, 612.0f, 792.0f);
        PDRectangle cropBox = new PDRectangle(10.0f, 20.0f, 400.0f, 600.0f);
        byte[] pdf = PdfTestDocuments.onePage(mediaBox, 90, cropBox);

        PdfPageInfo page = PdfInspector.inspect(pdf).pages().getFirst();

        assertEquals(600.0, page.widthPoints());
        assertEquals(400.0, page.heightPoints());
        assertEquals(new PdfBoxInfo(10.0, 20.0, 410.0, 620.0), page.cropBox());
        assertEquals(new PdfBoxInfo(0.0, 0.0, 612.0, 792.0), page.mediaBox());
    }

    @Test
    void reportsEncryptionWhenEmptyUserPasswordAllowsOpening() throws Exception {
        PdfInfo info = PdfInspector.inspect(PdfTestDocuments.encrypted(""));

        assertTrue(info.encrypted());
    }

    @Test
    void mapsPasswordProtectedPdfToSpecificException() throws IOException {
        byte[] pdf = PdfTestDocuments.encrypted("user-password");

        assertThrows(PdfPasswordRequiredException.class, () -> PdfInspector.inspect(pdf));
    }

    @Test
    void rejectsNullEmptyAndMalformedInput() {
        assertThrows(InvalidPdfException.class, () -> PdfInspector.inspect(null));
        assertThrows(InvalidPdfException.class, () -> PdfInspector.inspect(new byte[0]));
        assertThrows(
                InvalidPdfException.class,
                () -> PdfInspector.inspect("not a PDF".getBytes(StandardCharsets.UTF_8)));
    }
}
