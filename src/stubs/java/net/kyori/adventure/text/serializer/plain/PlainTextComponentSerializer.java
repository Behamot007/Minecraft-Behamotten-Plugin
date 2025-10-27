package net.kyori.adventure.text.serializer.plain;

import net.kyori.adventure.text.Component;

/**
 * Minimal plain text serializer stub.
 */
public final class PlainTextComponentSerializer {
    private static final PlainTextComponentSerializer INSTANCE = new PlainTextComponentSerializer();

    private PlainTextComponentSerializer() {
    }

    public static PlainTextComponentSerializer plainText() {
        return INSTANCE;
    }

    public String serialize(final Component component) {
        return component == null ? "" : component.toString();
    }
}
