package dev.pypdftools.ocr;

import dev.pypdftools.error.InvalidOcrDataException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Decodes and independently validates native OCR request schema version 1. */
public final class OcrRequestDecoder {
    public static final int SCHEMA_VERSION = 1;

    private static final int MAX_REQUEST_BYTES = 64 * 1024 * 1024;
    private static final int MAX_PAGES = 100_000;
    private static final int MAX_LINES_PER_PAGE = 100_000;
    private static final int MAX_TOTAL_LINES = 1_000_000;
    private static final int MAX_TEXT_CHARACTERS_PER_LINE = 1_000_000;
    private static final long MAX_AGGREGATE_TEXT_CHARACTERS = 64L * 1024L * 1024L;

    private OcrRequestDecoder() {}

    public static OcrRequest decode(byte[] requestData) throws InvalidOcrDataException {
        if (requestData == null) {
            throw new InvalidOcrDataException("OCR request must not be null");
        }
        if (requestData.length == 0) {
            throw new InvalidOcrDataException("OCR request must not be empty");
        }
        if (requestData.length > MAX_REQUEST_BYTES) {
            throw new InvalidOcrDataException(
                    "OCR request exceeds the " + MAX_REQUEST_BYTES + "-byte limit");
        }

        try {
            Object value = JsonParser.parse(decodeUtf8(requestData));
            return decodeRoot(requireObject(value, "OCR request"));
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new InvalidOcrDataException(
                    "invalid OCR request: " + error.getMessage(),
                    error);
        }
    }

    private static OcrRequest decodeRoot(Map<String, Object> root) {
        requireExactKeys(root, Set.of("schemaVersion", "options", "pages"), "OCR request");
        int schemaVersion = requireInt(root.get("schemaVersion"), "schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported request schema version " + schemaVersion
                            + "; expected " + SCHEMA_VERSION);
        }

        OcrOptions options = decodeOptions(requireObject(root.get("options"), "options"));
        List<?> rawPages = requireList(root.get("pages"), "pages");
        if (rawPages.size() > MAX_PAGES) {
            throw new IllegalArgumentException(
                    "pages exceeds the " + MAX_PAGES + "-page limit");
        }

