package dev.pypdftools.ocr;

import dev.pypdftools.error.FontException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import org.apache.fontbox.ttf.PostScriptTable;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/** Loads the embeddable font shipped in both the JAR and native image. */
final class DefaultOcrFont {
    private static final String RESOURCE = "/dev/pypdftools/fonts/Unifont-Regular.ttf.gz";

    // The font's 'post' table carries no glyph names, which PDFBox reports as a warning on
    // every load. java.util.logging holds loggers weakly, so the reference must be kept.
    private static final Logger POST_TABLE_LOGGER =
            Logger.getLogger(PostScriptTable.class.getName());

    static {
        POST_TABLE_LOGGER.setLevel(Level.SEVERE);
    }

    private DefaultOcrFont() {}

    static PDType0Font load(PDDocument document) throws FontException {
        try (InputStream input = DefaultOcrFont.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new FontException("bundled OCR font resource is missing");
            }
            return PDType0Font.load(document, new GZIPInputStream(input), true);
        } catch (IOException | RuntimeException error) {
            throw new FontException("bundled OCR font could not be loaded", error);
        }
    }
}
