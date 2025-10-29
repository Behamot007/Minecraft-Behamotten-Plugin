package org.bukkit.configuration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public interface ConfigurationSection {
    Set<String> getKeys(boolean deep);

    String getString(String key);

    Map<String, Object> getValues();

    /**
     * Minimal in-memory implementation used by tests.
     */
    class MemoryConfigurationSection implements ConfigurationSection {
        private final Map<String, Object> values;

        public MemoryConfigurationSection() {
            this(new LinkedHashMap<>());
        }

        public MemoryConfigurationSection(final Map<String, Object> values) {
            this.values = new LinkedHashMap<>(values);
        }

        @Override
        public Set<String> getKeys(final boolean deep) {
            return Collections.unmodifiableSet(values.keySet());
        }

        @Override
        public String getString(final String key) {
            final Object value = values.get(key);
            return value != null ? value.toString() : null;
        }

        @Override
        public Map<String, Object> getValues() {
            return Collections.unmodifiableMap(values);
        }
    }
}
