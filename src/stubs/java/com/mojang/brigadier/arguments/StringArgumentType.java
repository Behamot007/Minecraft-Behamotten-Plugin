package com.mojang.brigadier.arguments;

import com.mojang.brigadier.context.CommandContext;

/**
 * Simplified string argument type implementation.
 */
public final class StringArgumentType {
    private StringArgumentType() {
    }

    public static StringArgumentType word() {
        return new StringArgumentType();
    }

    public static String getString(final CommandContext<?> context, final String name) {
        return "";
    }
}
