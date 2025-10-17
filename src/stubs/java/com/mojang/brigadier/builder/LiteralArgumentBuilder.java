package com.mojang.brigadier.builder;

import java.util.Objects;

/**
 * Simplified literal builder implementation.
 */
public final class LiteralArgumentBuilder<S> extends ArgumentBuilder<S, LiteralArgumentBuilder<S>> {
    private final String literal;

    private LiteralArgumentBuilder(final String literal) {
        this.literal = Objects.requireNonNull(literal, "literal");
    }

    public static <S> LiteralArgumentBuilder<S> literal(final String name) {
        return new LiteralArgumentBuilder<>(name);
    }

    public String getLiteral() {
        return literal;
    }

    @Override
    protected LiteralArgumentBuilder<S> getThis() {
        return this;
    }
}
