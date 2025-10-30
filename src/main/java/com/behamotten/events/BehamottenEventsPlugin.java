package com.behamotten.events;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import com.behamotten.events.progress.AdvancementMasterCommand;
import com.behamotten.events.progress.AdvancementProgressListener;
import com.behamotten.events.progress.ProgressDataManager;
import com.behamotten.events.progress.QuestMasterCommand;

/**
 * Main plugin entry point for managing event participation commands and persistence.
 */
public final class BehamottenEventsPlugin extends JavaPlugin {
    private EventParticipationData participationData;
    private ProgressDataManager progressDataManager;

    @Override
    public void onEnable() {
        participationData = EventParticipationData.load(this);
        progressDataManager = ProgressDataManager.load(this);
        new EventCommandRegistrar(this, participationData).registerCommands();
        registerProgressCommands();
        getLogger().info(() -> "Loaded " + participationData.getParticipantCount() + " event participants.");
        registerProgressListeners();
    }

    @Override
    public void onDisable() {
        if (participationData != null) {
            participationData.save();
        }
        if (progressDataManager != null) {
            progressDataManager.saveAll();
        }
    }

    private void registerProgressListeners() {
        if (progressDataManager == null) {
            return;
        }
        getServer().getPluginManager().registerEvents(new AdvancementProgressListener(progressDataManager), this);
    }

    private void registerProgressCommands() {
        if (progressDataManager == null) {
            return;
        }
        registerProgressCommand("generatequestmaster",
                new QuestMasterCommand(this, progressDataManager));
        registerProgressCommand("generateadvancementmaster",
                new AdvancementMasterCommand(this, progressDataManager));
    }

    private void registerProgressCommand(final String name, final CommandExecutor executor) {
        final PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe(() -> "Command '" + name + "' is not defined in plugin.yml.");
            return;
        }
        command.setExecutor(executor);
    }
}
