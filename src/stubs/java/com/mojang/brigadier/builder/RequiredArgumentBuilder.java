package com.mojang.brigadier.builder;

import java.util.Objects;

/**
 * Simplified argument builder used for command arguments.
 */
public final class RequiredArgumentBuilder<S, T> extends ArgumentBuilder<S, RequiredArgumentBuilder<S, T>> {
    private final String name;
    private final T type;

    private RequiredArgumentBuilder(final String name, final T type) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <S, T> RequiredArgumentBuilder<S, T> argument(final String name, final T type) {
        return new RequiredArgumentBuilder<>(name, type);
    }

    public String getName() {
        return name;
    }

    public T getType() {
        return type;
    }

    @Override
    protected RequiredArgumentBuilder<S, T> getThis() {
        return this;
    }
}
