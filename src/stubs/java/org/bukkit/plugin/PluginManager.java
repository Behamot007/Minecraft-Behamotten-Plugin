package org.bukkit.plugin;

import org.bukkit.event.Listener;

/**
 * Minimal plugin manager stub mirroring the Bukkit interface type.
 */
public interface PluginManager {
    void registerEvents(Listener listener, Plugin plugin);
}
