package org.bukkit.plugin.java;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.advancement.Advancement;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Minimal JavaPlugin stub providing only the methods required for compilation.
 */
public class JavaPlugin implements Plugin {
    private final Logger logger = Logger.getLogger(getClass().getName());
    private final Server server = new MockServer();

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

    private static final class MockServer implements Server {
        private final PluginManager pluginManager = new MockPluginManager();

        @Override
        public PluginManager getPluginManager() {
            return pluginManager;
        }

        @Override
        public Iterator<Advancement> advancementIterator() {
            return Collections.emptyIterator();
        }

        private static final class MockPluginManager implements PluginManager {
            @Override
            public void registerEvents(final Listener listener, final Plugin plugin) {
                // no-op for tests
            }
        }
    }
}
