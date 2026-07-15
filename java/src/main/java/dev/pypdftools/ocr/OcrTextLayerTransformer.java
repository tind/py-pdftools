package dev.pypdftools.ocr;

import dev.pypdftools.error.FontException;
import dev.pypdftools.error.InvalidPdfException;
import dev.pypdftools.error.PageMismatchException;
import dev.pypdftools.error.PdfPasswordRequiredException;
import dev.pypdftools.error.PdfPermissionException;
import dev.pypdftools.error.PdfProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.util.Matrix;

/** Adds fitted, extractable OCR text while preserving the existing page streams. */
public final class OcrTextLayerTransformer {
    private static final double FONT_UNITS = 1000.0;

    private OcrTextLayerTransformer() {}

    public static byte[] transform(byte[] pdfData, OcrRequest request)
            throws InvalidPdfException,
                    PdfPasswordRequiredException,
                    PageMismatchException,
                    PdfPermissionException,
                    FontException,
                    PdfProcessingException {
        if (pdfData == null) {
            throw new InvalidPdfException("PDF data must not be null");
        }
        if (pdfData.length == 0) {
            throw new InvalidPdfException("PDF data must not be empty");
        }
        if (request == null) {
            throw new NullPointerException("request");
        }

        try (PDDocument document = loadDocument(pdfData)) {
            request.validatePageMatch(document.getNumberOfPages());
            requireModificationPermission(document);
            addText(document, request);

            ByteArrayOutputStream output = new ByteArrayOutputStream(pdfData.length);
            document.save(output);
            return output.toByteArray();
        } catch (InvalidPdfException
                | PageMismatchException
                | PdfPermissionException
                | FontException
                | PdfProcessingException error) {
            throw error;
        } catch (IOException | IllegalArgumentException error) {
            throw new PdfProcessingException(
                    "PDF could not be transformed: " + error.getMessage(),
                    error);
        }
    }

    private static PDDocument loadDocument(byte[] pdfData)
            throws InvalidPdfException, PdfPasswordRequiredException {
        try {
            return Loader.loadPDF(pdfData);
        } catch (InvalidPasswordException error) {
            throw new PdfPasswordRequiredException("PDF requires a password", error);
        } catch (IOException | IllegalArgumentException error) {
            throw new InvalidPdfException("PDF could not be read: " + error.getMessage(), error);
        }
    }

    private static void requireModificationPermission(PDDocument document)
            throws PdfPermissionException {
        if (!document.getCurrentAccessPermission().canModify()) {
            throw new PdfPermissionException("PDF security settings prohibit modification");
        }
    }

    private static void addText(PDDocument document, OcrRequest request)
            throws InvalidPdfException, FontException, PdfProcessingException {
        if (!hasEligibleLines(request)) {
            return;
        }

        PDType0Font font = DefaultOcrFont.load(document);
        FontMetrics metrics = fontMetrics(font);
        for (OcrPageData pageData : request.pages()) {
            List<OcrLineData> lines = eligibleLines(pageData.lines(), request.options());
            if (lines.isEmpty()) {
                continue;
            }
            PDPage page = document.getPage(pageData.pageIndex());
            writePage(document, page, pageData, lines, request.options(), font, metrics);
        }
    }

