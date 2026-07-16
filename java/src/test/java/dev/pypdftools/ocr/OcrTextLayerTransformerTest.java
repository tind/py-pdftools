package dev.pypdftools.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pypdftools.error.FontException;
import dev.pypdftools.error.InvalidPdfException;
import dev.pypdftools.error.PageMismatchException;
import dev.pypdftools.error.PdfPasswordRequiredException;
import dev.pypdftools.error.PdfPermissionException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OcrTextLayerTransformerTest {
    private static final OcrBounds DEFAULT_BOUNDS =
            new OcrBounds(0.10, 0.20, 0.60, 0.08);
    private static final OcrBounds VERTICAL_BOUNDS =
            new OcrBounds(0.20, 0.15, 0.08, 0.70);
    private static final PDRectangle OFFSET_CROP_BOX =
            new PDRectangle(10.0f, 20.0f, 400.0f, 600.0f);

    @Test
    void appendsInvisibleExtractableUnicodeTextAndEmbedsSubsetFont() throws Exception {
        PDRectangle cropBox = new PDRectangle(10.0f, 20.0f, 400.0f, 600.0f);
        byte[] input = pdfWithExistingText(
                new PDRectangle(612.0f, 792.0f),
                cropBox,
                90,
                "Existing text");
        String ocrText = "OCR café Ω Привет";

        byte[] output = OcrTextLayerTransformer.transform(
                input,
                request(false, null, false, page(0, 0, line(ocrText, 99.0))));

        try (PDDocument document = Loader.loadPDF(output)) {
            assertEquals(1, document.getNumberOfPages());
            assertEquals("Source title", document.getDocumentInformation().getTitle());
            assertEquals(
                    "preserved",
                    document.getDocumentInformation().getCustomMetadataValue("test-key"));
            PDPage page = document.getPage(0);
            assertEquals(90, page.getRotation());
            assertRectangleEquals(cropBox, page.getCropBox());
            assertRectangleEquals(new PDRectangle(612.0f, 792.0f), page.getMediaBox());

            String extracted = new PDFTextStripper().getText(document);
            assertTrue(extracted.replaceAll("\\s+", "").contains("Existingtext"), extracted);
            assertTrue(extracted.contains(ocrText), extracted);
            assertTrue(renderingModes(page).contains(3));

            List<PDFont> fonts = new ArrayList<>();
            for (var name : page.getResources().getFontNames()) {
                fonts.add(page.getResources().getFont(name));
            }
            PDType0Font ocrFont = assertInstanceOf(
                    PDType0Font.class,
                    fonts.stream()
                            .filter(PDType0Font.class::isInstance)
                            .findFirst()
                            .orElseThrow());
            assertTrue(ocrFont.isEmbedded());
            assertTrue(ocrFont.getName().contains("NotoSans"));
        }
    }

    @Test
    void debugModeUsesVisibleFillRendering() throws Exception {
        byte[] output = OcrTextLayerTransformer.transform(
                blankPdf(1, 0),
                request(false, null, true, page(0, 0, line("Debug", null))));

        try (PDDocument document = Loader.loadPDF(output)) {
            assertTrue(renderingModes(document.getPage(0)).contains(0));
            assertFalse(renderingModes(document.getPage(0)).contains(3));
        }
    }

    @Test
    void skipsBlankAndLowConfidenceLinesButKeepsMissingConfidence() throws Exception {
        OcrPageData page = page(
                0,
                0,
                line("Low confidence", 79.9),
                line("High confidence", 80.0),
                line(
                        "No confidence",
                        new OcrBounds(0.10, 0.35, 0.60, 0.08),
                        null),
                line("", 100.0),
                line("   ", 100.0));

        byte[] output = OcrTextLayerTransformer.transform(
                blankPdf(1, 0),
                request(false, 80.0, false, page));

        try (PDDocument document = Loader.loadPDF(output)) {
            String extracted = new PDFTextStripper().getText(document);
            assertFalse(extracted.contains("Low confidence"));
            assertTrue(extracted.contains("High confidence"));
            assertTrue(extracted.contains("No confidence"));
        }
    }

    @Test
    void partialRequestLeavesMissingPagesWithoutOcrText() throws Exception {
        byte[] output = OcrTextLayerTransformer.transform(
                blankPdf(2, 0),
                request(true, null, false, page(1, 0, line("Second page OCR", null))));

        try (PDDocument document = Loader.loadPDF(output)) {
            PDFTextStripper firstPage = new PDFTextStripper();
            firstPage.setStartPage(1);
            firstPage.setEndPage(1);
            PDFTextStripper secondPage = new PDFTextStripper();
            secondPage.setStartPage(2);
            secondPage.setEndPage(2);
            assertEquals("", firstPage.getText(document).strip());
            assertTrue(secondPage.getText(document).contains("Second page OCR"));
        }
    }

    @ParameterizedTest
    @CsvSource({
        "0,0", "0,90", "0,180", "0,270",
        "90,0", "90,90", "90,180", "90,270",
        "180,0", "180,90", "180,180", "180,270",
        "270,0", "270,90", "270,180", "270,270"
    })
    void writesTextForEveryPageRotationAndOcrOrientation(
            int pageRotation,
            int ocrOrientation) throws Exception {
        String text = "Rotation " + pageRotation + " orientation " + ocrOrientation;

        byte[] output = OcrTextLayerTransformer.transform(
                blankPdf(1, pageRotation),
                request(false, null, false, page(0, ocrOrientation, line(text, null))));

        try (PDDocument document = Loader.loadPDF(output)) {
            assertTrue(new PDFTextStripper().getText(document).contains(text));
        }
    }

    @ParameterizedTest
    @CsvSource({
        "0,90", "0,270",
        "90,90", "90,270",
        "180,90", "180,270",
        "270,90", "270,270"
    })
    void preservesVerticalSelectionOrderAndFirstToLastGlyphDirection(
            int pageRotation,
            int ocrOrientation) throws Exception {
        String text = "First to last";
        byte[] input = blankPdf(
                PDRectangle.LETTER,
                OFFSET_CROP_BOX,
                pageRotation);

        byte[] output = OcrTextLayerTransformer.transform(
                input,
                request(
                        false,
                        null,
                        false,
                        page(0, ocrOrientation, line(text, VERTICAL_BOUNDS, null))));

        try (PDDocument document = Loader.loadPDF(output)) {
            TextPositionCollector collector = new TextPositionCollector();
            collector.getText(document);
            List<TextPosition> positions = collector.positions();

            assertEquals(text, collector.unicodeInSelectionOrder());
            assertEquals(text.codePointCount(0, text.length()), positions.size());
            TextPosition first = positions.getFirst();
            TextPosition last = positions.getLast();
            assertEquals("F", first.getUnicode());
            assertEquals("t", last.getUnicode());
            assertEquals(first.getX(), last.getX(), 0.1);

            double visibleHeight = pageRotation == 90 || pageRotation == 270
                    ? OFFSET_CROP_BOX.getWidth()
                    : OFFSET_CROP_BOX.getHeight();
            double top = VERTICAL_BOUNDS.top() * visibleHeight;
            double bottom = (VERTICAL_BOUNDS.top() + VERTICAL_BOUNDS.height())
                    * visibleHeight;
            if (ocrOrientation == 90) {
                assertEquals(top, first.getY(), 0.1);
                assertTrue(first.getY() < last.getY());
                assertTrue(last.getY() < bottom);
            } else {
                assertEquals(bottom, first.getY(), 0.1);
                assertTrue(first.getY() > last.getY());
                assertTrue(last.getY() > top);
            }
        }
    }

    @Test
    void rejectsUnsupportedGlyphsInsteadOfSubstituting() throws Exception {
        OcrRequest request = request(
                false,
                null,
                false,
                page(0, 0, line("Unsupported \u6f22 glyph", null)));

        assertThrows(
                FontException.class,
                () -> OcrTextLayerTransformer.transform(blankPdf(1, 0), request));
    }

    @Test
    void enforcesPageMatchingBeforeWriting() throws Exception {
        OcrRequest missingPage = request(
                false,
                null,
                false,
                page(0, 0, line("Only first", null)));

        assertThrows(
                PageMismatchException.class,
                () -> OcrTextLayerTransformer.transform(blankPdf(2, 0), missingPage));
    }

    @Test
    void mapsPasswordAndPermissionFailures() throws Exception {
        OcrRequest request = request(
                false,
                null,
                false,
                page(0, 0, line("OCR", null)));

        assertThrows(
                PdfPasswordRequiredException.class,
                () -> OcrTextLayerTransformer.transform(
                        encryptedPdf("user-password", true),
                        request));
        assertThrows(
                PdfPermissionException.class,
                () -> OcrTextLayerTransformer.transform(
                        encryptedPdf("", false),
                        request));
    }

    @Test
    void rejectsNullEmptyAndMalformedPdfData() {
        OcrRequest request = request(false, null, false);

        assertThrows(
                InvalidPdfException.class,
                () -> OcrTextLayerTransformer.transform(null, request));
        assertThrows(
                InvalidPdfException.class,
                () -> OcrTextLayerTransformer.transform(new byte[0], request));
        assertThrows(
                InvalidPdfException.class,
                () -> OcrTextLayerTransformer.transform(
                        "not a PDF".getBytes(StandardCharsets.UTF_8),
                        request));
    }

    private static OcrRequest request(
            boolean allowPartial,
            Double minimumConfidence,
            boolean debugVisibleText,
            OcrPageData... pages) {
        return new OcrRequest(
                new OcrOptions(allowPartial, minimumConfidence, debugVisibleText),
                List.of(pages));
    }

    private static OcrPageData page(
            int pageIndex,
            int orientation,
            OcrLineData... lines) {
        return new OcrPageData(pageIndex, orientation, List.of(lines));
    }

    private static OcrLineData line(String text, Double confidence) {
        return new OcrLineData(text, DEFAULT_BOUNDS, confidence);
    }

    private static OcrLineData line(
            String text,
            OcrBounds bounds,
            Double confidence) {
        return new OcrLineData(text, bounds, confidence);
    }

    private static void assertRectangleEquals(PDRectangle expected, PDRectangle actual) {
        assertEquals(expected.getLowerLeftX(), actual.getLowerLeftX());
        assertEquals(expected.getLowerLeftY(), actual.getLowerLeftY());
        assertEquals(expected.getUpperRightX(), actual.getUpperRightX());
        assertEquals(expected.getUpperRightY(), actual.getUpperRightY());
    }

    private static byte[] blankPdf(int pageCount, int rotation) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int index = 0; index < pageCount; index++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                page.setRotation(rotation);
                document.addPage(page);
            }
            return save(document);
        }
    }

    private static byte[] blankPdf(
            PDRectangle mediaBox,
            PDRectangle cropBox,
            int rotation) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(mediaBox);
            page.setCropBox(cropBox);
            page.setRotation(rotation);
            document.addPage(page);
            return save(document);
        }
    }

    private static byte[] pdfWithExistingText(
            PDRectangle mediaBox,
            PDRectangle cropBox,
            int rotation,
            String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.getDocumentInformation().setTitle("Source title");
            document.getDocumentInformation().setCustomMetadataValue("test-key", "preserved");
            PDPage page = new PDPage(mediaBox);
            page.setCropBox(cropBox);
            page.setRotation(rotation);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(FontName.HELVETICA), 12.0f);
                stream.newLineAtOffset(72.0f, 500.0f);
                stream.showText(text);
                stream.endText();
            }
            return save(document);
        }
    }

    private static byte[] encryptedPdf(String userPassword, boolean canModify)
            throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.LETTER));
            AccessPermission permissions = new AccessPermission();
            permissions.setCanModify(canModify);
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                    "owner-password",
                    userPassword,
                    permissions);
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            return save(document);
        }
    }

    private static List<Integer> renderingModes(PDPage page) throws IOException {
        List<Object> tokens = new PDFStreamParser(page).parse();
        List<Integer> modes = new ArrayList<>();
        for (int index = 1; index < tokens.size(); index++) {
            Object token = tokens.get(index);
            if (token instanceof Operator operator
                    && "Tr".equals(operator.getName())
                    && tokens.get(index - 1) instanceof COSNumber number) {
                modes.add(number.intValue());
            }
        }
        return modes;
    }

    private static byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.save(output);
        return output.toByteArray();
    }

    private static final class TextPositionCollector extends PDFTextStripper {
        private final List<TextPosition> positions = new ArrayList<>();

        private TextPositionCollector() throws IOException {}

        @Override
        protected void processTextPosition(TextPosition text) {
            positions.add(text);
            super.processTextPosition(text);
        }

        private List<TextPosition> positions() {
            return List.copyOf(positions);
        }

        private String unicodeInSelectionOrder() {
            StringBuilder text = new StringBuilder();
            for (TextPosition position : positions) {
                text.append(position.getUnicode());
            }
            return text.toString();
        }
    }
}
