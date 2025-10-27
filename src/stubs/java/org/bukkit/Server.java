package org.bukkit;

import java.util.Collections;
import java.util.Iterator;
import org.bukkit.advancement.Advancement;
import org.bukkit.plugin.PluginManager;

/**
 * Minimal server stub used for compilation.
 */
public class Server {
    private final PluginManager pluginManager = new PluginManager();

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public Iterator<Advancement> advancementIterator() {
        return Collections.emptyIterator();
    }
}
