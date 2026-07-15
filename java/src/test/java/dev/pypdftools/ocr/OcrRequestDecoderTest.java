package dev.pypdftools.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pypdftools.error.InvalidOcrDataException;
import dev.pypdftools.error.PageMismatchException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OcrRequestDecoderTest {
    private static final String DEFAULT_OPTIONS =
            "{\"allowPartialDocument\":false,"
                    + "\"minimumConfidence\":null,\"debugVisibleText\":false}";
    private static final String VALID_LINE =
            "{\"text\":\"Grüße 😀\",\"confidence\":98.4,"
                    + "\"bounds\":{\"left\":0.1,\"top\":0.2,"
                    + "\"width\":0.4,\"height\":0.03}}";

    @Test
    void decodesCompleteUtf8Request() throws Exception {
        OcrRequest request = decode(request(
                DEFAULT_OPTIONS,
                "[{\"pageIndex\":0,\"orientation\":90,\"lines\":["
                        + VALID_LINE + "]}]"));

        assertFalse(request.options().allowPartialDocument());
        assertEquals(null, request.options().minimumConfidence());
        assertFalse(request.options().debugVisibleText());
        OcrPageData page = request.pages().getFirst();
        assertEquals(0, page.pageIndex());
        assertEquals(90, page.orientation());
        OcrLineData line = page.lines().getFirst();
        assertEquals("Grüße 😀", line.text());
        assertEquals(98.4, line.confidence());
        assertEquals(new OcrBounds(0.1, 0.2, 0.4, 0.03), line.bounds());
    }

    @Test
    void clampsCoordinatesWithinTolerance() throws Exception {
        String line =
                "{\"text\":\"edge\",\"confidence\":null,"
                        + "\"bounds\":{\"left\":-0.0000005,\"top\":0,"
                        + "\"width\":0.5,\"height\":1.0000005}}";
        OcrRequest request = decode(request(
                DEFAULT_OPTIONS,
                "[{\"pageIndex\":0,\"orientation\":0,\"lines\":["
                        + line + "]}]"));

        OcrBounds bounds = request.pages().getFirst().lines().getFirst().bounds();
        assertEquals(0.0, bounds.left());
        assertEquals(0.4999995, bounds.width(), 1e-12);
        assertEquals(1.0, bounds.height());
    }

    @Test
    void rejectsMalformedJsonUtf8AndDuplicateKeys() {
        assertThrows(InvalidOcrDataException.class, () -> decode("{"));
        assertThrows(
                InvalidOcrDataException.class,
                () -> OcrRequestDecoder.decode(new byte[] {(byte) 0xc3, 0x28}));
        assertThrows(
                InvalidOcrDataException.class,
                () -> decode("{\"schemaVersion\":1,\"schemaVersion\":1}"));
        assertThrows(
                InvalidOcrDataException.class,
                () -> decode("{\"value\":\"\\uD800\"}"));
    }

    @Test
    void rejectsSchemaAndStructuralMismatches() {
        assertThrows(
                InvalidOcrDataException.class,
                () -> decode(request(DEFAULT_OPTIONS, "[]").replace(
                        "\"schemaVersion\":1",
                        "\"schemaVersion\":2")));
        assertThrows(
                InvalidOcrDataException.class,
                () -> decode(request(DEFAULT_OPTIONS, "[]").replace(
                        "\"schemaVersion\":1",
                        "\"schemaVersion\":1.0")));
        assertThrows(
                InvalidOcrDataException.class,
                () -> decode(request(DEFAULT_OPTIONS, "[]").replace(
                        "\"pages\":[]",
                        "\"pages\":[],\"extra\":true")));
        assertThrows(
                InvalidOcrDataException.class,
                () -> decode("{\"schemaVersion\":1,\"options\":"
                        + DEFAULT_OPTIONS + "}"));
    }

    @Test
    void independentlyRejectsInvalidOcrValues() {
        String page = "[{\"pageIndex\":0,\"orientation\":0,\"lines\":["
                + VALID_LINE + "]}]";
        assertThrows(
                InvalidOcrDataException.class,
                () -> decode(request(DEFAULT_OPTIONS, page).replace(
                        "\"orientation\":0",
                        "\"orientation\":45")));
        assertThrows(
                InvalidOcrDataException.class,
                () -> decode(request(DEFAULT_OPTIONS, page).replace(
                        "\"confidence\":98.4",
                        "\"confidence\":101")));
        assertThrows(
                InvalidOcrDataException.class,
                () -> decode(request(DEFAULT_OPTIONS, page).replace(
                        "\"width\":0.4",
                        "\"width\":0")));
        assertThrows(
                InvalidOcrDataException.class,
                () -> decode(request(DEFAULT_OPTIONS, page).replace(
                        "\"left\":0.1",
                        "\"left\":0.9")));
    }

    @Test
    void rejectsDuplicatePageIndexes() {
        String page = "{\"pageIndex\":0,\"orientation\":0,\"lines\":[]}";
        assertThrows(
                InvalidOcrDataException.class,
                () -> decode(request(DEFAULT_OPTIONS, "[" + page + "," + page + "]")));
    }

    @Test
    void validatesCompleteDocumentPageMatching() throws Exception {
        OcrRequest request = decode(request(
                DEFAULT_OPTIONS,
                "[{\"pageIndex\":1,\"orientation\":0,\"lines\":[]},"
                        + "{\"pageIndex\":0,\"orientation\":0,\"lines\":[]}]"));

        request.validatePageMatch(2);
        assertThrows(PageMismatchException.class, () -> request.validatePageMatch(3));
        assertThrows(PageMismatchException.class, () -> request.validatePageMatch(1));
    }

    @Test
    void permitsValidPartialDocumentsButRejectsExtraPages() throws Exception {
        String options = DEFAULT_OPTIONS.replace(
                "\"allowPartialDocument\":false",
                "\"allowPartialDocument\":true");
        OcrRequest request = decode(request(
                options,
                "[{\"pageIndex\":2,\"orientation\":270,\"lines\":[]}]"));

        request.validatePageMatch(3);
        assertTrue(request.options().allowPartialDocument());
        assertThrows(PageMismatchException.class, () -> request.validatePageMatch(2));
    }

    private static OcrRequest decode(String request) throws InvalidOcrDataException {
        return OcrRequestDecoder.decode(request.getBytes(StandardCharsets.UTF_8));
    }

    private static String request(String options, String pages) {
        return "{\"schemaVersion\":1,\"options\":" + options
                + ",\"pages\":" + pages + "}";
    }
}
