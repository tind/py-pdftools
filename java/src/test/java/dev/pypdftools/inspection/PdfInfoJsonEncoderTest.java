package dev.pypdftools.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PdfInfoJsonEncoderTest {
    @Test
    void encodesInspectionSchemaVersionOneWithoutWhitespace() throws Exception {
        byte[] pdf = PdfTestDocuments.onePage(
                new org.apache.pdfbox.pdmodel.common.PDRectangle(200.0f, 100.0f),
                0,
                null);

        String json = new String(PdfInspectionOperation.inspect(pdf), StandardCharsets.UTF_8);

        assertEquals(
                "{\"schemaVersion\":1,\"pageCount\":1,\"encrypted\":false,\"pages\":[{"
                        + "\"pageIndex\":0,\"widthPoints\":200.0,\"heightPoints\":100.0,"
                        + "\"rotation\":0,\"cropBox\":{\"lowerLeftX\":0.0,\"lowerLeftY\":0.0,"
                        + "\"upperRightX\":200.0,\"upperRightY\":100.0},\"mediaBox\":{"
                        + "\"lowerLeftX\":0.0,\"lowerLeftY\":0.0,\"upperRightX\":200.0,"
                        + "\"upperRightY\":100.0}}]}",
                json);
    }
}
