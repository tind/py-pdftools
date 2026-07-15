package dev.pypdftools.inspection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

final class PdfTestDocuments {
    private PdfTestDocuments() {}

    static byte[] onePage(PDRectangle mediaBox, int rotation, PDRectangle cropBox)
            throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(mediaBox);
            page.setRotation(rotation);
            if (cropBox != null) {
                page.setCropBox(cropBox);
            }
            document.addPage(page);
            return save(document);
        }
    }

    static byte[] multipage() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(new PDRectangle(612.0f, 792.0f)));
            PDPage second = new PDPage(new PDRectangle(400.0f, 300.0f));
            second.setRotation(90);
            document.addPage(second);
            return save(document);
        }
    }

    static byte[] encrypted(String userPassword) throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.LETTER));
            AccessPermission permissions = new AccessPermission();
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("owner-password", userPassword, permissions);
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            return save(document);
        }
    }

    private static byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.save(output);
        return output.toByteArray();
    }
}
