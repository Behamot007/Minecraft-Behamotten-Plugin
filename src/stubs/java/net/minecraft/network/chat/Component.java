package net.minecraft.network.chat;

import java.util.Arrays;
import java.util.List;

/**
 * Basic immutable text component implementation.
 */
public final class Component {
    private final String key;
    private final List<Object> args;

    private Component(final String key, final List<Object> args) {
        this.key = key;
        this.args = List.copyOf(args);
    }

    public static Component translatable(final String key, final Object... args) {
        return new Component(key, Arrays.asList(args));
    }

    public String getKey() {
        return key;
    }

    public List<Object> getArgs() {
        return args;
    }
}
