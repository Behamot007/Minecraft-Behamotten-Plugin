package net.minecraft.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;

/**
 * Entry point for creating command builders.
 */
public final class Commands {
    private Commands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> literal(final String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    public static RequiredArgumentBuilder<CommandSourceStack, StringArgumentType> argument(
            final String name,
            final StringArgumentType type) {
        return RequiredArgumentBuilder.argument(name, type);
    }
}
