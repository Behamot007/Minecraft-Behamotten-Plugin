package com.mojang.brigadier.suggestion;

import java.util.List;

/**
 * Simple immutable suggestions list.
 */
public final class Suggestions {
    private final List<String> values;

    Suggestions(final List<String> values) {
        this.values = List.copyOf(values);
    }

    public List<String> getList() {
        return values;
    }
}
