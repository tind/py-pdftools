package dev.pypdftools.ocr;

import dev.pypdftools.error.FontException;
import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/** Loads the embeddable font shipped in both the JAR and native image. */
final class DefaultOcrFont {
    private static final String RESOURCE = "/dev/pypdftools/fonts/NotoSans-Regular.ttf";

    private DefaultOcrFont() {}

    static PDType0Font load(PDDocument document) throws FontException {
        try (InputStream input = DefaultOcrFont.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new FontException("bundled OCR font resource is missing");
            }
            return PDType0Font.load(document, input, true);
        } catch (IOException | RuntimeException error) {
            throw new FontException("bundled OCR font could not be loaded", error);
        }
    }
}
