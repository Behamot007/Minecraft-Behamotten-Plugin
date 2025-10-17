package com.mojang.brigadier.suggestion;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects suggestions for commands.
 */
public class SuggestionsBuilder {
    private final List<String> suggestions = new ArrayList<>();

    public SuggestionsBuilder suggest(final String suggestion) {
        suggestions.add(suggestion);
        return this;
    }

    public Suggestions build() {
        return new Suggestions(suggestions);
    }
}