    private static boolean hasEligibleLines(OcrRequest request) {
        for (OcrPageData page : request.pages()) {
            if (!eligibleLines(page.lines(), request.options()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static List<OcrLineData> eligibleLines(
            List<OcrLineData> lines,
            OcrOptions options) {
        return lines.stream()
                .filter(line -> !line.text().isBlank())
                .filter(line -> isConfidentEnough(line, options.minimumConfidence()))
                .toList();
    }

    private static boolean isConfidentEnough(OcrLineData line, Double minimumConfidence) {
        return minimumConfidence == null
                || line.confidence() == null
                || line.confidence() >= minimumConfidence;
    }

    private static void writePage(
            PDDocument document,
            PDPage page,
            OcrPageData pageData,
            List<OcrLineData> lines,
            OcrOptions options,
            PDType0Font font,
            FontMetrics metrics)
            throws InvalidPdfException, FontException, PdfProcessingException {
        PDRectangle cropBox = page.getCropBox();
        if (cropBox == null) {
            throw new InvalidPdfException("PDF page " + pageData.pageIndex() + " has no crop box");
        }
        validateCropBox(cropBox, pageData.pageIndex());
        int rotation = normalizeRotation(page.getRotation(), pageData.pageIndex());
        List<PreparedLine> preparedLines = new ArrayList<>(lines.size());
        for (OcrLineData line : lines) {
            preparedLines.add(prepareLine(
                    font,
                    metrics,
                    cropBox,
                    rotation,
                    pageData,
                    line));
        }

        try (PDPageContentStream stream = new PDPageContentStream(
                document,
                page,
                AppendMode.APPEND,
                true,
                true)) {
            stream.beginText();
            stream.setRenderingMode(options.debugVisibleText()
                    ? RenderingMode.FILL
                    : RenderingMode.NEITHER);
            if (options.debugVisibleText()) {
                stream.setNonStrokingColor(0.0f);
            }
            for (PreparedLine line : preparedLines) {
                writeLine(stream, font, line);
            }
            stream.endText();
        } catch (IOException | IllegalArgumentException error) {
            throw new PdfProcessingException(
                    "OCR text could not be written to PDF page " + pageData.pageIndex(),
                    error);
        }
    }

    private static PreparedLine prepareLine(
            PDType0Font font,
            FontMetrics metrics,
            PDRectangle cropBox,
            int rotation,
            OcrPageData pageData,
            OcrLineData line) throws FontException {
        TextRegion region = PageCoordinateMapper.map(
                cropBox,
                rotation,
                line.bounds(),
                pageData.orientation());
        TextPlacement placement = fitText(font, metrics, line.text(), region);
        return new PreparedLine(line.text(), placement);
    }

    private static void writeLine(
            PDPageContentStream stream,
            PDType0Font font,
            PreparedLine line)
            throws IOException {
        stream.setFont(font, line.placement().fontSize());
        stream.setHorizontalScaling(line.placement().horizontalScaling());
        stream.setTextMatrix(line.placement().matrix());
        stream.showText(line.text());
    }

    private static TextPlacement fitText(
            PDType0Font font,
            FontMetrics metrics,
            String text,
            TextRegion region)
            throws FontException {
        double widthUnits;
        try {
            font.encode(text);
            widthUnits = font.getStringWidth(text);
        } catch (IOException | IllegalArgumentException error) {
            throw new FontException("OCR text contains a glyph unsupported by the bundled font", error);
        }
        if (!(widthUnits > 0.0) || !Double.isFinite(widthUnits)) {
            throw new FontException("OCR text has no measurable width in the bundled font");
        }

        double fontSize = region.lineThickness() * FONT_UNITS / metrics.heightUnits();
        double naturalWidth = widthUnits * fontSize / FONT_UNITS;
        double horizontalScaling = region.readingLength() / naturalWidth * 100.0;
        double verticalCenter = (metrics.ascentUnits() + metrics.descentUnits())
                * fontSize / (2.0 * FONT_UNITS);
        double originX = region.centerX()
                - region.baselineX() * region.readingLength() / 2.0
                - region.glyphUpX() * verticalCenter;
        double originY = region.centerY()
                - region.baselineY() * region.readingLength() / 2.0
                - region.glyphUpY() * verticalCenter;

        return new TextPlacement(
                positiveFloat(fontSize, "font size"),
                positiveFloat(horizontalScaling, "horizontal text scaling"),
                new Matrix(
                        finiteFloat(region.baselineX(), "baseline x"),
                        finiteFloat(region.baselineY(), "baseline y"),
                        finiteFloat(region.glyphUpX(), "glyph-up x"),
                        finiteFloat(region.glyphUpY(), "glyph-up y"),
                        finiteFloat(originX, "text origin x"),
                        finiteFloat(originY, "text origin y")));
    }

    private static FontMetrics fontMetrics(PDType0Font font) throws FontException {
        PDFontDescriptor descriptor = font.getFontDescriptor();
        if (descriptor == null) {
            throw new FontException("bundled OCR font has no descriptor");
        }
        double ascent = descriptor.getAscent();
        double descent = descriptor.getDescent();
        double height = ascent - descent;
        if (!Double.isFinite(ascent)
                || !Double.isFinite(descent)
                || !(height > 0.0)) {
            throw new FontException("bundled OCR font has invalid vertical metrics");
        }
        return new FontMetrics(ascent, descent, height);
    }

    private static int normalizeRotation(int rawRotation, int pageIndex)
            throws InvalidPdfException {
        int rotation = Math.floorMod(rawRotation, 360);
        if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) {
            throw new InvalidPdfException(
                    "PDF page " + pageIndex + " has unsupported rotation " + rawRotation);
        }
        return rotation;
    }

    private static void validateCropBox(PDRectangle cropBox, int pageIndex)
            throws InvalidPdfException {
        double width = cropBox.getWidth();
        double height = cropBox.getHeight();
        if (!Double.isFinite(width)
                || !Double.isFinite(height)
                || !(width > 0.0)
                || !(height > 0.0)) {
            throw new InvalidPdfException(
                    "PDF page " + pageIndex + " has invalid crop-box geometry");
        }
    }

    private static float positiveFloat(double value, String name) throws FontException {
        float result = finiteFloat(value, name);
        if (!(result > 0.0f)) {
            throw new FontException(name + " must be positive");
        }
        return result;
    }

    private static float finiteFloat(double value, String name) throws FontException {
        float result = (float) value;
        if (!Float.isFinite(result)) {
            throw new FontException(name + " exceeds the supported numeric range");
        }
        return result;
    }

    private record FontMetrics(
            double ascentUnits,
            double descentUnits,
            double heightUnits) {}

    private record TextPlacement(
            float fontSize,
            float horizontalScaling,
            Matrix matrix) {}

    private record PreparedLine(String text, TextPlacement placement) {}
}
