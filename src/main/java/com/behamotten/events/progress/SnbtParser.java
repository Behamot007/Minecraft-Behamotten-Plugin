package com.behamotten.events.progress;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal SNBT (stringified NBT) parser that converts the textual representation used by
 * FTB Quests into regular Java collections. The supported feature set intentionally focuses on
 * the constructs that commonly appear inside quest definitions (compounds, lists, primitive
 * values and typed arrays). The parser is independent of the Minecraft server implementation
 * which allows us to process quest files during unit tests without depending on NMS classes.
 */
final class SnbtParser {

    private final String input;
    private int index;

    private SnbtParser(final String input) {
        this.input = input != null ? input : "";
    }

    static Object parse(final String input) throws SnbtParseException {
        final SnbtParser parser = new SnbtParser(input);
        final Object value = parser.parseValue();
        parser.skipWhitespaceAndComments();
        if (!parser.isEnd()) {
            throw parser.error("Unerwarteter Inhalt am Ende des SNBT-Dokuments");
        }
        return value;
    }

    private Object parseValue() throws SnbtParseException {
        skipWhitespaceAndComments();
        if (isEnd()) {
            throw error("Unerwartetes Ende des SNBT-Dokuments");
        }
        final char ch = peek();
        switch (ch) {
            case '{':
                return parseCompound();
            case '[':
                return parseList();
            case '"':
            case '\'':
                return parseQuotedString();
            default:
                return parseBareToken();
        }
    }

    private Map<String, Object> parseCompound() throws SnbtParseException {
        expect('{');
        skipWhitespaceAndComments();
        final Map<String, Object> result = new LinkedHashMap<>();
        if (consume('}')) {
            return result;
        }
        while (true) {
            skipWhitespaceAndComments();
            final String key = parseKey();
            skipWhitespaceAndComments();
            expect(':');
            final Object value = parseValue();
            result.put(key, value);
            skipWhitespaceAndComments();
            if (consume('}')) {
                break;
            }
            expect(',');
        }
        return result;
    }

    private String parseKey() throws SnbtParseException {
        skipWhitespaceAndComments();
        if (isEnd()) {
            throw error("Unerwartetes Ende während der Schlüsselauswertung");
        }
        final char ch = peek();
        if (ch == '"' || ch == '\'') {
            return parseQuotedString();
        }
        final int start = index;
        while (!isEnd()) {
            final char current = peek();
            if (Character.isWhitespace(current) || current == ':' || current == ',' || current == '}'
                    || current == ']') {
                break;
            }
            index++;
        }
        if (index <= start) {
            throw error("Leerer Schlüssel ist nicht erlaubt");
        }
        return input.substring(start, index);
    }

    private List<Object> parseList() throws SnbtParseException {
        expect('[');
        skipWhitespaceAndComments();
        String typeToken = null;
        final int typeStart = index;
        if (!isEnd()) {
            final char ch = peek();
            if (Character.isLetter(ch)) {
                final String candidate = readBareWord();
                skipWhitespaceAndComments();
                if (consume(';')) {
                    typeToken = candidate;
                } else {
                    index = typeStart;
                }
            }
        }
        skipWhitespaceAndComments();
        final List<Object> result = new ArrayList<>();
        if (consume(']')) {
            return result;
        }
        while (true) {
            final Object value = parseValue();
            result.add(value);
            skipWhitespaceAndComments();
            if (consume(']')) {
                break;
            }
            expect(',');
        }
        if (typeToken != null && !typeToken.isBlank()) {
            result.add(0, typeToken.trim());
        }
        return result;
    }

    private String parseQuotedString() throws SnbtParseException {
        skipWhitespaceAndComments();
        if (isEnd()) {
            throw error("Unerwartetes Ende beim Lesen einer Zeichenkette");
        }
        final char quote = peek();
        if (quote != '"' && quote != '\'') {
            throw error("Erwartete Anführungszeichen, gefunden: " + quote);
        }
        index++;
        final StringBuilder builder = new StringBuilder();
        while (!isEnd()) {
            final char ch = next();
            if (ch == quote) {
                return builder.toString();
            }
            if (ch == '\\') {
                if (isEnd()) {
                    throw error("Unvollständige Escape-Sequenz in Zeichenkette");
                }
                final char escaped = next();
                switch (escaped) {
                    case '"':
                    case '\'':
                    case '\\':
                        builder.append(escaped);
                        break;
                    case 'b':
                        builder.append('\b');
                        break;
                    case 'f':
                        builder.append('\f');
                        break;
                    case 'n':
                        builder.append('\n');
                        break;
                    case 'r':
                        builder.append('\r');
                        break;
                    case 't':
                        builder.append('\t');
                        break;
                    case 'u':
                        builder.append(parseUnicodeEscape());
                        break;
                    default:
                        builder.append(escaped);
                        break;
                }
            } else {
                builder.append(ch);
            }
        }
        throw error("Zeichenkette wurde nicht korrekt abgeschlossen");
    }

