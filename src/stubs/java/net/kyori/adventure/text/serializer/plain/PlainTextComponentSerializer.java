package net.kyori.adventure.text.serializer.plain;

import net.kyori.adventure.text.Component;

/**
 * Minimal plain text serializer stub mirroring the Adventure interface.
 */
public interface PlainTextComponentSerializer {
    /**
     * Returns a singleton serializer instance, matching Adventure's static accessor.
     */
    static PlainTextComponentSerializer plainText() {
        return Impl.INSTANCE;
    }

    /**
     * Serialize a component to plain text.
     */
    String serialize(Component component);

    /**
     * Simple implementation used by the stub.
     */
    final class Impl implements PlainTextComponentSerializer {
        private static final Impl INSTANCE = new Impl();

        private Impl() {
        }

        @Override
        public String serialize(final Component component) {
            return component == null ? "" : component.toString();
        }
    }
}
