package org.bukkit.plugin.java;

import java.io.File;
import java.util.logging.Logger;

import org.bukkit.command.PluginCommand;

/**
 * Minimal JavaPlugin stub providing only the methods required for compilation.
 */
public class JavaPlugin {
    private final Logger logger = Logger.getLogger(getClass().getName());

    public void onEnable() {
        // no-op
    }

    public void onDisable() {
        // no-op
    }

    public Logger getLogger() {
        return logger;
    }

    public File getDataFolder() {
        return new File("build/tmp/pluginData");
    }

    public PluginCommand getCommand(final String name) {
        return new PluginCommand(name);
    }
}
