package com.behamotten.events.progress;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Holds progress data for a specific player.
 */
public final class PlayerProgress {
    private UUID playerId;
    private String lastKnownName;
    private Map<String, CompletionRecord> completions;

    public PlayerProgress() {
        completions = new LinkedHashMap<>();
    }

    public PlayerProgress(final UUID playerId, final String lastKnownName,
            final Map<String, CompletionRecord> completions) {
        this();
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.lastKnownName = lastKnownName;
        if (completions != null) {
            this.completions.putAll(completions);
        }
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public Collection<CompletionRecord> getCompletions() {
        return Collections.unmodifiableCollection(completions.values());
    }

    public Map<String, CompletionRecord> getCompletionMap() {
        return Collections.unmodifiableMap(completions);
    }

    public boolean updateLastKnownName(final String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (!Objects.equals(lastKnownName, name)) {
            lastKnownName = name;
            return true;
        }
        return false;
    }

    public boolean recordCompletion(final MasterEntry entry, final Instant completedAt,
            final Collection<String> completedCriteria, final Map<String, String> details) {
        final CompletionRecord updated = new CompletionRecord(entry.getId(), entry.getType(), completedAt,
                completedCriteria, details);
        final CompletionRecord existing = completions.get(entry.getId());
        if (updated.equals(existing)) {
            return false;
        }
        completions.put(entry.getId(), updated);
        return true;
    }

    public PlayerProgress normalize() {
        if (completions == null) {
            completions = new LinkedHashMap<>();
        } else {
            completions.replaceAll((key, value) -> value == null ? null : value.normalize());
            completions.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        }
        return this;
    }
}
