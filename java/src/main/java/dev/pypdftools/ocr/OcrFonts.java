package dev.pypdftools.ocr;

import dev.pypdftools.error.FontException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * The embeddable fonts shipped in both the JAR and native image, loaded into one document.
 *
 * <p>Each code point is drawn with the first font that has a glyph for it. Noto Sans is the
 * primary font and supplies the line metrics; GNU Unifont covers the rest of the Basic
 * Multilingual Plane and is only loaded into documents that need it.
 */
final class OcrFonts {
    private static final List<Resource> RESOURCES = List.of(
            new Resource("/dev/pypdftools/fonts/NotoSans-Regular.ttf", false),
            new Resource("/dev/pypdftools/fonts/Unifont-Regular.ttf.gz", true));

    private final PDDocument document;
    private final PDType0Font[] loaded = new PDType0Font[RESOURCES.size()];
    private final Map<Integer, PDType0Font> fontByCodePoint = new HashMap<>();

    private OcrFonts(PDDocument document) {
        this.document = document;
    }

    static OcrFonts load(PDDocument document) throws FontException {
        OcrFonts fonts = new OcrFonts(document);
        fonts.font(0);
        return fonts;
    }

    PDType0Font primary() {
        return loaded[0];
    }

    /** Splits text into maximal runs of consecutive code points drawn with the same font. */
    List<TextRun> runs(String text) throws FontException {
        List<TextRun> runs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        PDType0Font currentFont = null;
        for (int codePoint : text.codePoints().toArray()) {
            PDType0Font font = fontFor(codePoint);
            if (font != currentFont && currentFont != null) {
                runs.add(new TextRun(currentFont, current.toString()));
                current.setLength(0);
            }
            currentFont = font;
            current.appendCodePoint(codePoint);
        }
        if (currentFont != null) {
            runs.add(new TextRun(currentFont, current.toString()));
        }
        return runs;
    }

    private PDType0Font fontFor(int codePoint) throws FontException {
        PDType0Font cached = fontByCodePoint.get(codePoint);
        if (cached != null) {
            return cached;
        }
        for (int index = 0; index < RESOURCES.size(); index++) {
            PDType0Font font = font(index);
            if (hasGlyph(font, codePoint)) {
                fontByCodePoint.put(codePoint, font);
                return font;
            }
        }
        throw new FontException(String.format(
                "OCR text contains U+%04X, which no bundled font supports",
                codePoint));
    }

    private static boolean hasGlyph(PDType0Font font, int codePoint) {
        try {
            font.encode(Character.toString(codePoint));
            return true;
        } catch (IOException | IllegalArgumentException error) {
            return false;
        }
    }

    private PDType0Font font(int index) throws FontException {
        if (loaded[index] == null) {
            loaded[index] = loadResource(document, RESOURCES.get(index));
        }
        return loaded[index];
    }

    private static PDType0Font loadResource(PDDocument document, Resource resource)
            throws FontException {
        try (InputStream raw = OcrFonts.class.getResourceAsStream(resource.path())) {
            if (raw == null) {
                throw new FontException("bundled OCR font resource is missing: " + resource.path());
            }
            try (InputStream input = resource.gzipped() ? new GZIPInputStream(raw) : raw) {
                return PDType0Font.load(document, input, true);
            }
        } catch (IOException | RuntimeException error) {
            throw new FontException("bundled OCR font could not be loaded: " + resource.path(), error);
        }
    }

    record TextRun(PDType0Font font, String text) {}

    private record Resource(String path, boolean gzipped) {}
}
