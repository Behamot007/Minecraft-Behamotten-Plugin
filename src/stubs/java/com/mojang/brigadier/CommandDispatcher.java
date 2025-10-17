package com.mojang.brigadier;

import java.util.Objects;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

/**
 * Very small stub of Brigadier's CommandDispatcher.
 */
public class CommandDispatcher<S> {
    public LiteralArgumentBuilder<S> register(final LiteralArgumentBuilder<S> builder) {
        return Objects.requireNonNull(builder, "builder");
    }
}
