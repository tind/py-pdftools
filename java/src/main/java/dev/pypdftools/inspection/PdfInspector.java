package dev.pypdftools.inspection;

import dev.pypdftools.error.InvalidPdfException;
import dev.pypdftools.error.PdfPasswordRequiredException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

/** Opens a PDF with PDFBox and extracts the inspection model used by Python. */
public final class PdfInspector {
    private PdfInspector() {}

    public static PdfInfo inspect(byte[] pdfData)
            throws InvalidPdfException, PdfPasswordRequiredException {
        if (pdfData == null) {
            throw new InvalidPdfException("PDF data must not be null");
        }
        if (pdfData.length == 0) {
            throw new InvalidPdfException("PDF data must not be empty");
        }

        try (PDDocument document = Loader.loadPDF(pdfData)) {
            List<PdfPageInfo> pages = new ArrayList<>(document.getNumberOfPages());
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                pages.add(inspectPage(document.getPage(pageIndex), pageIndex));
            }
            return new PdfInfo(document.getNumberOfPages(), pages, document.isEncrypted());
        } catch (InvalidPasswordException error) {
            throw new PdfPasswordRequiredException("PDF requires a password", error);
        } catch (IOException | IllegalArgumentException error) {
            throw new InvalidPdfException("PDF could not be read: " + error.getMessage(), error);
        }
    }

    private static PdfPageInfo inspectPage(PDPage page, int pageIndex)
            throws InvalidPdfException {
        int rotation = normalizeRotation(page.getRotation(), pageIndex);
        PDRectangle cropBox = page.getCropBox();
        PDRectangle mediaBox = page.getMediaBox();
        if (cropBox == null || mediaBox == null) {
            throw new InvalidPdfException("PDF page " + pageIndex + " has no page box");
        }

        double width = cropBox.getWidth();
        double height = cropBox.getHeight();
        if (rotation == 90 || rotation == 270) {
            double originalWidth = width;
            width = height;
            height = originalWidth;
        }

        try {
            return new PdfPageInfo(
                    pageIndex,
                    width,
                    height,
                    rotation,
                    boxInfo(cropBox),
                    boxInfo(mediaBox));
        } catch (IllegalArgumentException error) {
            throw new InvalidPdfException(
                    "PDF page " + pageIndex + " has invalid geometry: " + error.getMessage(),
                    error);
        }
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

    private static PdfBoxInfo boxInfo(PDRectangle box) {
        return new PdfBoxInfo(
                box.getLowerLeftX(),
                box.getLowerLeftY(),
                box.getUpperRightX(),
                box.getUpperRightY());
    }
}
