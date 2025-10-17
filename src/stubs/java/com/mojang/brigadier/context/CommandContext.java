package com.mojang.brigadier.context;

import java.util.Objects;

/**
 * Holds the command source during execution.
 */
public class CommandContext<S> {
    private final S source;

    public CommandContext(final S source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public S getSource() {
        return source;
    }
}
