package org.bukkit.plugin.java;

import java.io.File;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

/**
 * Minimal JavaPlugin stub providing only the methods required for compilation.
 */
public class JavaPlugin implements Plugin {
    private final Logger logger = Logger.getLogger(getClass().getName());
    private final Server server = new Server();

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

    public Server getServer() {
        return server;
    }
}
