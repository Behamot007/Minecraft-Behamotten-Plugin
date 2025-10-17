package net.minecraft.commands;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

/**
 * Provides very small helper methods for suggestions.
 */
public final class SharedSuggestionProvider {
    private SharedSuggestionProvider() {
    }

    public static <S> CompletableFuture<Suggestions> suggest(final List<String> suggestions,
            final SuggestionsBuilder builder) {
        for (final String suggestion : suggestions) {
            builder.suggest(suggestion);
        }
        return CompletableFuture.completedFuture(builder.build());
    }
}
