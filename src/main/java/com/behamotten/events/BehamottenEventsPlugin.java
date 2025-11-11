package com.behamotten.events;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import com.behamotten.events.advancements.AdvancementExportCommand;

/**
 * Main plugin entry point for managing event participation commands and persistence.
 */
public final class BehamottenEventsPlugin extends JavaPlugin {
    private EventParticipationData participationData;
    @Override
    public void onEnable() {
        participationData = EventParticipationData.load(this);
        new EventCommandRegistrar(this, participationData).registerCommands();
        registerAdvancementCommand();
        getLogger().info(() -> "Loaded " + participationData.getParticipantCount() + " event participants.");
    }

    @Override
    public void onDisable() {
        if (participationData != null) {
            participationData.save();
        }
    }

    private void registerAdvancementCommand() {
        registerCommand("exportadvancements", new AdvancementExportCommand(this));
    }

    private void registerCommand(final String name, final CommandExecutor executor) {
        final PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe(() -> "Command '" + name + "' is not defined in plugin.yml.");
            return;
        }
        command.setExecutor(executor);
    }
}