    private char parseUnicodeEscape() throws SnbtParseException {
        if (index + 4 > input.length()) {
            throw error("Unvollständige Unicode-Escape-Sequenz");
        }
        final String hex = input.substring(index, index + 4);
        index += 4;
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (final NumberFormatException exception) {
            throw error("Ungültiger Unicode-Wert: " + hex, exception);
        }
    }

    private Object parseBareToken() throws SnbtParseException {
        final String token = readBareWord();
        if (token.isEmpty()) {
            throw error("Leerer Wert ist nicht erlaubt");
        }
        final String lower = token.toLowerCase();
        if ("true".equals(lower)) {
            return Boolean.TRUE;
        }
        if ("false".equals(lower)) {
            return Boolean.FALSE;
        }
        if ("null".equals(lower)) {
            return null;
        }
        final char suffix = token.charAt(token.length() - 1);
        final String numberToken;
        if (isNumberSuffix(suffix)) {
            numberToken = token.substring(0, token.length() - 1);
        } else {
            numberToken = token;
        }
        try {
            if (suffix == 'b' || suffix == 'B') {
                return Byte.parseByte(numberToken);
            }
            if (suffix == 's' || suffix == 'S') {
                return Short.parseShort(numberToken);
            }
            if (suffix == 'l' || suffix == 'L') {
                return Long.parseLong(numberToken);
            }
            if (suffix == 'f' || suffix == 'F') {
                return Float.parseFloat(numberToken);
            }
            if (suffix == 'd' || suffix == 'D') {
                return Double.parseDouble(numberToken);
            }
            if (numberToken.contains(".") || numberToken.contains("e") || numberToken.contains("E")) {
                return Double.parseDouble(numberToken);
            }
            return Long.parseLong(numberToken);
        } catch (final NumberFormatException exception) {
            return token;
        }
    }

    private boolean isNumberSuffix(final char ch) {
        return ch == 'b' || ch == 'B' || ch == 's' || ch == 'S' || ch == 'l' || ch == 'L' || ch == 'f'
                || ch == 'F' || ch == 'd' || ch == 'D';
    }

    private String readBareWord() {
        skipWhitespaceAndComments();
        final int start = index;
        while (!isEnd()) {
            final char ch = peek();
            if (Character.isWhitespace(ch) || ch == ',' || ch == ':' || ch == ']' || ch == '}' || ch == ';') {
                break;
            }
            index++;
        }
        return input.substring(start, index);
    }

    private void expect(final char expected) throws SnbtParseException {
        skipWhitespaceAndComments();
        if (isEnd() || input.charAt(index) != expected) {
            throw error("Erwartetes Zeichen '" + expected + "' wurde nicht gefunden");
        }
        index++;
    }

    private boolean consume(final char expected) {
        if (!isEnd() && input.charAt(index) == expected) {
            index++;
            return true;
        }
        return false;
    }

    private void skipWhitespaceAndComments() {
        while (!isEnd()) {
            final char ch = peek();
            if (Character.isWhitespace(ch)) {
                index++;
                continue;
            }
            if (ch == '#') {
                skipUntilLineEnd();
                continue;
            }
            if (ch == '/' && index + 1 < input.length()) {
                final char next = input.charAt(index + 1);
                if (next == '/') {
                    index += 2;
                    skipUntilLineEnd();
                    continue;
                }
                if (next == '*') {
                    index += 2;
                    skipBlockComment();
                    continue;
                }
            }
            break;
        }
    }

    private void skipUntilLineEnd() {
        while (!isEnd()) {
            final char ch = peek();
            index++;
            if (ch == '\n' || ch == '\r') {
                break;
            }
        }
    }

    private void skipBlockComment() {
        while (!isEnd()) {
            final char ch = next();
            if (ch == '*' && !isEnd() && peek() == '/') {
                index++;
                break;
            }
        }
    }

    private char peek() {
        return input.charAt(index);
    }

    private char next() {
        return input.charAt(index++);
    }

    private boolean isEnd() {
        return index >= input.length();
    }

    private SnbtParseException error(final String message) {
        return new SnbtParseException(message + " (Index " + index + ")");
    }

    private SnbtParseException error(final String message, final Throwable cause) {
        return new SnbtParseException(message + " (Index " + index + ")", cause);
    }

    static final class SnbtParseException extends Exception {
        private static final long serialVersionUID = 1L;

        SnbtParseException(final String message) {
            super(message);
        }

        SnbtParseException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
