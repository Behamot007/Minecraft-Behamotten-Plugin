package com.behamotten.events.progress;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser and writer tailored for plugin export data.
 */
public final class SimpleJson {
    private static final String INDENT = "  ";

    private SimpleJson() {
    }

    public static Object parse(final String json) throws JsonException {
        if (json == null) {
            return null;
        }
        final Parser parser = new Parser(json);
        return parser.parse();
    }

    public static String stringify(final Object value) {
        final StringBuilder builder = new StringBuilder();
        writeValue(value, builder, 0);
        return builder.toString();
    }

    private static void writeValue(final Object value, final StringBuilder builder, final int depth) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String) {
            builder.append('"').append(escape((String) value)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            builder.append(value.toString());
        } else if (value instanceof Map<?, ?>) {
            writeObject((Map<?, ?>) value, builder, depth);
        } else if (value instanceof List<?>) {
            writeArray((List<?>) value, builder, depth);
        } else {
            builder.append('"').append(escape(value.toString())).append('"');
        }
    }

    private static void writeObject(final Map<?, ?> map, final StringBuilder builder, final int depth) {
        builder.append('{');
        if (!map.isEmpty()) {
            builder.append('\n');
            boolean first = true;
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                if (!first) {
                    builder.append(',').append('\n');
                }
                indent(builder, depth + 1);
                builder.append('"').append(escape(entry.getKey().toString())).append('"').append(':').append(' ');
                writeValue(entry.getValue(), builder, depth + 1);
                first = false;
            }
            builder.append('\n');
            indent(builder, depth);
        }
        builder.append('}');
    }

    private static void writeArray(final List<?> list, final StringBuilder builder, final int depth) {
        builder.append('[');
        if (!list.isEmpty()) {
            builder.append('\n');
            boolean first = true;
            for (final Object element : list) {
                if (!first) {
                    builder.append(',').append('\n');
                }
                indent(builder, depth + 1);
                writeValue(element, builder, depth + 1);
                first = false;
            }
            builder.append('\n');
            indent(builder, depth);
        }
        builder.append(']');
    }

    private static void indent(final StringBuilder builder, final int depth) {
        for (int i = 0; i < depth; i++) {
            builder.append(INDENT);
        }
    }

    private static String escape(final String value) {
        final StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            switch (ch) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                    break;
            }
        }
        return escaped.toString();
    }

    private static final class Parser {
        private final String input;
        private int index;

        Parser(final String input) {
            this.input = input;
        }

        Object parse() throws JsonException {
            skipWhitespace();
            final Object value = parseValue();
            skipWhitespace();
            if (!isEnd()) {
                throw error("Unerwartete Zeichen am Ende des JSON-Inhalts");
            }
            return value;
        }

        private Object parseValue() throws JsonException {
            skipWhitespace();
            if (isEnd()) {
                throw error("Unerwartetes Ende des JSON-Inhalts");
            }
            final char ch = input.charAt(index);
            switch (ch) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                    return parseLiteral("true", Boolean.TRUE);
                case 'f':
                    return parseLiteral("false", Boolean.FALSE);
                case 'n':
                    return parseLiteral("null", null);
                default:
                    if (ch == '-' || isDigit(ch)) {
                        return parseNumber();
                    }
                    throw error("Unerwartetes Zeichen '" + ch + "'");
            }
        }

        private Map<String, Object> parseObject() throws JsonException {
            expect('{');
            skipWhitespace();
            final Map<String, Object> object = new LinkedHashMap<>();
            if (consume('}')) {
                return object;
            }
            while (true) {
                skipWhitespace();
                final String key = parseString();
                skipWhitespace();
                expect(':');
                final Object value = parseValue();
                object.put(key, value);
                skipWhitespace();
                if (consume('}')) {
                    break;
                }
                expect(',');
            }
            return object;
        }

        private List<Object> parseArray() throws JsonException {
            expect('[');
            skipWhitespace();
            final List<Object> array = new ArrayList<>();
            if (consume(']')) {
                return array;
            }
            while (true) {
                final Object value = parseValue();
                array.add(value);
                skipWhitespace();
                if (consume(']')) {
                    break;
                }
                expect(',');
            }
            return array;
        }

        private String parseString() throws JsonException {
            expect('"');
            final StringBuilder builder = new StringBuilder();
            while (!isEnd()) {
                char ch = input.charAt(index++);
                if (ch == '"') {
                    return builder.toString();
                }
                if (ch == '\\') {
                    if (isEnd()) {
                        throw error("Unvollständige Escape-Sequenz");
                    }
                    ch = input.charAt(index++);
                    switch (ch) {
                        case '"':
                            builder.append('"');
                            break;
                        case '\\':
                            builder.append('\\');
                            break;
                        case '/':
                            builder.append('/');
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
                            builder.append(parseUnicode());
                            break;
                        default:
                            throw error("Ungültige Escape-Sequenz \\" + ch);
                    }
                } else {
                    builder.append(ch);
                }
            }
            throw error("Unvollendeter String");
        }

        private char parseUnicode() throws JsonException {
            if (index + 4 > input.length()) {
                throw error("Unvollständige Unicode-Escape-Sequenz");
            }
            int codePoint = 0;
            for (int i = 0; i < 4; i++) {
                final char ch = input.charAt(index++);
                final int digit = Character.digit(ch, 16);
                if (digit < 0) {
                    throw error("Ungültige Unicode-Escape-Sequenz");
                }
                codePoint = (codePoint << 4) + digit;
            }
            return (char) codePoint;
        }

        private Object parseNumber() throws JsonException {
            final int start = index;
            if (input.charAt(index) == '-') {
                index++;
            }
            if (index >= input.length()) {
                throw error("Ungültige Zahl");
            }
            if (input.charAt(index) == '0') {
                index++;
            } else if (isDigit(input.charAt(index))) {
                while (index < input.length() && isDigit(input.charAt(index))) {
                    index++;
                }
            } else {
                throw error("Ungültige Zahl");
            }
            boolean fractional = false;
            if (index < input.length() && input.charAt(index) == '.') {
                fractional = true;
                index++;
                if (index >= input.length() || !isDigit(input.charAt(index))) {
                    throw error("Ungültige Zahl");
                }
                while (index < input.length() && isDigit(input.charAt(index))) {
                    index++;
                }
            }
            if (index < input.length()) {
                final char ch = input.charAt(index);
                if (ch == 'e' || ch == 'E') {
                    fractional = true;
                    index++;
                    if (index < input.length() && (input.charAt(index) == '+' || input.charAt(index) == '-')) {
                        index++;
                    }
                    if (index >= input.length() || !isDigit(input.charAt(index))) {
                        throw error("Ungültiger Exponent");
                    }
                    while (index < input.length() && isDigit(input.charAt(index))) {
                        index++;
                    }
                }
            }
            final String numberText = input.substring(start, index);
            try {
                if (!fractional) {
                    final long longValue = Long.parseLong(numberText);
                    if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                        return (int) longValue;
                    }
                    return longValue;
                }
                return Double.parseDouble(numberText);
            } catch (final NumberFormatException exception) {
                throw error("Ungültige Zahl: " + numberText);
            }
        }

        private Object parseLiteral(final String literal, final Object value) throws JsonException {
            if (input.startsWith(literal, index)) {
                index += literal.length();
                return value;
            }
            throw error("Erwartet '" + literal + "'");
        }

        private void expect(final char expected) throws JsonException {
            skipWhitespace();
            if (isEnd() || input.charAt(index) != expected) {
                throw error("Erwartet '" + expected + "'");
            }
            index++;
        }

        private boolean consume(final char expected) {
            skipWhitespace();
            if (!isEnd() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (!isEnd()) {
                final char ch = input.charAt(index);
                if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
                    index++;
                } else {
                    break;
                }
            }
        }

        private boolean isDigit(final char ch) {
            return ch >= '0' && ch <= '9';
        }

        private boolean isEnd() {
            return index >= input.length();
        }

        private JsonException error(final String message) {
            return new JsonException(message + " (Index " + index + ")");
        }
    }

    public static final class JsonException extends Exception {
        public JsonException(final String message) {
            super(message);
        }
    }
}
