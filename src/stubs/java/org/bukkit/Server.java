package org.bukkit;

import java.util.Iterator;
import org.bukkit.advancement.Advancement;
import org.bukkit.plugin.PluginManager;

/**
 * Minimal server stub used for compilation.
 */
public interface Server {
    PluginManager getPluginManager();

    Iterator<Advancement> advancementIterator();
}
