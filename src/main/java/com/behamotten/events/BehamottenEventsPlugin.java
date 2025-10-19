package com.behamotten.events;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin entry point for managing event participation commands and persistence.
 */
public final class BehamottenEventsPlugin extends JavaPlugin {
    private EventParticipationData participationData;

    @Override
    public void onEnable() {
        participationData = EventParticipationData.load(this);
        new EventCommandRegistrar(this, participationData).registerCommands();
        getLogger().info(() -> "Loaded " + participationData.getParticipantCount() + " event participants.");
    }

    @Override
    public void onDisable() {
        if (participationData != null) {
            participationData.save();
        }
    }
}
