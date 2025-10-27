package com.behamotten.events.progress;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Describes a single achievement or quest definition stored in the master export file.
 */
public final class MasterEntry {

    /**
     * Supported types of exported progress entries.
     */
    public enum EntryType {
        ADVANCEMENT,
        QUEST
    }

    private String id;
    private EntryType type;
    private String name;
    private String description;
    private String parentId;
    private String icon;
    private Map<String, Object> attributes;
    private List<String> criteria;

    /**
     * Creates an empty entry used for JSON deserialization.
     */
    public MasterEntry() {
        attributes = new LinkedHashMap<>();
        criteria = new ArrayList<>();
    }

    /**
     * Creates a fully populated entry instance.
     */
    public MasterEntry(final String id, final EntryType type, final String name, final String description,
            final String parentId, final String icon, final Map<String, ?> attributes,
            final Collection<String> criteria) {
        this();
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.name = name;
        this.description = description;
        this.parentId = parentId;
        this.icon = icon;
        if (attributes != null) {
            this.attributes.putAll(attributes);
        }
        if (criteria != null) {
            this.criteria.addAll(criteria);
        }
    }

    public String getId() {
        return id;
    }

    public EntryType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getParentId() {
        return parentId;
    }

    public String getIcon() {
        return icon;
    }

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public List<String> getCriteria() {
        return Collections.unmodifiableList(criteria);
    }

    /**
     * Ensures that optional collections are initialized after JSON deserialization.
     */
    public MasterEntry normalize() {
        if (attributes == null) {
            attributes = new LinkedHashMap<>();
        }
        if (criteria == null) {
            criteria = new ArrayList<>();
        }
        return this;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MasterEntry)) {
            return false;
        }
        final MasterEntry other = (MasterEntry) obj;
        return Objects.equals(id, other.id)
                && type == other.type
                && Objects.equals(name, other.name)
                && Objects.equals(description, other.description)
                && Objects.equals(parentId, other.parentId)
                && Objects.equals(icon, other.icon)
                && Objects.equals(attributes, other.attributes)
                && Objects.equals(criteria, other.criteria);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, name, description, parentId, icon, attributes, criteria);
    }

    @Override
    public String toString() {
        return "MasterEntry{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", parentId='" + parentId + '\'' +
                ", icon='" + icon + '\'' +
                ", attributes=" + attributes +
                ", criteria=" + criteria +
                '}';
    }
}
