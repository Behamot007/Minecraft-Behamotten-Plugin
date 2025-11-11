package com.behamotten.events.advancements;

import java.util.List;
import java.util.Map;

/**
 * Minimal JSON writer tailored for the advancement export.
 */
final class JsonWriter {
    private static final String INDENT = "  ";

    private JsonWriter() {
    }

    static String stringify(final Object value) {
        final StringBuilder builder = new StringBuilder();
        writeValue(builder, value, 0);
        return builder.toString();
    }

    private static void writeValue(final StringBuilder builder, final Object value, final int depth) {
        if (value == null) {
            builder.append("null");
            return;
        }
        if (value instanceof String) {
            builder.append('"').append(escape((String) value)).append('"');
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
            return;
        }
        if (value instanceof Map<?, ?>) {
            writeObject(builder, (Map<?, ?>) value, depth);
            return;
        }
        if (value instanceof List<?>) {
            writeArray(builder, (List<?>) value, depth);
            return;
        }
        builder.append('"').append(escape(value.toString())).append('"');
    }

    private static void writeObject(final StringBuilder builder, final Map<?, ?> map, final int depth) {
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
                writeValue(builder, entry.getValue(), depth + 1);
                first = false;
            }
            builder.append('\n');
            indent(builder, depth);
        }
        builder.append('}');
    }

    private static void writeArray(final StringBuilder builder, final List<?> list, final int depth) {
        builder.append('[');
        if (!list.isEmpty()) {
            builder.append('\n');
            boolean first = true;
            for (final Object element : list) {
                if (!first) {
                    builder.append(',').append('\n');
                }
                indent(builder, depth + 1);
                writeValue(builder, element, depth + 1);
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
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
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
}
