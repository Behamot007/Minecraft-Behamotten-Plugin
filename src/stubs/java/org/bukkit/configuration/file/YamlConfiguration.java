package org.bukkit.configuration.file;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

public class YamlConfiguration extends FileConfiguration {
    public static YamlConfiguration loadConfiguration(final File file) {
        return new YamlConfiguration();
    }

    public void createSection(final String path, final Map<String, Object> values) {
        setSection(path, new ConfigurationSection(values));
    }

    public void save(final File file) throws IOException {
        if (Boolean.getBoolean("behamotten.test.failSave")) {
            throw new IllegalStateException("Simulated save failure");
        }
        // no-op stub
    }
}
