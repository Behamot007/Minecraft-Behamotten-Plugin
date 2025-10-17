package com.mojang.brigadier;

import com.mojang.brigadier.context.CommandContext;

/**
 * Functional interface representing a command execution.
 */
@FunctionalInterface
public interface Command<S> {
    int run(CommandContext<S> context);
}
