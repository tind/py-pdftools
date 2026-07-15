package dev.pypdftools.ocr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small strict JSON parser used for the private native request schema. */
final class JsonParser {
    private static final int MAX_DEPTH = 64;

    private final String input;
    private int index;

    private JsonParser(String input) {
        this.input = input;
    }

    static Object parse(String input) {
        JsonParser parser = new JsonParser(input);
        Object value = parser.parseValue(0);
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("unexpected trailing content");
        }
        return value;
    }

    private Object parseValue(int depth) {
        skipWhitespace();
        if (atEnd()) {
            throw error("expected a JSON value");
        }
        return switch (current()) {
            case '{' -> parseObject(depth + 1);
            case '[' -> parseArray(depth + 1);
            case '"' -> parseString();
            case 't' -> parseLiteral("true", Boolean.TRUE);
            case 'f' -> parseLiteral("false", Boolean.FALSE);
            case 'n' -> parseLiteral("null", null);
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObject(int depth) {
        requireDepth(depth);
        advance();
        skipWhitespace();
        Map<String, Object> result = new LinkedHashMap<>();
        if (consume('}')) {
            return result;
        }

        while (true) {
            skipWhitespace();
            if (atEnd() || current() != '"') {
                throw error("object keys must be strings");
            }
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue(depth);
            if (result.containsKey(key)) {
                throw error("duplicate object key " + key);
            }
            result.put(key, value);
            skipWhitespace();
            if (consume('}')) {
                return result;
            }
            expect(',');
        }
    }

    private List<Object> parseArray(int depth) {
        requireDepth(depth);
        advance();
        skipWhitespace();
        List<Object> result = new ArrayList<>();
        if (consume(']')) {
            return result;
        }

        while (true) {
            result.add(parseValue(depth));
            skipWhitespace();
            if (consume(']')) {
                return result;
            }
            expect(',');
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (!atEnd()) {
            char character = current();
            advance();
            if (character == '"') {
                return result.toString();
            }
            if (character == '\\') {
                appendEscape(result);
            } else if (character < 0x20) {
                throw error("unescaped control character in string");
            } else if (Character.isHighSurrogate(character)) {
                if (atEnd() || !Character.isLowSurrogate(current())) {
                    throw error("unpaired surrogate in string");
                }
                result.append(character).append(current());
                advance();
            } else if (Character.isLowSurrogate(character)) {
                throw error("unpaired surrogate in string");
            } else {
                result.append(character);
            }
        }
        throw error("unterminated string");
    }

    private void appendEscape(StringBuilder result) {
        if (atEnd()) {
            throw error("unterminated string escape");
        }
        char escape = current();
        advance();
        switch (escape) {
            case '"', '\\', '/' -> result.append(escape);
            case 'b' -> result.append('\b');
            case 'f' -> result.append('\f');
            case 'n' -> result.append('\n');
            case 'r' -> result.append('\r');
            case 't' -> result.append('\t');
            case 'u' -> appendUnicodeEscape(result);
            default -> throw error("unknown string escape " + escape);
        }
    }

    private void appendUnicodeEscape(StringBuilder result) {
        char first = parseHexCharacter();
        if (Character.isHighSurrogate(first)) {
            if (index + 2 > input.length()
                    || input.charAt(index) != '\\'
                    || input.charAt(index + 1) != 'u') {
                throw error("high surrogate must be followed by a low surrogate");
            }
            index += 2;
            char second = parseHexCharacter();
            if (!Character.isLowSurrogate(second)) {
                throw error("high surrogate must be followed by a low surrogate");
            }
            result.append(first).append(second);
        } else if (Character.isLowSurrogate(first)) {
            throw error("low surrogate must follow a high surrogate");
        } else {
            result.append(first);
        }
    }

    private char parseHexCharacter() {
        if (index + 4 > input.length()) {
            throw error("incomplete Unicode escape");
        }
        int value = 0;
        for (int offset = 0; offset < 4; offset++) {
            int digit = Character.digit(input.charAt(index + offset), 16);
            if (digit < 0) {
                throw error("invalid Unicode escape");
            }
            value = value * 16 + digit;
        }
        index += 4;
        return (char) value;
    }

    private Object parseNumber() {
        int start = index;
        consume('-');
        if (atEnd()) {
            throw error("incomplete number");
        }
        if (consume('0')) {
            if (!atEnd() && isDigit(current())) {
                throw error("leading zero in number");
            }
        } else {
            requireDigitOneToNine();
            consumeDigits();
        }

        boolean floatingPoint = false;
        if (consume('.')) {
            floatingPoint = true;
            requireDigit();
            consumeDigits();
        }
        if (consume('e') || consume('E')) {
            floatingPoint = true;
            if (!consume('+')) {
                consume('-');
            }
            requireDigit();
            consumeDigits();
        }

        String token = input.substring(start, index);
        try {
            if (!floatingPoint) {
                return Long.valueOf(token);
            }
            double value = Double.parseDouble(token);
            if (!Double.isFinite(value)) {
                throw error("number is outside the finite double range");
            }
            return value;
        } catch (NumberFormatException invalidNumber) {
            throw error("invalid or out-of-range number");
        }
    }

    private Object parseLiteral(String literal, Object value) {
        if (!input.startsWith(literal, index)) {
            throw error("invalid JSON value");
        }
        index += literal.length();
        return value;
    }

    private void requireDepth(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("JSON nesting exceeds " + MAX_DEPTH + " levels");
        }
    }

    private void requireDigitOneToNine() {
        if (atEnd() || current() < '1' || current() > '9') {
            throw error("expected a digit from 1 through 9");
        }
        advance();
    }

    private void requireDigit() {
        if (atEnd() || !isDigit(current())) {
            throw error("expected a digit");
        }
    }

    private void consumeDigits() {
        while (!atEnd() && isDigit(current())) {
            advance();
        }
    }

    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private void skipWhitespace() {
        while (!atEnd()) {
            char character = current();
            if (character != ' ' && character != '\t' && character != '\r' && character != '\n') {
                return;
            }
            advance();
        }
    }

    private void expect(char expected) {
        if (!consume(expected)) {
            throw error("expected '" + expected + "'");
        }
    }

    private boolean consume(char expected) {
        if (!atEnd() && current() == expected) {
            advance();
            return true;
        }
        return false;
    }

    private char current() {
        return input.charAt(index);
    }

    private void advance() {
        index++;
    }

    private boolean atEnd() {
        return index >= input.length();
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(
                "invalid JSON at character " + index + ": " + message);
    }
}
