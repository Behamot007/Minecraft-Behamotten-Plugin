package com.behamotten.events.progress;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the completion state of a single quest or advancement for a player.
 */
public final class CompletionRecord {
    private String entryId;
    private MasterEntry.EntryType type;
    private Instant completedAt;
    private List<String> completedCriteria;
    private Map<String, String> details;

    public CompletionRecord() {
        completedCriteria = new ArrayList<>();
        details = new LinkedHashMap<>();
    }

    public CompletionRecord(final String entryId, final MasterEntry.EntryType type, final Instant completedAt,
            final Collection<String> completedCriteria, final Map<String, String> details) {
        this();
        this.entryId = Objects.requireNonNull(entryId, "entryId");
        this.type = Objects.requireNonNull(type, "type");
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt");
        if (completedCriteria != null) {
            this.completedCriteria.addAll(completedCriteria);
        }
        if (details != null) {
            this.details.putAll(details);
        }
    }

    public String getEntryId() {
        return entryId;
    }

    public MasterEntry.EntryType getType() {
        return type;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<String> getCompletedCriteria() {
        return Collections.unmodifiableList(completedCriteria);
    }

    public Map<String, String> getDetails() {
        return Collections.unmodifiableMap(details);
    }

    public CompletionRecord normalize() {
        if (completedCriteria == null) {
            completedCriteria = new ArrayList<>();
        }
        if (details == null) {
            details = new LinkedHashMap<>();
        }
        return this;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CompletionRecord)) {
            return false;
        }
        final CompletionRecord other = (CompletionRecord) obj;
        return Objects.equals(entryId, other.entryId)
                && type == other.type
                && Objects.equals(completedAt, other.completedAt)
                && Objects.equals(completedCriteria, other.completedCriteria)
                && Objects.equals(details, other.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entryId, type, completedAt, completedCriteria, details);
    }

    @Override
    public String toString() {
        return "CompletionRecord{" +
                "entryId='" + entryId + '\'' +
                ", type=" + type +
                ", completedAt=" + completedAt +
                ", completedCriteria=" + completedCriteria +
                ", details=" + details +
                '}';
    }
}
