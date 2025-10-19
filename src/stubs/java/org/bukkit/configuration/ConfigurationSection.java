package org.bukkit.configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ConfigurationSection {
    private final Map<String, Object> values;

    public ConfigurationSection() {
        this(new LinkedHashMap<>());
    }

    public ConfigurationSection(final Map<String, Object> values) {
        this.values = new LinkedHashMap<>(values);
    }

    public Set<String> getKeys(final boolean deep) {
        return values.keySet();
    }

    public String getString(final String key) {
        final Object value = values.get(key);
        return value != null ? value.toString() : null;
    }

    public Map<String, Object> getValues() {
        return values;
    }
}
