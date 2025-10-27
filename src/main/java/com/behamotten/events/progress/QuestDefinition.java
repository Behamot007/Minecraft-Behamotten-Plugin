package com.behamotten.events.progress;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Describes a quest definition imported from external data (e.g. FTB Quests).
 */
public final class QuestDefinition {
    private String id;
    private String name;
    private String description;
    private String chapter;
    private String icon;
    private Map<String, String> attributes;
    private List<String> criteria;
    private List<String> tags;

    public QuestDefinition() {
        attributes = new LinkedHashMap<>();
        criteria = new ArrayList<>();
        tags = new ArrayList<>();
    }

    public QuestDefinition(final String id, final String name, final String description, final String chapter,
            final String icon, final Map<String, String> attributes, final Collection<String> criteria,
            final Collection<String> tags) {
        this();
        this.id = Objects.requireNonNull(id, "id");
        this.name = name;
        this.description = description;
        this.chapter = chapter;
        this.icon = icon;
        if (attributes != null) {
            this.attributes.putAll(attributes);
        }
        if (criteria != null) {
            this.criteria.addAll(criteria);
        }
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getChapter() {
        return chapter;
    }

    public String getIcon() {
        return icon;
    }

    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public List<String> getCriteria() {
        return Collections.unmodifiableList(criteria);
    }

    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    public QuestDefinition normalize() {
        if (attributes == null) {
            attributes = new LinkedHashMap<>();
        }
        if (criteria == null) {
            criteria = new ArrayList<>();
        }
        if (tags == null) {
            tags = new ArrayList<>();
        }
        return this;
    }

    /**
     * Creates a new {@link MasterEntry} instance representing this quest definition.
     */
    public MasterEntry toMasterEntry() {
        final Map<String, Object> mergedAttributes = new LinkedHashMap<>();
        if (chapter != null && !chapter.isBlank()) {
            mergedAttributes.put("chapter", chapter);
        }
        if (!tags.isEmpty()) {
            mergedAttributes.put("tags", new ArrayList<>(tags));
        }
        mergedAttributes.putAll(attributes);
        return new MasterEntry(id, MasterEntry.EntryType.QUEST, name, description, null, icon, mergedAttributes, criteria);
    }
}
