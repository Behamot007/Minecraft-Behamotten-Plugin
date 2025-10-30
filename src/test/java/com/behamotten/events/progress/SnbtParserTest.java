package com.behamotten.events.progress;

import java.util.List;
import java.util.Map;

public final class SnbtParserTest {

    public void run() {
        parseCompoundWithDifferentValueTypes();
        parseListWithTypedPrefix();
    }

    private void parseCompoundWithDifferentValueTypes() {
        final String snbt = "{id: \"chapter\", number: 5b, flag: true, text: 'Hallo', nested: {value: 42}}";
        final Map<?, ?> result;
        try {
            result = castToMap(SnbtParser.parse(snbt));
        } catch (final SnbtParser.SnbtParseException exception) {
            throw new AssertionError("Parsing valid SNBT compound should not fail", exception);
        }
        assertEquals("chapter", result.get("id"), "id should be parsed as string");
        assertEquals(Byte.valueOf((byte) 5), result.get("number"), "number should preserve byte suffix");
        assertEquals(Boolean.TRUE, result.get("flag"), "flag should be parsed as boolean");
        assertEquals("Hallo", result.get("text"), "Single quoted string should be supported");
        final Map<?, ?> nested = castToMap(result.get("nested"));
        assertEquals(Long.valueOf(42L), nested.get("value"), "Nested number should be parsed");
    }

    private void parseListWithTypedPrefix() {
        final String snbt = "{values: [I; 1, 2, 3]}";
        final Map<?, ?> root;
        try {
            root = castToMap(SnbtParser.parse(snbt));
        } catch (final SnbtParser.SnbtParseException exception) {
            throw new AssertionError("Parsing typed list should not fail", exception);
        }
        final List<?> values = castToList(root.get("values"));
        if (values.size() != 4) {
            throw new AssertionError("Typed list should contain type token and values");
        }
        assertEquals("I", values.get(0), "First element should preserve type token");
        assertEquals(Long.valueOf(1L), values.get(1), "Integer value should be parsed");
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> castToMap(final Object value) {
        if (!(value instanceof Map<?, ?>)) {
            throw new AssertionError("Expected map but received: " + value);
        }
        return (Map<?, ?>) value;
    }

    @SuppressWarnings("unchecked")
    private List<?> castToList(final Object value) {
        if (!(value instanceof List<?>)) {
            throw new AssertionError("Expected list but received: " + value);
        }
        return (List<?>) value;
    }

    private void assertEquals(final Object expected, final Object actual, final String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }
}
