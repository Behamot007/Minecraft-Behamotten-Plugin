package org.bukkit.configuration.file;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

public class FileConfiguration {
    private final Map<String, ConfigurationSection> sections = new HashMap<>();

    public ConfigurationSection getConfigurationSection(final String path) {
        return sections.get(path);
    }

    protected void setSection(final String path, final ConfigurationSection section) {
        sections.put(path, section);
    }
}