        List<OcrPageData> pages = new ArrayList<>(rawPages.size());
        Set<Integer> pageIndexes = new HashSet<>();
        int totalLines = 0;
        long totalTextCharacters = 0;
        for (int pagePosition = 0; pagePosition < rawPages.size(); pagePosition++) {
            DecodedPage page = decodePage(rawPages.get(pagePosition), pagePosition);
            if (!pageIndexes.add(page.value().pageIndex())) {
                throw new IllegalArgumentException(
                        "duplicate OCR page index " + page.value().pageIndex());
            }
            totalLines = Math.addExact(totalLines, page.value().lines().size());
            if (totalLines > MAX_TOTAL_LINES) {
                throw new IllegalArgumentException(
                        "OCR request exceeds the " + MAX_TOTAL_LINES + "-line limit");
            }
            totalTextCharacters = Math.addExact(
                    totalTextCharacters,
                    page.textCharacters());
            if (totalTextCharacters > MAX_AGGREGATE_TEXT_CHARACTERS) {
                throw new IllegalArgumentException(
                        "OCR request exceeds the aggregate text-size limit");
            }
            pages.add(page.value());
        }
        return new OcrRequest(options, pages);
    }

    private static OcrOptions decodeOptions(Map<String, Object> options) {
        requireExactKeys(
                options,
                Set.of("allowPartialDocument", "minimumConfidence", "debugVisibleText"),
                "options");
        return new OcrOptions(
                requireBoolean(options.get("allowPartialDocument"), "allowPartialDocument"),
                requireNullableNumber(
                        options.get("minimumConfidence"),
                        "minimumConfidence"),
                requireBoolean(options.get("debugVisibleText"), "debugVisibleText"));
    }

    private static DecodedPage decodePage(Object value, int pagePosition) {
        String context = "pages[" + pagePosition + "]";
        Map<String, Object> page = requireObject(value, context);
        requireExactKeys(page, Set.of("pageIndex", "orientation", "lines"), context);

        List<?> rawLines = requireList(page.get("lines"), context + ".lines");
        if (rawLines.size() > MAX_LINES_PER_PAGE) {
            throw new IllegalArgumentException(
                    context + ".lines exceeds the " + MAX_LINES_PER_PAGE + "-line limit");
        }
        List<OcrLineData> lines = new ArrayList<>(rawLines.size());
        long textCharacters = 0;
        for (int linePosition = 0; linePosition < rawLines.size(); linePosition++) {
            OcrLineData line = decodeLine(
                    rawLines.get(linePosition),
                    context + ".lines[" + linePosition + "]");
            textCharacters = Math.addExact(textCharacters, line.text().length());
            lines.add(line);
        }

        OcrPageData result = new OcrPageData(
                requireInt(page.get("pageIndex"), context + ".pageIndex"),
                requireInt(page.get("orientation"), context + ".orientation"),
                lines);
        return new DecodedPage(result, textCharacters);
    }

    private static OcrLineData decodeLine(Object value, String context) {
        Map<String, Object> line = requireObject(value, context);
        requireExactKeys(line, Set.of("text", "confidence", "bounds"), context);
        String text = requireString(line.get("text"), context + ".text");
        if (text.length() > MAX_TEXT_CHARACTERS_PER_LINE) {
            throw new IllegalArgumentException(
                    context + ".text exceeds the per-line text-size limit");
        }
        return new OcrLineData(
                text,
                decodeBounds(requireObject(line.get("bounds"), context + ".bounds"), context),
                requireNullableNumber(line.get("confidence"), context + ".confidence"));
    }

    private static OcrBounds decodeBounds(Map<String, Object> bounds, String lineContext) {
        String context = lineContext + ".bounds";
        requireExactKeys(bounds, Set.of("left", "top", "width", "height"), context);
        return new OcrBounds(
                requireNumber(bounds.get("left"), context + ".left"),
                requireNumber(bounds.get("top"), context + ".top"),
                requireNumber(bounds.get("width"), context + ".width"),
                requireNumber(bounds.get("height"), context + ".height"));
    }

    private static String decodeUtf8(byte[] data) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data))
                    .toString();
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException("OCR request must be valid UTF-8", error);
        }
    }

    private static Map<String, Object> requireObject(Object value, String context) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException(context + " must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException(context + " keys must be strings");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static List<?> requireList(Object value, String context) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(context + " must be an array");
        }
        return list;
    }

    private static void requireExactKeys(
            Map<String, Object> value,
            Set<String> expected,
            String context) {
        if (!value.keySet().equals(expected)) {
            Set<String> missing = new HashSet<>(expected);
            missing.removeAll(value.keySet());
            Set<String> unexpected = new HashSet<>(value.keySet());
            unexpected.removeAll(expected);
            throw new IllegalArgumentException(
                    context + " has invalid fields; missing=" + missing
                            + ", unexpected=" + unexpected);
        }
    }

    private static int requireInt(Object value, String context) {
        if (!(value instanceof Long number)
                || number < Integer.MIN_VALUE
                || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(context + " must be an int");
        }
        return number.intValue();
    }

    private static double requireNumber(Object value, String context) {
        if (value instanceof Long integer) {
            return integer.doubleValue();
        }
        if (value instanceof Double number && Double.isFinite(number)) {
            return number;
        }
        throw new IllegalArgumentException(context + " must be a finite number");
    }

    private static Double requireNullableNumber(Object value, String context) {
        return value == null ? null : requireNumber(value, context);
    }

    private static boolean requireBoolean(Object value, String context) {
        if (!(value instanceof Boolean result)) {
            throw new IllegalArgumentException(context + " must be a bool");
        }
        return result;
    }

    private static String requireString(Object value, String context) {
        if (!(value instanceof String result)) {
            throw new IllegalArgumentException(context + " must be a string");
        }
        return result;
    }

    private record DecodedPage(OcrPageData value, long textCharacters) {}
}
