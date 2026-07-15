package dev.pypdftools.inspection;

import java.nio.charset.StandardCharsets;

/** Encodes PDF inspection results using private inspection schema version 1. */
public final class PdfInfoJsonEncoder {
    public static final int SCHEMA_VERSION = 1;

    private PdfInfoJsonEncoder() {}

    public static byte[] encode(PdfInfo info) {
        return encodeToString(info).getBytes(StandardCharsets.UTF_8);
    }

    static String encodeToString(PdfInfo info) {
        StringBuilder json = new StringBuilder(128 + info.pages().size() * 256);
        json.append("{\"schemaVersion\":").append(SCHEMA_VERSION);
        json.append(",\"pageCount\":").append(info.pageCount());
        json.append(",\"encrypted\":").append(info.encrypted());
        json.append(",\"pages\":[");
        for (int index = 0; index < info.pages().size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            appendPage(json, info.pages().get(index));
        }
        return json.append("]}").toString();
    }

    private static void appendPage(StringBuilder json, PdfPageInfo page) {
        json.append("{\"pageIndex\":").append(page.pageIndex());
        json.append(",\"widthPoints\":").append(page.widthPoints());
        json.append(",\"heightPoints\":").append(page.heightPoints());
        json.append(",\"rotation\":").append(page.rotation());
        json.append(",\"cropBox\":");
        appendBox(json, page.cropBox());
        json.append(",\"mediaBox\":");
        appendBox(json, page.mediaBox());
        json.append('}');
    }

    private static void appendBox(StringBuilder json, PdfBoxInfo box) {
        json.append("{\"lowerLeftX\":").append(box.lowerLeftX());
        json.append(",\"lowerLeftY\":").append(box.lowerLeftY());
        json.append(",\"upperRightX\":").append(box.upperRightX());
        json.append(",\"upperRightY\":").append(box.upperRightY());
        json.append('}');
    }
}
