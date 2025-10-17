package com.mojang.brigadier.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

/**
 * Base command builder used by the literal and argument builders.
 */
public abstract class ArgumentBuilder<S, T extends ArgumentBuilder<S, T>> {
    private final List<ArgumentBuilder<S, ?>> children = new ArrayList<>();
    private Predicate<S> requirement = source -> true;
    private Command<S> command;
    private SuggestionProvider<S> suggestions;

    public T requires(final Predicate<S> requirement) {
        this.requirement = Objects.requireNonNull(requirement, "requirement");
        return getThis();
    }

    public T executes(final Command<S> command) {
        this.command = Objects.requireNonNull(command, "command");
        return getThis();
    }

    public T suggests(final SuggestionProvider<S> suggestions) {
        this.suggestions = Objects.requireNonNull(suggestions, "suggestions");
        return getThis();
    }

    public T then(final ArgumentBuilder<S, ?> child) {
        children.add(Objects.requireNonNull(child, "child"));
        return getThis();
    }

    protected abstract T getThis();

    public Predicate<S> getRequirement() {
        return requirement;
    }

    public Command<S> getCommand() {
        return command;
    }

    public SuggestionProvider<S> getSuggestions() {
        return suggestions;
    }

    public List<ArgumentBuilder<S, ?>> getChildren() {
        return children;
    }

    public CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context, final SuggestionsBuilder builder) {
        if (suggestions != null) {
            return suggestions.getSuggestions(context, builder);
        }
        return CompletableFuture.completedFuture(builder.build());
    }
}
